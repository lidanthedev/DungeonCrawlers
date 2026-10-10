package me.lidan.dungeonCrawlers.integration;

import io.lumine.mythic.bukkit.MythicBukkit;
import me.lidan.cavecrawlers.utils.MiniMessageUtils;
import me.lidan.dungeonCrawlers.core.encounter.EncounterFactory.EncounterContext;
import me.lidan.dungeonCrawlers.core.encounter.RingmasterEncounter;
import me.lidan.dungeonCrawlers.core.encounter.RingmasterGeometry;
import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Models, hazards, music and actors share the encounter tick and its cleanup boundary. */
public final class BukkitRingmasterArena implements RingmasterEncounter.Arena, Listener {
    private static final Particle.DustOptions WARNING = new Particle.DustOptions(Color.fromRGB(255, 218, 80), 1.8F);
    private static final Particle.DustOptions DANGER = new Particle.DustOptions(Color.fromRGB(245, 70, 210), 2F);
    private static final Particle.DustOptions SAFE = new Particle.DustOptions(Color.fromRGB(90, 240, 220), 1.7F);
    private static final int PROJECTILE_CAP = 40;
    private static final double RADIUS = RingmasterGeometry.RADIUS;
    private final EncounterContext context;
    private final Plugin plugin;
    private final RunPreparationService runs;
    private final PlayerLifecycleService lifecycle;
    private final MythicMobGateway mythic;
    private final BukkitDifficultyService difficulty;
    private final Set<UUID> performers = new HashSet<>();
    private final List<Bullet> bullets = new ArrayList<>();
    private final List<Drop> drops = new ArrayList<>();
    private final List<BukkitRingmasterVisuals.Rig> attackRigs = new ArrayList<>();
    private final List<BukkitRingmasterVisuals.Rig> horses = new ArrayList<>();
    private final Map<UUID, Long> sweepHits = new HashMap<>();
    private final int partySize;
    private List<Player> audience = List.of();
    private UUID boss;
    private Location center;
    private BukkitRingmasterVisuals visuals;
    private BukkitRingmasterVisuals.Rig bossRig;
    private BukkitRingmasterVisuals.Rig movie;
    private BukkitRingmasterVisuals.Rig sweepRig;
    private RingmasterEncounter.Attack attack;
    private long started, lastVisual, lastTick, lastBeat, transitionAt;
    private int targetCursor, positionCursor, casts, volley, spotlight, scythes, life, beat;
    private double sweepAngle;
    private boolean registered, cleaned, opening, sweepActive;

    public BukkitRingmasterArena(EncounterContext context, Plugin plugin, RunPreparationService runs,
                                PlayerLifecycleService lifecycle, MythicMobGateway mythic,
                                BukkitDifficultyService difficulty) {
        this.context = context; this.plugin = plugin; this.runs = runs;
        this.lifecycle = lifecycle; this.mythic = mythic; this.difficulty = difficulty;
        partySize = runs.info(context.instanceId()).orElseThrow().participants().size();
    }

