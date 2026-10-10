package me.lidan.dungeonCrawlers.integration;

import me.lidan.cavecrawlers.damage.AbilityDamage;
import me.lidan.cavecrawlers.damage.DamageCalculationEvent;
import me.lidan.cavecrawlers.damage.DamageManager;
import me.lidan.cavecrawlers.damage.FinalDamageCalculation;
import me.lidan.cavecrawlers.stats.StatType;
import me.lidan.cavecrawlers.stats.StatsCalculateEvent;
import me.lidan.cavecrawlers.stats.StatsManager;
import me.lidan.dungeonCrawlers.config.registry.ConfigRegistryService;
import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Main-thread, instance-scoped class abilities and party support effects. */
public final class BukkitClassAbilityService implements Listener, AutoCloseable {
    private final Plugin plugin;
    private final RunPreparationService runs;
    private final PlayerLifecycleService lifecycle;
    private final GenerationService generation;
    private final ConfigRegistryService config;
    private final BukkitEntityIdentity identity;
    private final BukkitBossIdentity bosses;
    private final BukkitDifficultyService difficulty;
    private final NamespacedKey classArrow;
    private final Clock clock;
    private final String worldName;
    private final Map<CooldownKey, Instant> cooldowns = new HashMap<>();
    private final Map<UUID, Map<String, Effect>> effects = new HashMap<>();
    private final Map<UUID, Taunt> taunts = new HashMap<>();
    private final Map<UUID, Shot> arrows = new HashMap<>();

    public BukkitClassAbilityService(Plugin plugin, RunPreparationService runs, PlayerLifecycleService lifecycle,
                                     GenerationService generation, ConfigRegistryService config,
                                     BukkitEntityIdentity identity, BukkitBossIdentity bosses,
                                     BukkitDifficultyService difficulty, Clock clock, String worldName) {
        this.plugin = plugin; this.runs = runs; this.lifecycle = lifecycle; this.generation = generation;
        this.config = config; this.identity = identity; this.bosses = bosses;
        this.difficulty = difficulty; this.classArrow = new NamespacedKey(plugin, "class_arrow");
        this.clock = clock; this.worldName = worldName;
    }

    public static String abilityName(String classId) {
        return switch (classId) {
            case "berserker" -> "Bloodrage";
            case "mage" -> "Stormcall";
            case "tank" -> "Iron Challenge";
            case "archer" -> "Cataclysm Arrow";
            case "healer" -> "Lifesurge";
            default -> "";
        };
    }

    public static int cooldownSeconds(String classId) {
        return switch (classId) {
            case "berserker", "archer" -> 30;
            case "mage" -> 25;
            case "tank" -> 35;
            case "healer" -> 60;
            default -> 0;
        };
    }

