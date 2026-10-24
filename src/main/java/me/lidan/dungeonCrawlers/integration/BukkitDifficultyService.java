package me.lidan.dungeonCrawlers.integration;

import io.lumine.mythic.bukkit.MythicBukkit;
import me.lidan.cavecrawlers.damage.DamageCalculationEvent;
import me.lidan.cavecrawlers.skills.SkillsManager;
import me.lidan.cavecrawlers.stats.StatType;
import me.lidan.cavecrawlers.stats.StatsCalculateEvent;
import me.lidan.cavecrawlers.stats.StatsManager;
import me.lidan.cavecrawlers.storage.PlayerDataManager;
import me.lidan.dungeonCrawlers.core.claim.RewardClaimService;
import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.random.NamedRandomFactory;
import me.lidan.dungeonCrawlers.core.difficulty.Difficulty;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.integration.addon.RunicPetAdapter;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Team;
import java.util.*;

/** Instance-scoped combat, transient stats and fixed Runic loot. All calls are on the server thread. */
public final class BukkitDifficultyService implements Listener, AutoCloseable {
    private static final Set<StatType> SKILL_STATS = Set.of(StatType.HEALTH, StatType.DEFENSE,
            StatType.STRENGTH, StatType.CRIT_DAMAGE, StatType.CRIT_CHANCE, StatType.INTELLIGENCE, StatType.ABILITY_DAMAGE);
    private final Plugin plugin;
    private final GenerationService generation;
    private final RunPreparationService runs;
    private final PlayerLifecycleService lifecycle;
    private final RewardClaimService claims;
    private final Map<UUID, Enemy> enemies = new HashMap<>();
    private final Set<UUID> runicBossInstances = new HashSet<>();
    private final Map<UUID, Map<UUID, Double>> magicFind = new HashMap<>();
    private final RunicPetAdapter pet;
    private final Team glow;
    public BukkitDifficultyService(Plugin plugin, GenerationService generation, RunPreparationService runs,
                                  PlayerLifecycleService lifecycle, RewardClaimService claims) {
        this.plugin = plugin; this.generation = generation; this.runs = runs; this.lifecycle = lifecycle; this.claims = claims;
        pet = plugin.getServer().getPluginManager().isPluginEnabled("CaveCrawlAddon") ? new RunicPetAdapter() : null;
        glow = plugin.getServer().getScoreboardManager().getMainScoreboard().registerNewTeam(
                "dc" + UUID.randomUUID().toString().substring(0, 12));
        glow.color(net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE);
    }
    public boolean activeRunicPet(UUID player) { return pet != null && pet.activeLevel(player) > 0; }
    public Set<UUID> enemyIds() { return Set.copyOf(enemies.keySet()); }
    public boolean runicBoss(UUID instance) { return runicBossInstances.contains(instance); }
    public void spawn(Entity entity, Spawn spawn) {
        var context = generation.layoutContext(spawn.instance()).orElseThrow();
        var rules = context.difficulty();
        double chance = spawn.boss() ? rules.runicBossChance() : rules.runicChance();
        if (spawn.boss() && rules.tier().ordinal() < Difficulty.VOID.ordinal()) chance = 0;
        boolean runic = new NamedRandomFactory(context.seed()).stream("runic:" + spawn.room() + ":"
                + spawn.point() + ":" + spawn.boss()).nextDouble() < chance;
        var active = MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId()).orElseThrow();
        active.getEntity().setHealthAndMax(active.getEntity().getMaxHealth() * rules.healthMultiplier(runic));
        enemies.put(entity.getUniqueId(), new Enemy(spawn.instance(), runic, spawn.boss()));
        if (runic && spawn.boss()) runicBossInstances.add(spawn.instance());
        if (runic) {
            entity.setGlowing(true);
            glow.addEntry(entity.getUniqueId().toString());
            active.getEntity().setCustomName("§dRunic " + active.getEntity().getName());
            active.getEntity().setAlwaysShowName(true);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void outgoing(DamageCalculationEvent event) {
        UUID instance = runs.instanceFor(event.getPlayer().getUniqueId()).orElse(null);
        if (instance == null) return;
        var player = lifecycle.player(instance, event.getPlayer().getUniqueId()).orElse(null);
        if (player == null || player.state() != PlayerLifecycleService.PlayerState.ALIVE) { event.setCancelled(true); return; }
        Enemy enemy = enemies.get(event.getTarget().getUniqueId());
        if (enemy == null || !instance.equals(enemy.instance())) return;
        var run = runs.info(instance).orElseThrow();
        String selected = run.selectedClasses().get(event.getPlayer().getUniqueId());
        boolean duplicate = selected != null && run.participants().stream()
                .filter(id -> selected.equals(run.selectedClasses().get(id))).limit(2).count() > 1;
        var rules = generation.layoutContext(instance).orElseThrow().difficulty();
        boolean protectedMob = !enemy.boss() && rules.groupMechanics() && event.getTarget().getNearbyEntities(8, 8, 8)
                .stream().anyMatch(other -> nearbyEnemy(event.getTarget(), other, instance));
        event.setDamage(event.getDamage() * rules.outgoingMultiplier(duplicate, protectedMob));
    }
    private boolean nearbyEnemy(Entity target, Entity other, UUID instance) {
        Enemy nearby = enemies.get(other.getUniqueId());
        return nearby != null && !nearby.boss() && nearby.instance().equals(instance) && !other.isDead()
                && other.isValid() && target.getLocation().distanceSquared(other.getLocation()) <= 64;
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void incoming(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Entity source = event.getDamageSource().getCausingEntity();
        if (source == null && event instanceof EntityDamageByEntityEvent attack) source = attack.getDamager();
        if (source == null) return;
        if (source instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Entity shooter)
            source = shooter;
        Enemy enemy = enemies.get(source.getUniqueId());
        if (enemy == null || !runs.instanceFor(player.getUniqueId()).filter(enemy.instance()::equals).isPresent()) return;
        var rules = generation.layoutContext(enemy.instance()).orElseThrow().difficulty();
        boolean alone = lifecycle.info(enemy.instance()).orElseThrow().players().stream()
                .filter(other -> !other.playerId().equals(player.getUniqueId()) && other.online()
                        && other.state() == PlayerLifecycleService.PlayerState.ALIVE)
                .map(other -> plugin.getServer().getPlayer(other.playerId())).filter(Objects::nonNull)
                .noneMatch(other -> other.getWorld().equals(player.getWorld())
                        && player.getLocation().distanceSquared(other.getLocation()) <= 144);
        event.setDamage(event.getDamage() * rules.incomingMultiplier(alone, enemy.runic()));
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void stats(StatsCalculateEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        UUID instance = runs.instanceFor(player).orElse(null);
        if (instance == null) return;
        var context = generation.layoutContext(instance).orElse(null);
        if (context == null) return;
        int petLevel = pet == null ? 0 : pet.activeLevel(player);
        var stats = event.getStats();
        stats.get(StatType.HEALTH).multiply(1 + petLevel / 200D);
        var skill = SkillsManager.getInstance().getSkillInfo("dungeon");
        var level = skill == null ? null : PlayerDataManager.getInstance().getSkills(event.getPlayer()).get(skill);
        double multiplier = 1 + (level == null ? 0 : Math.clamp(level.getLevel(), 0, 60)) / 100D;
        for (StatType type : SKILL_STATS) stats.set(type, stats.get(type).getValue() * multiplier);
        double mf = Math.max(0, stats.get(StatType.MAGIC_FIND).getValue()) * context.difficulty().magicFindMultiplier();
        stats.set(StatType.MAGIC_FIND, mf);
        magicFind.computeIfAbsent(instance, ignored -> new HashMap<>()).put(player, mf);
    }
    public double magicFind(UUID instance, UUID player) {
        Player online = plugin.getServer().getPlayer(player);
        if (online != null) {
            double value = StatsManager.getInstance().getStats(online).get(StatType.MAGIC_FIND).getValue();
            magicFind.computeIfAbsent(instance, ignored -> new HashMap<>()).put(player, Math.max(0, value));
        }
        return magicFind.getOrDefault(instance, Map.of()).getOrDefault(player, 0D);
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void death(EntityDeathEvent event) {
        Enemy enemy = enemies.remove(event.getEntity().getUniqueId());
        glow.removeEntry(event.getEntity().getUniqueId().toString());
        if (enemy == null || !enemy.runic()) return;
        if (enemy.boss()) runicBossInstances.add(enemy.instance());
        if (Byte.valueOf((byte) 1).equals(event.getEntity().getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, "ringmaster_first_life"),
                org.bukkit.persistence.PersistentDataType.BYTE))) return;
        var context = generation.layoutContext(enemy.instance()).orElse(null);
        Player killer = event.getEntity().getKiller();
        var run = runs.info(enemy.instance()).orElse(null);
        if (context == null || !context.progressionEnabled() || run == null || run.startedAt() == null
                || killer == null || !run.participants().contains(killer.getUniqueId())) return;
        var participant = lifecycle.player(enemy.instance(), killer.getUniqueId()).orElse(null);
        if (participant == null || participant.state() != PlayerLifecycleService.PlayerState.ALIVE) return;
        if (enemy.boss() || new NamedRandomFactory(context.seed()).stream("runic-loot:" + event.getEntity().getUniqueId()).nextDouble()
                < context.difficulty().fragmentChance(magicFind(enemy.instance(), killer.getUniqueId())))
            claims.grantLoot(event.getEntity().getUniqueId(), killer, "RUNIC_FRAGMENT", enemy.boss() ? 4 : 1);
    }
    public void forceRunic(org.bukkit.command.CommandSender sender, UUID entityId) {
        Enemy enemy = enemies.get(entityId);
        if (enemy == null || generation.layoutContext(enemy.instance()).orElseThrow().progressionEnabled()) {
            DungeonMessages.send(sender, "<red>Runic forcing requires an enemy in a debug instance.</red>"); return;
        }
        if (!enemy.runic()) {
            var active = MythicBukkit.inst().getMobManager().getActiveMob(entityId).orElseThrow();
            active.getEntity().setHealthAndMax(active.getEntity().getMaxHealth() * 10);
            active.getEntity().setCustomName("§dRunic " + active.getEntity().getName());
            active.getEntity().setAlwaysShowName(true);
            active.getEntity().getBukkitEntity().setGlowing(true); glow.addEntry(entityId.toString());
            enemies.put(entityId, new Enemy(enemy.instance(), true, enemy.boss()));
        }
        if (enemy.boss()) runicBossInstances.add(enemy.instance());
        DungeonMessages.send(sender, "<light_purple>Debug enemy is Runic. Debug runs give no fragments or Dungeon XP.</light_purple>");
    }

    public void info(org.bukkit.command.CommandSender sender, Player player) {
        UUID instance = runs.instanceFor(player.getUniqueId()).orElse(null);
        if (instance == null) { DungeonMessages.send(sender, "<red>Player is not in a dungeon.</red>"); return; }
        var context = generation.layoutContext(instance).orElseThrow();
        var state = lifecycle.player(instance, player.getUniqueId()).orElseThrow();
        DungeonMessages.send(sender, "<yellow>" + context.difficulty().tier().displayName() + " <gray>MF: "
                + magicFind(instance, player.getUniqueId()) + ", deaths: " + state.deaths()
                + ", Runic pet: " + activeRunicPet(player.getUniqueId())
                + ", charge used: " + state.runicChargeUsed() + ", progression: " + context.progressionEnabled());
    }

    public void tick() {
        enemies.entrySet().removeIf(entry -> {
            Entity entity = plugin.getServer().getEntity(entry.getKey());
            if (entity == null || !entity.isValid() || entity.isDead()) { glow.removeEntry(entry.getKey().toString()); return true; }
            if (entry.getValue().runic()) entity.getWorld().spawnParticle(Particle.DUST, entity.getLocation().add(0, 1, 0),
                    6, .4, .7, .4, new Particle.DustOptions(Color.fromRGB(180, 40, 255), 1.2F));
            return false;
        });
        magicFind.keySet().removeIf(instance -> generation.layoutContext(instance).isEmpty());
        runicBossInstances.removeIf(instance -> generation.layoutContext(instance).isEmpty());
    }
    @Override public void close() { if (pet != null) pet.close(); glow.unregister(); enemies.clear(); magicFind.clear(); runicBossInstances.clear(); }
    public record Spawn(UUID instance, int room, Point point, boolean boss) { }
    private record Enemy(UUID instance, boolean runic, boolean boss) { }
}