    @Override
    public void begin(UUID entityId, int nextLife) {
        if (cleaned) throw new IllegalStateException("ringmaster arena is cleaned");
        if (movie != null) { movie.close(); movie = null; }
        transitionAt = 0;
        boss = entityId; life = nextLife; opening = life == 1;
        LivingEntity entity = boss();
        Point point = context.arenaCenter();
        center = new Location(entity.getWorld(), point.x() + .5, point.y(), point.z() + .5);
        if (visuals == null) visuals = new BukkitRingmasterVisuals(entity.getWorld(), context.instanceId());
        entity.setAI(!opening);
        entity.setInvulnerable(opening);
        entity.setRemoveWhenFarAway(false);
        if (entity.getAttribute(Attribute.SCALE) != null) entity.getAttribute(Attribute.SCALE).setBaseValue(1.65);
        entity.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "ringmaster_first_life"),
                org.bukkit.persistence.PersistentDataType.BYTE, (byte) (life == 1 ? 1 : 0));
        var active = MythicBukkit.inst().getMobManager().getActiveMob(boss).orElseThrow();
        active.getEntity().setHealthAndMax(active.getEntity().getMaxHealth() * (1 + .55 * (partySize - 1)));
        if (!registered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
        audience = players();
        if (life == 2) reposition();
        bossRig = visuals.jester(entity.getLocation(), life == 2);
        title(life == 1 ? "<gold><bold>THE MAD RINGMASTER</bold></gold>" : "<light_purple><bold>THE WORLD REVOLVES</bold></light_purple>",
                life == 1 ? "<white>Act I · The midnight carnival</white>" : "<white>Act II · No strings attached</white>");
        burst(entity.getLocation().add(0, 2, 0), life == 1 ? Particle.FIREWORK : Particle.REVERSE_PORTAL, 80);
        sound(life == 1 ? Sound.ENTITY_EVOKER_PREPARE_SUMMON : Sound.ENTITY_WITHER_SPAWN, .8F, .8F);
    }

    @Override public void ready() { opening = false; boss().setInvulnerable(false); boss().setAI(true); }

    @Override
    public void transition(Instant now) {
        audience = players();
        transitionAt = now.toEpochMilli();
        movie = visuals.jester(center.clone().add(0, 8, 0), true);
        title("<dark_purple><bold>THE MASK BREAKS</bold></dark_purple>", "<gold>The encore begins in six seconds...</gold>");
        sound(Sound.ENTITY_WITHER_AMBIENT, .9F, .55F);
    }

    private LivingEntity boss() {
        Entity entity = boss == null ? null : plugin.getServer().getEntity(boss);
        if (!(entity instanceof LivingEntity living)) throw new IllegalStateException("ringmaster is unavailable");
        return living;
    }

    private boolean alive(Player player) {
        return player != null && player.isOnline()
                && runs.instanceFor(player.getUniqueId()).filter(context.instanceId()::equals).isPresent()
                && lifecycle.player(context.instanceId(), player.getUniqueId())
                .filter(value -> value.online() && value.state() == PlayerLifecycleService.PlayerState.ALIVE).isPresent();
    }

    private List<Player> players() {
        return runs.info(context.instanceId()).map(run -> run.participants().stream().sorted()
                .map(plugin.getServer()::getPlayer).filter(this::alive)
                .filter(p -> center != null && p.getWorld().equals(center.getWorld())
                        && horizontalDistanceSquared(p.getLocation(), center) <= 46 * 46).toList()).orElse(List.of());
    }

    @Override
    public void tick(Instant now) {
        if (cleaned) return;
        long time = now.toEpochMilli();
        boolean visual = time - lastVisual >= 100;
        if (visual) lastVisual = time;
        audience = players();
        music(time);
        if (transitionAt != 0) {
            if (visual && movie != null) {
                double progress = Math.clamp((time - transitionAt) / 6000D, 0, 1);
                movie.move(center.clone().add(0, 8 + Math.sin(progress * Math.PI) * 4, 0), progress * 540, 0, 1 + progress);
                ring(center, 5 + progress * 25, SAFE);
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6 + progress * Math.PI * 3;
                    particle(center.getX() + 5 * Math.cos(a), center.getY() + 4 + progress * 10,
                            center.getZ() + 5 * Math.sin(a), DANGER);
                }
            }
            return;
        }
        List<Player> players = audience;
        performers.removeIf(id -> {
            Entity entity = plugin.getServer().getEntity(id);
            if (!(entity instanceof Mob mob) || !entity.isValid() || entity.isDead()) return true;
            mob.setTarget(nearest(players, mob.getLocation()));
            return false;
        });
        if (!opening && (attack == null || attack == RingmasterEncounter.Attack.CAROUSEL)
                && boss() instanceof Mob mob) mob.setTarget(nearest(players, mob.getLocation()));
        if (visual && bossRig != null) {
            Location at = boss().getLocation().add(0, Math.sin(time / 230D) * .1, 0);
            bossRig.move(at, at.getYaw(), Math.sin(time / 430D) * .04, 1);
        }
        double seconds = lastTick == 0 ? .05 : Math.min(.1, Math.max(0, (time - lastTick) / 1000D));
        lastTick = time;
        tickBullets(players, seconds, time, visual);
        if (attack == null) return;
        long elapsed = time - started;
        switch (attack) {
            case BALLOONS -> {
                if (elapsed < 1000 && visual) ring(boss().getLocation(), 2, WARNING);
                if (volley < 3 && elapsed >= 1000 + volley * 900L) {
                    Player target = target(players);
                    if (target != null) fan(target, false, time);
                    volley++;
                }
                if (elapsed >= 3800) finishAttack();
            }
            case CARDS -> {
                if (elapsed < 1300 && visual) ring(boss().getLocation(), 3, SAFE);
                if (volley < 2 && elapsed >= 1300 + volley * 1200L) {
                    Player target = target(players);
                    if (target != null) fan(target, true, time);
                    volley++;
                }
                if (elapsed >= 3400) finishAttack();
            }
            case CARD_RING -> {
                if (elapsed < 1400 && visual) { ring(boss().getLocation(), 3, WARNING); gapArrow(boss().getLocation(), sweepAngle); }
                if (volley < 2 && elapsed >= 1400 + volley * 2300L) {
                    deckRing(boss().getLocation().add(0, 1.3, 0), sweepAngle + volley * .35, time);
                    volley++;
                }
                if (elapsed >= 6000) finishAttack();
            }
            case JACK_IN_THE_BOX -> {
                for (Drop box : drops) {
                    if (visual && !box.hit) {
                        ring(box.at, 3.5, WARNING);
                        box.rig.move(box.at, elapsed * .07, Math.sin(elapsed / 90D) * .08, 1);
                    }
                    if (!box.hit && elapsed >= 3000) {
                        box.hit = true; box.rig.close();
                        pulse(box.at, 3.5, 700000, players);
                        if (cleaned) return;
                        for (int i = 0; i < 8; i++) {
                            double a = i * Math.PI / 4;
                            bullet(box.at.clone().add(Math.cos(a), 1.1, Math.sin(a)),
                                    new Vector(Math.cos(a), 0, Math.sin(a)).multiply(11), false, i, 500000, time);
                        }
                        burst(box.at.clone().add(0, 1, 0), Particle.FIREWORK, 30);
                    }
                }
                if (elapsed >= 4500) finishAttack();
            }
            case CAROUSEL -> {
                double angle = RingmasterGeometry.carouselAngle(sweepAngle, elapsed);
                if (!sweepActive && elapsed >= 2000) {
                    sweepActive = true; sweepRig.close(); sweepRig = beam(Material.PURPLE_STAINED_GLASS);
                }
                if (visual) {
                    sweepRig.move(center, Math.toDegrees(angle), 0, 1);
                    for (int i = 0; i < horses.size(); i++) {
                        double a = angle + (i >= 3 ? Math.PI : 0), r = 11 + (i % 3) * 12;
                        horses.get(i).move(center.clone().add(r * Math.cos(a), .3 + Math.sin(time / 180D + i) * .2, r * Math.sin(a)),
                                Math.toDegrees(a) + 90, 0, 1);
                    }
                    sweep(angle, elapsed < 2000 ? WARNING : DANGER);
                    sweep(angle + Math.PI, elapsed < 2000 ? WARNING : DANGER);
                    if (elapsed < 2000) { gapArrow(center, angle + .2); gapArrow(center, angle + Math.PI + .2); }
                }
                if (elapsed >= 2000 && elapsed < 10000) for (Player player : players) {
                    Location at = player.getLocation();
                    double x = at.getX() - center.getX(), z = at.getZ() - center.getZ();
                    if (onFloor(at) && (RingmasterGeometry.inSweep(x, z, angle) || RingmasterGeometry.inSweep(x, z, angle + Math.PI))
                            && time - sweepHits.getOrDefault(player.getUniqueId(), 0L) >= 900) {
                        sweepHits.put(player.getUniqueId(), time); hit(player, 800000);
                    }
                }
                if (elapsed >= 10000) finishAttack();
            }
            case SPOTLIGHT -> {
                if (visual) sectorOutline(elapsed < 7500 ? WARNING : DANGER);
                if (volley < 3 && elapsed >= 7500 + volley * 1500L) {
                    for (Player player : players) {
                        Location at = player.getLocation();
                        if (onFloor(at) && RingmasterGeometry.sector(at.getX() - center.getX(), at.getZ() - center.getZ()) == spotlight)
                            hit(player, 1100000);
                    }
                    for (int i = 0; i < 8; i++) {
                        double a = spotlight * Math.PI / 2 + (i - 3.5) * .15;
                        burst(center.clone().add(28 * Math.cos(a), .5, 28 * Math.sin(a)), Particle.FIREWORK, 12);
                    }
                    sound(Sound.ENTITY_FIREWORK_ROCKET_BLAST, .7F, .7F + volley * .15F);
                    volley++;
                }
                if (elapsed >= 11500) finishAttack();
            }
            case SCYTHES -> {
                if (scythes < 6 && elapsed >= scythes * 1800L) {
                    Player target = target(players);
                    if (target != null) {
                        Location at = target.getLocation().clone(); at.setY(center.getY());
                        BukkitRingmasterVisuals.Rig rig = visuals.scythe(at.clone().add(0, 12, 0));
                        rig.move(at.clone().add(0, 12, 0), scythes * 60, -.35, 1);
                        drops.add(new Drop(at, time, rig));
                    }
                    scythes++;
                }
                for (Drop drop : drops) {
                    if (drop.hit) continue;
                    long age = time - drop.created;
                    if (visual) {
                        ring(drop.at, 4, WARNING);
                        drop.rig.move(drop.at.clone().add(0, 12 * (1 - Math.clamp(age / 2200D, 0, 1)), 0),
                                age * .06, -.35, 1);
                    }
                    if (age >= 2200) {
                        drop.hit = true; drop.rig.close();
                        pulse(drop.at, 4, 1400000, players);
                        if (cleaned) return;
                        burst(drop.at.clone().add(0, .5, 0), Particle.EXPLOSION, 4);
                        sound(Sound.ENTITY_IRON_GOLEM_ATTACK, .7F, .5F);
                    }
                }
                if (elapsed >= 12500) finishAttack();
            }
        }
    }

    private Player nearest(List<Player> players, Location at) {
        return players.stream().min(java.util.Comparator.comparingDouble(p -> horizontalDistanceSquared(p.getLocation(), at))).orElse(null);
    }

    private Player target(List<Player> players) { return players.isEmpty() ? null : players.get(targetCursor++ % players.size()); }
    @Override public boolean busy() { return attack != null || !bullets.isEmpty(); }

    @Override
    public void cast(RingmasterEncounter.Attack kind, Instant now) {
        List<Player> players = players();
        audience = players;
        if (cleaned || players.isEmpty()) return;
        if (casts++ % 2 == 0) reposition();
        boss().setAI(kind == RingmasterEncounter.Attack.CAROUSEL);
        attack = kind; started = now.toEpochMilli(); volley = 0; scythes = 0; sweepActive = false;
        sweepHits.clear(); drops.clear();
        Location target = players.get(targetCursor % players.size()).getLocation();
        sweepAngle = Math.atan2(target.getZ() - center.getZ(), target.getX() - center.getX()) + Math.PI / 3;
        if (kind == RingmasterEncounter.Attack.CARD_RING) {
            Location origin = boss().getLocation();
            sweepAngle = Math.atan2(target.getZ() - origin.getZ(), target.getX() - origin.getX());
        }
        spotlight = RingmasterGeometry.sector(target.getX() - center.getX(), target.getZ() - center.getZ());
        if (kind == RingmasterEncounter.Attack.JACK_IN_THE_BOX) {
            for (int i = 0; i < Math.min(2, players.size()); i++) {
                Location at = players.get((targetCursor + i) % players.size()).getLocation().clone(); at.setY(center.getY());
                BukkitRingmasterVisuals.Rig rig = visuals.box(at); rig.move(at, 0, 0, 1);
                drops.add(new Drop(at, started, rig)); attackRigs.add(rig);
            }
            targetCursor++;
        }
        if (kind == RingmasterEncounter.Attack.CAROUSEL) {
            sweepRig = beam(Material.YELLOW_STAINED_GLASS);
            for (int i = 0; i < 6; i++) { var rig = visuals.horse(center); horses.add(rig); attackRigs.add(rig); }
        }
        if (kind == RingmasterEncounter.Attack.SPOTLIGHT) {
            double angle = spotlight * Math.PI / 2;
            var card = visuals.card(center.clone().add(25 * Math.cos(angle), 7, 25 * Math.sin(angle)), spotlight);
            card.move(center.clone().add(25 * Math.cos(angle), 7, 25 * Math.sin(angle)), spotlight * 90, 0, 5);
            attackRigs.add(card);
        }
        String cue = switch (kind) {
            case BALLOONS -> "<gold>POPCORN PANIC! Three balloon volleys. Sidestep or use cover.</gold>";
            case CARDS -> "<aqua>ROYAL FLUSH! Dodge the spinning cards.</aqua>";
            case JACK_IN_THE_BOX -> "<yellow>SURPRISE! Leave the gift circles before they burst.</yellow>";
            case CARD_RING -> "<aqua>DECK CYCLONE! Slip through the gap in the cards.</aqua>";
            case CAROUSEL -> "<yellow>DEVIL'S CAROUSEL! Fast spinning arms! Keep moving near the hub.</yellow>";
            case SPOTLIGHT -> "<yellow>SUIT ROULETTE! Leave the marked quarter. Three pulses.</yellow>";
            case SCYTHES -> "<light_purple><bold>THE LAST LAUGH! Six giant scythes. Keep moving!</bold></light_purple>";
        };
        notice(cue);
        for (Player player : players) player.sendActionBar(MiniMessageUtils.miniMessage(cue));
        if (kind == RingmasterEncounter.Attack.SCYTHES)
            title("<light_purple><bold>THE LAST LAUGH</bold></light_purple>", "<white>Keep moving. The scythes remember where you stood.</white>");
        sound(Sound.BLOCK_NOTE_BLOCK_CHIME, .8F, 1.4F);
    }

    private BukkitRingmasterVisuals.Rig beam(Material material) {
        var rig = visuals.rig(center);
        for (int side : new int[]{-1, 1}) rig.block(material, side * 23, .07, 0, 38, .08, 2);
        attackRigs.add(rig); return rig;
    }

    private void finishAttack() {
        attack = null;
        for (var rig : attackRigs) rig.close();
        attackRigs.clear(); horses.clear(); sweepRig = null;
        for (Drop drop : drops) drop.rig.close();
        drops.clear();
        if (boss != null && plugin.getServer().getEntity(boss) instanceof LivingEntity entity) entity.setAI(true);
    }

    private void reposition() {
        Point offset = context.arenaRotation().apply(switch (positionCursor++ % 4) {
            case 0 -> new Point(20, 0, 0);
            case 1 -> new Point(0, 0, 20);
            case 2 -> new Point(-20, 0, 0);
            default -> new Point(0, 0, -20);
        });
        Location at = center.clone().add(offset.x(), 0, offset.z());
        at.setDirection(center.toVector().subtract(at.toVector()));
        if (!at.getBlock().isPassable() || !at.clone().add(0, 2, 0).getBlock().isPassable())
            throw new IllegalStateException("ringmaster ground position is obstructed; use the grand carnival arena");
        burst(boss().getLocation().add(0, 1, 0), Particle.REVERSE_PORTAL, 40);
        boss().teleport(at); ring(at, 2, SAFE);
    }

    private void fan(Player target, boolean cards, long time) {
        Location origin = boss().getLocation().add(0, 1.5, 0);
        Vector aim = target.getLocation().add(0, .9, 0).toVector().subtract(origin.toVector());
        aim.setY(Math.clamp(aim.getY(), -2, 2));
        if (aim.lengthSquared() < .01) aim = new Vector(1, 0, 0);
        aim.normalize();
        int count = cards ? 7 : 5;
        for (int i = 0; i < count; i++)
            bullet(origin.clone(), aim.clone().rotateAroundY((i - (count - 1) / 2D) * .19).multiply(cards ? 18 : 15),
                    cards, i + volley, cards ? 650000 : 500000, time);
        sound(cards ? Sound.ENTITY_PLAYER_ATTACK_SWEEP : Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, .6F, 1.5F);
    }

    private void deckRing(Location origin, double gap, long time) {
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI / 12;
            if (RingmasterGeometry.inDeckGap(angle, gap)) continue;
            bullet(origin.clone().add(2 * Math.cos(angle), 0, 2 * Math.sin(angle)),
                    new Vector(Math.cos(angle), 0, Math.sin(angle)).multiply(13), true, i, 650000, time);
        }
        sound(Sound.ENTITY_EVOKER_CAST_SPELL, .65F, .8F);
    }

    private void bullet(Location at, Vector velocity, boolean card, int color, double damage, long time) {
        if (bullets.size() >= PROJECTILE_CAP) return;
        var rig = card ? visuals.card(at, color) : visuals.balloon(at, color);
        rig.move(at, Math.toDegrees(Math.atan2(-velocity.getX(), velocity.getZ())), 0, 1);
        bullets.add(new Bullet(at, velocity, rig, card, damage, time));
    }

    private void tickBullets(List<Player> players, double seconds, long time, boolean visual) {
        Iterator<Bullet> iterator = bullets.iterator();
        while (iterator.hasNext()) {
            Bullet bullet = iterator.next();
            Vector step = bullet.velocity.clone().multiply(seconds);
            boolean remove = time - bullet.created > 5500 || horizontalDistanceSquared(bullet.at, center) > 47 * 47;
            if (!remove && step.lengthSquared() > 0) {
                remove = center.getWorld().rayTraceBlocks(bullet.at, step.clone().normalize(), step.length(),
                        FluidCollisionMode.NEVER, true) != null;
                if (!remove) for (Player player : players) {
                    Vector to = player.getLocation().add(0, .9, 0).toVector().subtract(bullet.at.toVector());
                    double fraction = Math.clamp(to.dot(step) / step.lengthSquared(), 0, 1);
                    if (to.subtract(step.clone().multiply(fraction)).lengthSquared() <= .75 * .75) {
                        hit(player, bullet.damage);
                        if (cleaned) return;
                        remove = true; break;
                    }
                }
            }
            if (remove) {
                if (visual) burst(bullet.at, Particle.FIREWORK, 5);
                bullet.rig.close(); iterator.remove(); continue;
            }
            bullet.at.add(step);
            if (visual) {
                double yaw = Math.toDegrees(Math.atan2(-bullet.velocity.getX(), bullet.velocity.getZ()));
                bullet.rig.move(bullet.at, yaw, bullet.card ? (time - bullet.created) / 500D : Math.sin(time / 180D) * .12, 1);
                particle(bullet.at.getX(), bullet.at.getY(), bullet.at.getZ(), bullet.card ? SAFE : DANGER);
            }
        }
    }

    private void hit(Player player, double damage) { if (alive(player)) player.damage(damage, boss()); }
    private void pulse(Location at, double radius, double damage, List<Player> players) {
        for (Player p : players) if (onFloor(p.getLocation()) && horizontalDistanceSquared(p.getLocation(), at) <= radius * radius) hit(p, damage);
        ring(at, radius, DANGER);
    }
    private boolean onFloor(Location at) {
        return at.getY() >= center.getY() - 1 && at.getY() <= center.getY() + 12
                && RingmasterGeometry.onStage(at.getX() - center.getX(), at.getZ() - center.getZ());
    }
    private static double horizontalDistanceSquared(Location a, Location b) {
        double x = a.getX() - b.getX(), z = a.getZ() - b.getZ(); return x * x + z * z;
    }
    private void ring(Location at, double radius, Particle.DustOptions dust) {
        int count = Math.max(32, (int) (radius * 6));
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count;
            particle(at.getX() + radius * Math.cos(a), center.getY() + .12, at.getZ() + radius * Math.sin(a), dust);
        }
    }
    private void sweep(double angle, Particle.DustOptions dust) {
        for (double r = 4; r <= RADIUS; r += 1) {
            double x = center.getX() + r * Math.cos(angle), z = center.getZ() + r * Math.sin(angle);
            particle(x - Math.sin(angle), center.getY() + .15, z + Math.cos(angle), dust);
            particle(x + Math.sin(angle), center.getY() + .15, z - Math.cos(angle), dust);
        }
    }
    private void gapArrow(Location at, double angle) {
        for (int i = 0; i < 9; i++) {
            double r = 6 + i * .5;
            particle(at.getX() + r * Math.cos(angle), center.getY() + .18, at.getZ() + r * Math.sin(angle), SAFE);
        }
        for (int side : new int[]{-1, 1}) for (int i = 0; i < 5; i++) {
            double r = 10 - i * .4;
            particle(at.getX() + r * Math.cos(angle) + side * i * .3 * Math.sin(angle), center.getY() + .18,
                    at.getZ() + r * Math.sin(angle) - side * i * .3 * Math.cos(angle), SAFE);
        }
    }
    private void sectorOutline(Particle.DustOptions dust) {
        double a = spotlight * Math.PI / 2;
        for (double r = 4; r <= RADIUS; r += 1) for (double side : new double[]{-Math.PI / 4, Math.PI / 4})
            particle(center.getX() + r * Math.cos(a + side), center.getY() + .15, center.getZ() + r * Math.sin(a + side), dust);
        for (double t = a - Math.PI / 4; t <= a + Math.PI / 4; t += .045)
            particle(center.getX() + RADIUS * Math.cos(t), center.getY() + .15, center.getZ() + RADIUS * Math.sin(t), dust);
        // Interior stripes show which quarter is dangerous even when its borders are far away.
        for (double r = 8; r < RADIUS; r += 6) for (double t = a - .7; t <= a + .7; t += .16)
            particle(center.getX() + r * Math.cos(t), center.getY() + .12, center.getZ() + r * Math.sin(t), dust);
    }
    private void particle(double x, double y, double z, Particle.DustOptions dust) {
        for (Player p : audience) p.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust, true);
    }
    private void burst(Location at, Particle particle, int count) {
        for (Player p : audience) p.spawnParticle(particle, at, count, .8, .8, .8, .02);
    }
    private void sound(Sound sound, float volume, float pitch) {
        for (Player p : audience) p.playSound(p.getLocation(), sound, volume, pitch);
    }
    private void music(long time) {
        if (time - lastBeat < (life == 2 ? 420 : 650)) return;
        lastBeat = time;
        int[] notes = {0, 3, 7, 10, 7, 3, 1, 6, 8, 6, 3, 1};
        float pitch = (float) Math.pow(2, (notes[beat % notes.length] - 6) / 12D);
        sound(life == 2 ? Sound.BLOCK_NOTE_BLOCK_BIT : Sound.BLOCK_NOTE_BLOCK_CHIME, .32F, pitch);
        if (beat++ % 3 == 0) sound(Sound.BLOCK_NOTE_BLOCK_BASEDRUM, .22F, .7F);
    }
    private void title(String text, String subtitle) {
        Title title = Title.title(MiniMessageUtils.miniMessage(text), MiniMessageUtils.miniMessage(subtitle),
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(500)));
        for (Player p : audience) p.showTitle(title);
    }

    @Override
    public void summonPerformers() {
        List<Player> players = players();
        if (players.isEmpty()) return;
        int count = partySize == 1 ? 1 : 3, cap = partySize == 1 ? 2 : 6;
        for (int i = 0; i < count && performers.size() < cap; i++) {
            Point offset = context.arenaRotation().apply(new Point(i == 0 ? 12 : -12, 0, i == 2 ? 12 : -12));
            Location at = center.clone().add(offset.x(), 0, offset.z());
            if (!at.getBlock().isPassable()) continue;
            ring(at, 2, WARNING);
            var spawned = mythic.spawn(i == 1 ? "RingmasterAcrobat" : "RingmasterPerformer", at, 1);
            if (!spawned.successful() || !(spawned.entity() instanceof Mob mob))
                throw new IllegalStateException("ringmaster performer spawn failed: " + spawned.detail());
            performers.add(mob.getUniqueId()); mob.setTarget(nearest(players, at));
            difficulty.spawn(mob, new BukkitDifficultyService.Spawn(context.instanceId(), -2,
                    new Point(at.getBlockX(), at.getBlockY(), at.getBlockZ()), false));
            burst(at.clone().add(0, 1, 0), Particle.FIREWORK, 20);
        }
    }
    @Override public double healthFraction() {
        return MythicBukkit.inst().getMobManager().getActiveMob(boss)
                .map(active -> active.getEntity().getHealth() / active.getEntity().getMaxHealth()).orElse(1D);
    }
    @Override public void notice(String message) {
        runs.info(context.instanceId()).ifPresent(run -> run.participants().stream()
                .map(plugin.getServer()::getPlayer).filter(java.util.Objects::nonNull)
                .forEach(p -> DungeonMessages.send(p, message)));
    }
    @Override public void clear() {
        for (UUID id : performers) {
            Entity entity = plugin.getServer().getEntity(id);
            if (entity != null && !mythic.remove(entity)) entity.remove();
        }
        performers.clear(); bullets.clear(); drops.clear(); attackRigs.clear(); horses.clear(); sweepHits.clear();
        if (visuals != null) visuals.clear();
        bossRig = null; movie = null; sweepRig = null; transitionAt = 0; attack = null;
    }
    @Override public void cleanup() {
        if (cleaned) return;
        cleaned = true; clear(); audience = List.of(); HandlerList.unregisterAll(this);
    }
    private boolean owns(Entity entity) {
        return entity != null && (entity.getUniqueId().equals(boss) || performers.contains(entity.getUniqueId()));
    }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent event) {
        Entity attacker = event.getDamager();
        if (attacker instanceof org.bukkit.entity.Projectile p && p.getShooter() instanceof Entity shooter) attacker = shooter;
        if (owns(attacker) && event.getEntity() instanceof Player player && !alive(player)) event.setCancelled(true);
        if (owns(event.getEntity()) && attacker instanceof Player player && (!alive(player) || opening)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void target(EntityTargetLivingEntityEvent event) {
        if (owns(event.getEntity()) && (!(event.getTarget() instanceof Player player) || !alive(player))) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void drops(EntityDeathEvent event) {
        if (owns(event.getEntity())) { event.getDrops().clear(); event.setDroppedExp(0); }
    }
    private record Bullet(Location at, Vector velocity, BukkitRingmasterVisuals.Rig rig, boolean card, double damage, long created) { }
    private static final class Drop {
        private final Location at;
        private final long created;
        private final BukkitRingmasterVisuals.Rig rig;
        private boolean hit;
        private Drop(Location at, long created, BukkitRingmasterVisuals.Rig rig) { this.at = at; this.created = created; this.rig = rig; }
    }
}