    public static String abilityDescription(String classId, int level) {
        return switch (classId) {
            case "berserker" -> "+" + (25 + Math.clamp(level, 0, 60)) + "% strength and damage for 10s.";
            case "mage" -> "Lightning strikes every room enemy; intelligence scaling, glowing for 10s.";
            case "tank" -> "Taunt every room enemy; take 50% less damage for 10s.";
            case "archer" -> "Fire a strength-scaled explosive arrow with an 8-block blast.";
            case "healer" -> "Fully heal your party; regenerate 8% health each second for 10s.";
            default -> "";
        };
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrop(PlayerDropItemEvent event) {
        UUID instance = runs.instanceFor(event.getPlayer().getUniqueId()).orElse(null);
        if (instance == null) return;
        // Cancel first so a failed cast or cooldown never drops the held equipment.
        event.setCancelled(true);
        if (activeInstance(event.getPlayer()) == null) return;
        activateClass(event.getPlayer());
    }

    public boolean activateClass(Player player) {
        UUID instance = activeInstance(player);
        if (instance == null) return false;
        String selected = runs.info(instance).orElseThrow().selectedClasses().get(player.getUniqueId());
        if (selected == null || cooldownSeconds(selected) == 0) return false;
        String name = abilityName(selected);
        if (!ready(player, "class", name)) return false;
        int level = DungeonClassScaling.dungeonLevel(player);
        List<Mob> mobs;
        switch (selected) {
            case "berserker" -> {
                double bonus = (25 + level) / 100D;
                effect(player, "bloodrage", instance, Map.of(StatType.STRENGTH, bonus), bonus, 0, 0, 1);
            }
            case "mage" -> {
                mobs = roomMobs(player, instance);
                if (mobs.isEmpty()) return noEnemies(player);
                for (Mob mob : mobs) {
                    mob.getWorld().strikeLightningEffect(mob.getLocation());
                    mob.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0));
                }
                damage(player, mobs, new AbilityDamage(player, 4000, 10).calculate());
            }
            case "tank" -> {
                mobs = roomMobs(player, instance);
                if (mobs.isEmpty()) return noEnemies(player);
                effect(player, "iron_challenge", instance, Map.of(), 0, .5, 0, 1);
                for (Mob mob : mobs) {
                    Taunt previous = taunts.get(mob.getUniqueId());
                    taunts.put(mob.getUniqueId(), new Taunt(instance, player.getUniqueId(),
                            previous == null ? mob.getTarget() : previous.previousTarget(), clock.instant().plusSeconds(10)));
                    mob.setTarget(player);
                }
            }
            case "archer" -> {
                if (roomBounds(player.getLocation(), instance) == null) return noEnemies(player);
                Arrow arrow = player.launchProjectile(Arrow.class, player.getEyeLocation().getDirection().multiply(2.5));
                arrow.setDamage(0);
                arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
                arrow.getPersistentDataContainer().set(classArrow, PersistentDataType.BYTE, (byte) 1);
                arrows.put(arrow.getUniqueId(), new Shot(instance, player.getUniqueId(),
                        new AbilityDamage(player, 4000, 10, StatType.STRENGTH, false).calculate(),
                        clock.instant().plusSeconds(10)));
            }
            case "healer" -> {
                double healing = healingMultiplier(player, instance);
                for (Player member : party(player, instance, 0)) {
                    heal(member, 100);
                    effect(member, "lifesurge", instance, Map.of(), 0, 0, 8, healing);
                }
            }
            default -> { return false; }
        }
        startCooldown(player, "class", cooldownSeconds(selected));
        activated(player, name);
        return true;
    }

    public boolean activateSupport(Player player, String abilityId) {
        UUID instance = activeInstance(player);
        if (instance == null) {
            DungeonMessages.send(player, "<yellow>Support abilities require a living player in an active dungeon.</yellow>");
            return false;
        }
        String name;
        int cooldown;
        double healing;
        double regen = 0;
        double reduction = 0;
        Map<StatType, Double> stats = Map.of();
        switch (abilityId) {
            case "dc_aegis_standard" -> { name = "Rallying Aegis"; cooldown = 25; healing = 10; stats = Map.of(StatType.DEFENSE, .35); }
            case "dc_bastion_horn" -> { name = "Bastion's Call"; cooldown = 35; healing = 5; reduction = .25; }
            case "dc_renewal_staff" -> { name = "Wellspring"; cooldown = 25; healing = 30; regen = 5; }
            case "dc_dawnlight_tome" -> { name = "Dawn's Benediction"; cooldown = 30; healing = 15; stats = Map.of(StatType.STRENGTH, .25, StatType.INTELLIGENCE, .25); }
            default -> { return false; }
        }
        if (!ready(player, abilityId, name)) return false;
        double multiplier = healingMultiplier(player, instance);
        for (Player member : party(player, instance, 16)) {
            heal(member, healing * multiplier);
            effect(member, abilityId, instance, stats, 0, reduction, regen, multiplier);
        }
        startCooldown(player, abilityId, cooldown);
        activated(player, name);
        return true;
    }

    private double healingMultiplier(Player source, UUID instance) {
        var selected = config.snapshot().classes().get(runs.info(instance).orElseThrow()
                .selectedClasses().get(source.getUniqueId()));
        return selected == null ? 1 : 1 + selected.healingPercentPerLevel()
                * DungeonClassScaling.dungeonLevel(source) / 100D;
    }

    private void effect(Player player, String key, UUID instance, Map<StatType, Double> stats,
                        double damage, double reduction, double regen, double healing) {
        effects.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()).put(key,
                new Effect(instance, clock.instant().plusSeconds(10), stats, damage, reduction, regen * healing));
        // A cancelled DROP restores the held item only after its event returns.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (instance.equals(activeInstance(player))) StatsManager.getInstance().calculateStats(player);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStats(StatsCalculateEvent event) {
        for (Effect effect : activeEffects(event.getPlayer())) {
            effect.stats().forEach((type, percent) -> event.getStats().get(type).multiply(1 + percent));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOutgoing(DamageCalculationEvent event) {
        UUID instance = activeInstance(event.getPlayer());
        if (instance == null || !belongsTo(event.getTarget(), instance)) return;
        double bonus = activeEffects(event.getPlayer()).stream().mapToDouble(Effect::damage).max().orElse(0);
        event.setDamage(event.getDamage() * (1 + bonus));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIncoming(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        double reduction = activeEffects(player).stream().mapToDouble(Effect::reduction).max().orElse(0);
        event.setDamage(event.getDamage() * (1 - reduction));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        Taunt taunt = taunts.get(event.getEntity().getUniqueId());
        if (taunt == null) return;
        Player tank = plugin.getServer().getPlayer(taunt.player());
        if (validTaunt(taunt, tank)) {
            event.setTarget(tank);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onArrowDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager().getPersistentDataContainer().has(classArrow, PersistentDataType.BYTE)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onArrowHit(ProjectileHitEvent event) {
        Shot shot = arrows.remove(event.getEntity().getUniqueId());
        if (shot == null) return;
        event.setCancelled(true);
        Location center = event.getEntity().getLocation();
        event.getEntity().remove();
        Player source = plugin.getServer().getPlayer(shot.player());
        if (source == null || !shot.instance().equals(activeInstance(source))
                || !clock.instant().isBefore(shot.expires())) return;
        Bounds room = roomBounds(center, shot.instance());
        if (room == null) return;
        center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1, 1);
        List<Mob> targets = center.getWorld().getNearbyEntities(center, 8, 8, 8).stream()
                .filter(Mob.class::isInstance).map(Mob.class::cast)
                .filter(mob -> belongsTo(mob, shot.instance()) && room.contains(point(mob.getLocation()))
                        && mob.getLocation().distanceSquared(center) <= 64 && !mob.isDead() && mob.isValid()).toList();
        damage(source, targets, shot.damage());
    }

    private static void damage(Player player, List<Mob> mobs, double amount) {
        if (mobs.isEmpty()) return;
        // A projectile source keeps Cave's damage hooks working even while the caster holds a bow.
        Arrow source = DamageManager.getInstance().launchProjectile(player, Arrow.class,
                new FinalDamageCalculation(amount, false));
        try {
            DamageSource damage = DamageSource.builder(DamageType.ARROW)
                    .withDirectEntity(source).withCausingEntity(player).build();
            for (Mob mob : mobs) {
                mob.setNoDamageTicks(0);
                mob.damage(amount, damage);
            }
        } finally {
            source.remove();
        }
    }

    private List<Mob> roomMobs(Player player, UUID instance) {
        Bounds bounds = roomBounds(player.getLocation(), instance);
        if (bounds == null) return List.of();
        Location center = new Location(player.getWorld(), (bounds.minimum().x() + bounds.maximum().x()) / 2D,
                (bounds.minimum().y() + bounds.maximum().y()) / 2D, (bounds.minimum().z() + bounds.maximum().z()) / 2D);
        return player.getWorld().getNearbyEntities(center,
                        (bounds.maximum().x() - bounds.minimum().x()) / 2D + 1,
                        (bounds.maximum().y() - bounds.minimum().y()) / 2D + 1,
                        (bounds.maximum().z() - bounds.minimum().z()) / 2D + 1).stream()
                .filter(Mob.class::isInstance).map(Mob.class::cast)
                .filter(mob -> !mob.isDead() && mob.isValid() && belongsTo(mob, instance)
                        && bounds.contains(point(mob.getLocation()))).toList();
    }

    private Bounds roomBounds(Location location, UUID instance) {
        return generation.layoutPlan(instance).stream().flatMap(plan -> plan.placements().stream())
                .filter(room -> room.bounds().contains(point(location))).map(room -> room.bounds()).findFirst().orElse(null);
    }

    private boolean belongsTo(Entity entity, UUID instance) {
        return identity.belongsTo(entity, instance) || bosses.read(entity).filter(instance::equals).isPresent()
                || difficulty.belongsTo(entity, instance);
    }

    private static Point point(Location location) {
        return new Point(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private List<Player> party(Player source, UUID instance, double radius) {
        return runs.info(instance).stream().flatMap(run -> run.participants().stream())
                .map(id -> plugin.getServer().getPlayer(id)).filter(player -> player != null
                        && instance.equals(activeInstance(player)) && player.getWorld().equals(source.getWorld())
                        && (radius == 0 || player.getLocation().distanceSquared(source.getLocation()) <= radius * radius))
                .toList();
    }

    private UUID activeInstance(Player player) {
        if (player == null || !player.isOnline() || player.isDead() || !player.getWorld().getName().equals(worldName)) return null;
        UUID instance = runs.instanceFor(player.getUniqueId()).orElse(null);
        if (instance == null) return null;
        var run = runs.info(instance).orElse(null);
        if (run == null || (run.state() != RunPreparationService.RunState.RUNNING && run.state() != RunPreparationService.RunState.BOSS)) return null;
        return lifecycle.player(instance, player.getUniqueId())
                .filter(state -> state.online() && state.state() == PlayerLifecycleService.PlayerState.ALIVE)
                .map(ignored -> instance).orElse(null);
    }

    private List<Effect> activeEffects(Player player) {
        UUID instance = activeInstance(player);
        if (instance == null) return List.of();
        return effects.getOrDefault(player.getUniqueId(), Map.of()).values().stream()
                .filter(effect -> effect.instance().equals(instance) && clock.instant().isBefore(effect.expires())).toList();
    }

    private boolean ready(Player player, String key, String name) {
        Instant expires = cooldowns.get(new CooldownKey(player.getUniqueId(), key));
        if (expires == null || !clock.instant().isBefore(expires)) return true;
        long seconds = Math.max(1, (java.time.Duration.between(clock.instant(), expires).toMillis() + 999) / 1000);
        DungeonMessages.send(player, "<yellow>" + name + " is ready in " + seconds + "s.</yellow>");
        return false;
    }

    private void startCooldown(Player player, String key, int seconds) {
        cooldowns.put(new CooldownKey(player.getUniqueId(), key), clock.instant().plusSeconds(seconds));
    }

    private static boolean noEnemies(Player player) {
        DungeonMessages.send(player, "<yellow>No dungeon enemies in this room.</yellow>");
        return false;
    }

    private static void activated(Player player, String name) {
        DungeonMessages.send(player, "<aqua>" + name + "!</aqua>");
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1, 0), 25, .5, .5, .5);
    }

    private static void heal(Player player, double percent) {
        StatsManager.healPlayerPercent(player, percent);
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 3, .4, .4, .4);
    }

    /** Called once per second by the existing plugin task. */
    public void tick() {
        Instant now = clock.instant();
        cooldowns.values().removeIf(expiry -> !now.isBefore(expiry));
        for (UUID playerId : new ArrayList<>(effects.keySet())) {
            Player player = plugin.getServer().getPlayer(playerId);
            UUID instance = activeInstance(player);
            Map<String, Effect> active = effects.get(playerId);
            boolean removed = active.values().removeIf(effect -> !effect.instance().equals(instance) || !now.isBefore(effect.expires()));
            if (removed && player != null && player.isOnline() && !player.isDead()) StatsManager.getInstance().calculateStats(player);
            if (active.isEmpty()) { effects.remove(playerId); continue; }
            double regen = active.values().stream().mapToDouble(Effect::regen).max().orElse(0);
            if (regen > 0) heal(player, regen);
        }
        taunts.entrySet().removeIf(entry -> {
            Entity entity = plugin.getServer().getEntity(entry.getKey());
            Player tank = plugin.getServer().getPlayer(entry.getValue().player());
            if (!(entity instanceof Mob mob) || mob.isDead() || !mob.isValid()) return true;
            if (!validTaunt(entry.getValue(), tank)) { restoreTarget(mob, entry.getValue()); return true; }
            if (!tank.equals(mob.getTarget())) mob.setTarget(tank);
            return false;
        });
        arrows.entrySet().removeIf(entry -> {
            Entity arrow = plugin.getServer().getEntity(entry.getKey());
            Shot shot = entry.getValue();
            if (arrow == null) return true;
            if (!now.isBefore(shot.expires()) || !shot.instance().equals(activeInstance(plugin.getServer().getPlayer(shot.player())))) {
                arrow.remove(); return true;
            }
            return false;
        });
    }

    private boolean validTaunt(Taunt taunt, Player tank) {
        return tank != null && taunt.instance().equals(activeInstance(tank)) && clock.instant().isBefore(taunt.expires());
    }

    private static void restoreTarget(Mob mob, Taunt taunt) {
        if (mob.getTarget() == null || !mob.getTarget().getUniqueId().equals(taunt.player())) return;
        LivingEntity previous = taunt.previousTarget();
        mob.setTarget(previous != null && previous.isValid() && !previous.isDead() ? previous : null);
    }

    @Override public void close() {
        List<UUID> buffedPlayers = List.copyOf(effects.keySet());
        for (var entry : taunts.entrySet()) {
            if (plugin.getServer().getEntity(entry.getKey()) instanceof Mob mob) restoreTarget(mob, entry.getValue());
        }
        arrows.keySet().forEach(id -> { Entity arrow = plugin.getServer().getEntity(id); if (arrow != null) arrow.remove(); });
        taunts.clear(); arrows.clear(); effects.clear(); cooldowns.clear();
        buffedPlayers.stream().map(plugin.getServer()::getPlayer)
                .filter(player -> player != null && player.isOnline() && !player.isDead())
                .forEach(player -> StatsManager.getInstance().calculateStats(player));
    }

    private record CooldownKey(UUID player, String ability) { }
    private record Effect(UUID instance, Instant expires, Map<StatType, Double> stats, double damage, double reduction, double regen) { }
    private record Taunt(UUID instance, UUID player, LivingEntity previousTarget, Instant expires) { }
    private record Shot(UUID instance, UUID player, double damage, Instant expires) { }
}
