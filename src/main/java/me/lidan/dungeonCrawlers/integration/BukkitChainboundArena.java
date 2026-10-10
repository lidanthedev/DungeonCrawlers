package me.lidan.dungeonCrawlers.integration;

import io.lumine.mythic.bukkit.MythicBukkit;
import me.lidan.cavecrawlers.damage.DamageCalculationEvent;
import me.lidan.cavecrawlers.utils.MiniMessageUtils;
import me.lidan.dungeonCrawlers.core.encounter.ChainboundEncounter;
import me.lidan.dungeonCrawlers.core.encounter.ChainboundEncounter.Attack;
import me.lidan.dungeonCrawlers.core.encounter.ChainboundEncounter.Settings;
import me.lidan.dungeonCrawlers.core.encounter.ChainboundEncounter.Stage;
import me.lidan.dungeonCrawlers.core.encounter.EncounterFactory.EncounterContext;
import me.lidan.dungeonCrawlers.core.encounter.FoundryGeometry;
import me.lidan.dungeonCrawlers.core.encounter.CounterweightTrial;
import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.protection.TeleportPermitService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Multiplayer presentation and collision mechanics run entirely on the existing encounter tick. */
public final class BukkitChainboundArena implements ChainboundEncounter.Arena, Listener {
    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 193, 65), 1.7F);
    private static final Particle.DustOptions CYAN = new Particle.DustOptions(Color.fromRGB(64, 240, 220), 1.8F);
    private static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(255, 65, 94), 1.8F);
    private final EncounterContext context;
    private final Plugin plugin;
    private final GenerationService generation;
    private final RunPreparationService runs;
    private final PlayerLifecycleService lifecycle;
    private final TeleportPermitService permits;
    private final Clock clock;
    private final Map<UUID, Long> hitTimes = new HashMap<>();
    private final Set<Player> barViewers = new HashSet<>();
    private final List<Location> brands = new ArrayList<>();
    private final Map<UUID, Location> tethers = new LinkedHashMap<>();
    private CounterweightTrial counterweightTrial;
    private long staggerUntil, lanceAt;
    private boolean counterweightResolved, cageResolved;
    private final BossBar bar = BossBar.bossBar(MiniMessageUtils.miniMessage("<aqua>Veyra · The Chainbound Architect"),
            1, BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_10);
    private BukkitFoundryScene scene;
    private Settings settings;
    private UUID bossId;
    private Location center;
    private List<Player> audience = List.of();
    private Stage stage = Stage.NEW;
    private Attack attack;
    private long stageAt, castAt, lastVisual, lastMusic, lastPosition;
    private int casts, pulses, safeQuadrant, bridge, introCue, transformCue, beat;
    private double aim;
    private boolean lowered, bridgeRetracted, victoryRestored, cleaned, registered, guillotineHit;

    public BukkitChainboundArena(EncounterContext context, Plugin plugin, GenerationService generation,
                                RunPreparationService runs, PlayerLifecycleService lifecycle,
                                TeleportPermitService permits, Clock clock) {
        this.context = context; this.plugin = plugin; this.generation = generation; this.runs = runs;
        this.lifecycle = lifecycle; this.permits = permits; this.clock = clock;
    }

    @Override public void begin(UUID boss, Settings settings) {
        this.bossId = boss; this.settings = settings;
        LivingEntity actor = boss();
        if (scene == null) {
            Point origin = context.arenaCenter();
            center = new Location(actor.getWorld(), origin.x() + .5, origin.y(), origin.z() + .5);
            scene = new BukkitFoundryScene(center, context.instanceId());
        }
        actor.setInvulnerable(true);
        actor.setAI(false);
        var active = MythicBukkit.inst().getMobManager().getActiveMob(boss).orElseThrow();
        double existingMultiplier = active.getEntity().getMaxHealth() / Settings.defaults().health();
        int party = runs.info(context.instanceId()).orElseThrow().participants().size();
        active.getEntity().setHealthAndMax(settings.health() * existingMultiplier * (1 + .45 * (party - 1)));
        actor.setRemoveWhenFarAway(false);
        actor.addScoreboardTag("foundry_boss");
        if (actor.getAttribute(Attribute.SCALE) != null) actor.getAttribute(Attribute.SCALE).setBaseValue(1.6);
        if (!registered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
    }

    private LivingEntity boss() {
        Entity entity = bossId == null ? null : plugin.getServer().getEntity(bossId);
        if (!(entity instanceof LivingEntity actor) || !actor.isValid()) throw new IllegalStateException("Veyra is missing");
        return actor;
    }

    private boolean alive(Player player) {
        return player.isOnline() && runs.instanceFor(player.getUniqueId()).filter(context.instanceId()::equals).isPresent()
                && lifecycle.player(context.instanceId(), player.getUniqueId()).filter(p -> p.online()
                && p.state() == PlayerLifecycleService.PlayerState.ALIVE).isPresent();
    }

    private List<Player> participants() {
        if (center == null) return List.of();
        return runs.info(context.instanceId()).map(run -> run.participants().stream().sorted()
                .map(plugin.getServer()::getPlayer).filter(p -> p != null && p.isOnline() && p.getWorld().equals(center.getWorld()))
                .filter(p -> horizontal(p.getLocation(), center) <= 42 * 42).toList()).orElse(List.of());
    }

    @Override public void stage(Stage next, Instant now) {
        clearStagger();
        cancelAttack();
        stage = next; stageAt = now.toEpochMilli(); pulses = 0;
        audience = participants();
        if (next != Stage.DYING) {
            boolean cinematic = next == Stage.INTRO || next == Stage.TRANSFORM;
            boss().setAI(!cinematic); boss().setInvulnerable(cinematic);
            boss().setVelocity(new Vector());
        }
        bar.name(MiniMessageUtils.miniMessage("<aqua>Veyra · The Chainbound Architect"));
        switch (next) {
            case INTRO -> {
                title("<aqua><bold>VEYRA, THE CHAINBOUND ARCHITECT", "<gray>Every stone remembers its maker.");
                sound(Sound.BLOCK_BELL_RESONATE, .8F, .5F);
                notice("<aqua>Veyra: You have mistaken my prison for your passage.");
            }
            case FIRST -> {
                boss().teleport(center); boss().setFallDistance(0);
                notice("<gold>Watch the golden telegraphs. Cyan counterweights can break the architect's hold.");
            }
            case TRANSFORM -> {
                scene.beginTransformation();
                title("<dark_red><bold>THE WORLD WILL KNEEL", "<gold>Brace at the heart. The cathedral is coming apart.");
                notice("<red>Veyra: I built this heaven. I can unmake it.");
                sound(Sound.ENTITY_WITHER_SPAWN, .9F, .6F);
                relocateAll(false);
            }
            case RIVEN -> {
                scene.reveal();
                lowered = true; relocateAll(true);
                boss().teleport(center.clone().add(0, -FoundryGeometry.DROP, 0));
                boss().setFallDistance(0);
                title("<red><bold>THE RIVEN CRUCIBLE", "<aqua>Use the spokes and islands. Cyan marks refuge.");
                sound(Sound.ENTITY_ENDER_DRAGON_GROWL, .7F, .55F);
                notice("<red>Veyra: No sky. No mercy. Only the weight of your mistakes.");
                bar.color(BossBar.Color.RED);
            }
            case FINAL -> {
                title("<dark_red><bold>THE LAST WEAVE", "<gold>Three lances. Crossing chains. The heart demands a counterweight.");
                notice("<red>Veyra: Then let the heart break with us!");
                sound(Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, .85F, .65F);
                boss().setGlowing(true);
                bar.color(BossBar.Color.PURPLE);
            }
            case DYING -> {
                title("<aqua><bold>THE LAST CHAIN BREAKS", "<gold>The foundry remembers freedom.");
                sound(Sound.ENTITY_IRON_GOLEM_DEATH, 1, .6F);
                notice("<gray>Veyra: I only wanted... to hold it together.");
            }
            default -> { }
        }
    }

    @Override public void tick(Instant now) {
        if (cleaned) return;
        long time = now.toEpochMilli();
        audience = participants();
        Set<Player> current = new HashSet<>(audience);
        for (Player previous : new HashSet<>(barViewers)) if (!current.contains(previous)) {
            previous.hideBossBar(bar); barViewers.remove(previous);
        }
        for (Player player : audience) if (barViewers.add(player)) player.showBossBar(bar);
        if (time - lastVisual < 100) return;
        lastVisual = time;
        music(time);
        double elapsed = time - stageAt;
        if (stage == Stage.DYING) {
            double progress = Math.clamp(elapsed / settings.deathMillis(), 0, 1);
            scene.death(progress);
            ring(floor(), 4 + progress * 26, CYAN);
            if (progress >= .8 && !victoryRestored) {
                victoryRestored = true;
                scene.restore();
                if (lowered) relocateAll(false);
                sound(Sound.UI_TOAST_CHALLENGE_COMPLETE, .9F, .8F);
                for (Player player : audience) player.spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 2, 0), 12, 1, 1, 1, .02);
            }
            return;
        }
        scene.bossCrown(boss().getLocation(), stage == Stage.RIVEN || stage == Stage.FINAL ? 1.4 : 1, time);
        bar.progress((float) Math.clamp(healthFraction(), 0, 1));
        if (stage == Stage.INTRO) {
            double rise = Math.sin(elapsed / settings.introMillis() * Math.PI) * 1.5;
            boss().teleport(center.clone().add(0, rise, 0));
            for (int q = 0; q < 4; q++) scene.combatChain(q,
                    center.clone().add(q % 2 == 0 ? 18 : -18, 9, q < 2 ? 18 : -18),
                    boss().getLocation().add(0, 2, 0), elapsed / settings.introMillis());
            ring(center, 3 + elapsed / settings.introMillis() * 24, CYAN);
            if (introCue == 0 && elapsed >= settings.introMillis() / 2D) {
                introCue++;
                notice("<gray>Veyra: Hear the bells. They toll for you.");
                sound(Sound.BLOCK_CHAIN_PLACE, .8F, .5F);
            }
            return;
        }
        if (stage == Stage.TRANSFORM) {
            double progress = Math.clamp(elapsed / settings.transformMillis(), 0, 1);
            scene.transform(progress);
            ring(center, 8, CYAN);
            // The safe hub descends first; later removal can never drop participants into an unbuilt floor.
            if (progress >= .30 && !lowered) {
                lowered = true; relocateAll(true);
                boss().teleport(floor()); boss().setFallDistance(0);
            }
            int cue = (int) (progress * 8);
            if (cue > transformCue) {
                transformCue = cue;
                sound(Sound.BLOCK_ANVIL_LAND, .8F, .55F + cue * .05F);
                for (Player player : audience) player.spawnParticle(Particle.BLOCK, center.clone().add(0, 4, 0), 12,
                        10, 3, 10, org.bukkit.Material.DEEPSLATE_TILES.createBlockData());
            }
            return;
        }
        List<Player> fighters = audience.stream().filter(this::alive).toList();
        if (staggerUntil > 0 && time >= staggerUntil) {
            staggerUntil = 0; boss().setAI(true); boss().setGlowing(stage == Stage.FINAL);
            notice("<red>Veyra: You will not turn my own chains against me twice!");
        }
        if (time - lastPosition >= 500) {
            lastPosition = time;
            if (staggerUntil == 0 && boss() instanceof Mob mob) mob.setTarget(nearest(fighters, mob.getLocation()));
            if (horizontal(boss().getLocation(), center) > 31 * 31 || boss().getLocation().getY() < floor().getY() - 1)
                boss().teleport(floor());
            for (Player player : fighters) {
                if (player.getY() < floor().getY() - .5 || horizontal(player.getLocation(), center) > 33 * 33) {
                    teleport(player, floor().add(0, 0, 5));
                    hit(player, settings.damage() * .5, time);
                }
            }
        }
        if (attack != null) tickAttack(time, fighters);
    }

    @Override public void cast(Attack next, Instant now) {
        cancelAttack();
        attack = next; castAt = now.toEpochMilli(); pulses = 0;
        guillotineHit = false;
        casts++;
        List<Player> fighters = participants().stream().filter(this::alive).toList();
        Player target = fighters.isEmpty() ? null : fighters.get(casts % fighters.size());
        Location at = target == null ? center : target.getLocation();
        aim = Math.atan2(at.getZ() - center.getZ(), at.getX() - center.getX());
        safeQuadrant = FoundryGeometry.quadrant(at.getX() - center.getX(), at.getZ() - center.getZ());
        bridge = Math.floorMod((int) Math.round(aim / (Math.PI / 2)), 4);
        lanceAt = castAt;
        counterweightResolved = false; cageResolved = false;
        if (next == Attack.CHAIN_CAGE) fighters.forEach(player -> tethers.put(player.getUniqueId(), player.getLocation().clone()));
        if (next == Attack.COUNTERWEIGHTS) {
            counterweightTrial = new CounterweightTrial(Math.min(settings.warningMillis(), 1800));
            scene.counterweights(lowered, false);
            boss().setAI(false); boss().setVelocity(new Vector());
        }
        context.diagnostics().accept("chainbound attack=" + next + " stage=" + stage);
        if (next == Attack.BRANDS || next == Attack.LAST_WEAVE)
            fighters.forEach(player -> brands.add(player.getLocation().clone()));
        String instruction = switch (next) {
            case FORGE_WAVE -> "FORGE WAVE · Jump the expanding ring";
            case BRANDS -> "SHATTERED BRANDS · Spread, then leave your marked ground";
            case LANCE -> stage == Stage.FINAL ? "CANTOR'S REPRISAL · Three locked lines; dodge each strike" : "CANTOR'S LANCE · Step out of the golden line";
            case CHAIN_DRAW -> "CHAIN DRAW · A spoke is retracting; take the diagonal bridges";
            case GUILLOTINE -> "HEAVEN'S GUILLOTINE · Reach the cyan hub or island";
            case RIFT_PULSE -> "RIFT PULSE · Jump the rings while avoiding the crosscut";
            case CHAIN_CAGE -> "CHAIN CAGE · Run outside your golden circle before the chain locks";
            case CLOCKWORK_REQUIEM -> "CLOCKWORK REQUIEM · Jump the chain cross; follow its rotation";
            case COUNTERWEIGHTS -> fighters.size() > 1 ? "COUNTERWEIGHT VERDICT · Hold BOTH cyan pads with different players" : "COUNTERWEIGHT VERDICT · Hold either cyan pad to stagger Veyra";
            case LAST_WEAVE -> "LAST WEAVE · Leave your brand, dodge chains, then reach cyan";
        };
        bar.name(MiniMessageUtils.miniMessage("<aqua>Veyra <gray>· <gold>" + instruction.split(" · ")[0]));
        title("<gold><bold>" + instruction.split(" · ")[0], "<white>" + instruction.split(" · ")[1]);
        sound(Sound.BLOCK_BELL_USE, .55F, next == Attack.LAST_WEAVE ? .5F : 1.2F);
    }

    private void tickAttack(long time, List<Player> fighters) {
        long elapsed = time - castAt;
        long warn = settings.warningMillis();
        Location origin = floor();
        boolean telegraph = elapsed < warn;
        switch (attack) {
            case FORGE_WAVE -> {
                double radius = telegraph ? 3 : Math.clamp((elapsed - warn) / 100D, 0, 32);
                ring(origin, radius, telegraph ? GOLD : RED);
                if (!telegraph) for (Player player : fighters) {
                    double x = player.getX() - center.getX(), z = player.getZ() - center.getZ();
                    if (FoundryGeometry.wave(x, z, radius, 1.6) && grounded(player)) hit(player, settings.damage(), time);
                }
            }
            case BRANDS -> {
                for (Location mark : brands) {
                    mark.setY(origin.getY());
                    ring(mark, 3.5, telegraph ? GOLD : RED);
                    if (!telegraph && pulses == 0) for (Player player : fighters)
                        if (horizontal(player.getLocation(), mark) <= 12.25) hit(player, settings.damage() * 1.25, time);
                }
                if (!telegraph && pulses++ == 0) sound(Sound.ENTITY_GENERIC_EXPLODE, .7F, .8F);
            }
            case LANCE -> {
                int strikes = stage == Stage.FINAL ? 3 : 1;
                line(origin, aim, GOLD);
                long deadline = pulses == 0 ? castAt + warn : lanceAt + 1000;
                if (pulses < strikes && time >= deadline) {
                    line(origin, aim, RED);
                    sound(Sound.ENTITY_IRON_GOLEM_ATTACK, .8F, .6F + pulses * .2F);
                    for (Player player : fighters) if (FoundryGeometry.lane(player.getX() - center.getX(),
                            player.getZ() - center.getZ(), aim, 2)) hit(player, settings.damage() * 1.35, time);
                    if (cleaned) return;
                    Location dash = origin.clone().add(Math.cos(aim) * 22, 0, Math.sin(aim) * 22);
                    if (!lowered || FoundryGeometry.lowerPlatform(dash.getX() - center.getX(), dash.getZ() - center.getZ())) boss().teleport(dash);
                    pulses++; lanceAt = time;
                    if (pulses < strikes && !fighters.isEmpty()) {
                        Player target = fighters.get((casts + pulses) % fighters.size());
                        aim = Math.atan2(target.getZ() - center.getZ(), target.getX() - center.getX());
                        sound(Sound.BLOCK_BELL_USE, .5F, 1.4F);
                    }
                }
            }
            case CHAIN_CAGE -> chainCage(time, elapsed, warn, origin, fighters);
            case CLOCKWORK_REQUIEM -> clockwork(time, elapsed, warn, origin, fighters);
            case COUNTERWEIGHTS -> counterweights(time, elapsed, warn, origin, fighters);
            case CHAIN_DRAW, LAST_WEAVE -> {
                line(origin, bridge * Math.PI / 2, telegraph ? GOLD : RED);
                if (!telegraph && !bridgeRetracted) {
                    scene.drawBridge(bridge, true); bridgeRetracted = true;
                    sound(Sound.BLOCK_CHAIN_BREAK, .9F, .6F);
                }
                if (!telegraph) scene.animateBridge(Math.clamp((elapsed - warn) / 2200D, 0, 1));
                if (!telegraph && elapsed < warn + 1800) {
                    double angle = aim + (elapsed - warn) / 1800D * Math.PI / 2;
                    line(origin, angle, RED);
                    for (Player player : fighters) {
                        double x = player.getX() - center.getX(), z = player.getZ() - center.getZ();
                        if (FoundryGeometry.lane(x, z, angle, 1.5) && grounded(player)) {
                            hit(player, settings.damage() * 1.1, time);
                            if (alive(player)) player.setVelocity(new Vector(-x, .25, -z).normalize().multiply(.45));
                        }
                    }
                }
                if (attack == Attack.LAST_WEAVE) {
                    long secondWarn = warn + 1200;
                    if (elapsed < secondWarn + 1200) for (Location mark : brands) ring(mark, 3.5, GOLD);
                    if (elapsed >= secondWarn + 1200 && pulses == 0) {
                        pulses++;
                        for (Player player : fighters) if (brands.stream().anyMatch(mark -> horizontal(mark, player.getLocation()) < 12.25))
                            hit(player, settings.damage() * 1.4, time);
                    }
                    guillotine(origin, elapsed, secondWarn + 1200, fighters, time);
                }
            }
            case GUILLOTINE -> guillotine(origin, elapsed, warn + 3000, fighters, time);
            case RIFT_PULSE -> {
                double radius = telegraph ? 3 : ((elapsed - warn) % 1200) / 38D;
                ring(origin, radius, telegraph ? GOLD : RED);
                double angle = aim + Math.PI / 2;
                line(origin, angle, elapsed < warn + 1000 ? GOLD : RED);
                if (!telegraph) for (Player player : fighters) {
                    double x = player.getX() - center.getX(), z = player.getZ() - center.getZ();
                    if (grounded(player) && (FoundryGeometry.wave(x, z, radius, 1.6)
                            || elapsed >= warn + 1000 && FoundryGeometry.lane(x, z, angle, 1.5)))
                        hit(player, settings.damage(), time);
                }
            }
        }
        if (elapsed >= warn + 3400) cancelAttack();
    }

    private void chainCage(long time, long elapsed, long warn, Location origin, List<Player> fighters) {
        if (cageResolved) return;
        scene.hideCombatChains();
        int slot = 0;
        for (var tether : new ArrayList<>(tethers.entrySet())) {
            Player player = fighters.stream().filter(p -> p.getUniqueId().equals(tether.getKey())).findFirst().orElse(null);
            Location anchor = tether.getValue().clone(); anchor.setY(origin.getY());
            if (player == null || horizontal(anchor, player.getLocation()) >= 49) {
                tethers.remove(tether.getKey());
                if (player != null) {
                    player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, .9F, 1.4F);
                    ring(anchor, 7, CYAN);
                }
                continue;
            }
            ring(anchor, 7, elapsed < warn ? GOLD : RED);
            scene.combatChain(slot++, anchor.clone().add(0, .3, 0), player.getLocation().add(0, 1, 0),
                    Math.clamp(elapsed / (double) (warn + 2200), 0, 1));
            if (elapsed >= warn + 2200) {
                hit(player, settings.damage() * 1.5, time);
                if (cleaned) return;
            } else if (elapsed >= warn + 1000) {
                Vector pull = anchor.toVector().subtract(player.getLocation().toVector()).setY(.1);
                if (pull.lengthSquared() > .01) player.setVelocity(pull.normalize().multiply(.18));
            }
        }
        if (elapsed >= warn + 2200) {
            cageResolved = true; sound(Sound.BLOCK_CHAIN_BREAK, .8F, .5F); scene.hideCombatChains();
            context.diagnostics().accept("chainbound cage unresolved=" + tethers.size());
        }
    }

    private void clockwork(long time, long elapsed, long warn, Location origin, List<Player> fighters) {
        double rotation = elapsed < warn ? 0 : (elapsed - warn) / 3400D * Math.PI * (stage == Stage.FINAL ? 1.5 : 1);
        double angle = aim + rotation;
        for (int arm = 0; arm < 4; arm++) {
            double direction = angle + arm * Math.PI / 2;
            scene.combatChain(arm, origin.clone().add(Math.cos(direction) * 5, .6, Math.sin(direction) * 5),
                    origin.clone().add(Math.cos(direction) * 30, .6, Math.sin(direction) * 30), 1);
            for (int r = 6; r <= 30; r += 3) particle(origin.clone().add(Math.cos(direction) * r, .2,
                    Math.sin(direction) * r), elapsed < warn ? GOLD : RED);
        }
        double radius = elapsed < warn ? 3 : ((elapsed - warn) % 1600) / 50D;
        if (stage == Stage.FINAL) ring(origin, radius, elapsed < warn ? GOLD : RED);
        if (elapsed >= warn) for (Player player : fighters) {
            double x = player.getX() - center.getX(), z = player.getZ() - center.getZ();
            boolean chains = Math.hypot(x, z) > 5 && Math.hypot(x, z) <= 30.5 && (FoundryGeometry.lane(x, z, angle, 1.1)
                    || FoundryGeometry.lane(x, z, angle + Math.PI / 2, 1.1));
            if (grounded(player) && (chains || stage == Stage.FINAL && FoundryGeometry.wave(x, z, radius, 1.6)))
                hit(player, settings.damage() * 1.1, time);
        }
        if (elapsed >= warn && pulses++ % 5 == 0) sound(Sound.BLOCK_CHAIN_STEP, .45F, .6F);
    }

    private void counterweights(long time, long elapsed, long warn, Location origin, List<Player> fighters) {
        if (counterweightResolved) return;
        double progress = counterweightTrial.progress(time);
        ring(origin.clone().add(-5, 0, 0), 1.5, CYAN);
        ring(origin.clone().add(5, 0, 0), 1.5, CYAN);
        bar.name(MiniMessageUtils.miniMessage("<aqua>Veyra <gray>· <gold>COUNTERWEIGHTS <white>" + Math.round(progress * 100) + "%"));
        scene.combatChain(0, origin.clone().add(-5, .2, 0), boss().getLocation().add(0, 2, 0), progress);
        scene.combatChain(1, origin.clone().add(5, .2, 0), boss().getLocation().add(0, 2, 0), progress);
        var positions = fighters.stream().map(player -> new CounterweightTrial.Position(
                grounded(player) && player.getY() >= origin.getY() - .2 ? player.getX() - center.getX() : Double.MAX_VALUE, player.getZ() - center.getZ())).toList();
        boolean success = counterweightTrial.update(time, positions);
        if (success || elapsed >= warn + 2800) {
            counterweightResolved = true;
            if (success) {
                scene.counterweights(lowered, true);
                boss().setAI(false); boss().setVelocity(new Vector()); boss().setGlowing(true);
                staggerUntil = time + 3000;
                bar.name(MiniMessageUtils.miniMessage("<aqua>Veyra <gray>· <white>STAGGERED"));
                MythicBukkit.inst().getMobManager().getActiveMob(bossId).ifPresent(active -> {
                    double health = active.getEntity().getHealth();
                    double wound = Math.min(active.getEntity().getMaxHealth() * .03, allowedDamage());
                    active.getEntity().setHealth(Math.max(1, health - wound));
                });
                title("<aqua><bold>THE ARCHITECT STAGGERS", "<white>The chains wound Veyra. Strike while she is staggered!");
                sound(Sound.BLOCK_ANVIL_LAND, 1, .5F);
            } else {
                boss().setAI(true);
                ring(origin, 32, RED); sound(Sound.ENTITY_GENERIC_EXPLODE, .8F, .6F);
                for (Player player : fighters) hit(player, settings.damage() * 1.3, time);
            }
            context.diagnostics().accept("chainbound counterweights=" + (success ? "broken" : "failed"));
        }
    }

    private void guillotine(Location origin, long elapsed, long deadline, List<Player> fighters, long time) {
        int sx = safeQuadrant % 2 == 0 ? 1 : -1, sz = safeQuadrant < 2 ? 1 : -1;
        Location refuge = origin.clone().add(sx * 17, 0, sz * 17);
        ring(refuge, 5.5, CYAN);
        ring(origin, 8, CYAN);
        scene.guillotine(safeQuadrant, Math.clamp((elapsed - deadline + 900) / 900D, 0, 1), lowered);
        for (int q = 0; q < 4; q++) if (q != safeQuadrant)
            ring(origin.clone().add(q % 2 == 0 ? 17 : -17, 0, q < 2 ? 17 : -17), 6, elapsed < deadline ? GOLD : RED);
        if (elapsed >= deadline && !guillotineHit) {
            guillotineHit = true;
            sound(Sound.BLOCK_ANVIL_LAND, .75F, .55F);
            for (Player player : fighters) if (horizontal(player.getLocation(), refuge) > 5.5 * 5.5
                    && horizontal(player.getLocation(), origin) > 8 * 8)
                hit(player, settings.damage() * 1.7, time);
        }
    }

    private boolean grounded(Player player) { return player.getY() <= floor().getY() + .55; }
    private Location floor() { return center.clone().add(0, lowered ? -FoundryGeometry.DROP : 0, 0); }
    private void cancelAttack() {
        if (bridgeRetracted && scene != null) scene.drawBridge(bridge, false);
        if (attack == Attack.COUNTERWEIGHTS && staggerUntil == 0 && bossId != null
                && plugin.getServer().getEntity(bossId) instanceof LivingEntity actor) actor.setAI(true);
        bridgeRetracted = false; attack = null; brands.clear(); tethers.clear(); counterweightTrial = null;
        if (scene != null) { scene.hideHammers(); scene.hideCombatChains(); scene.hideCounterweights(); }
    }
    private void clearStagger() {
        if (staggerUntil > 0 && bossId != null && plugin.getServer().getEntity(bossId) instanceof LivingEntity actor) {
            actor.setAI(true); actor.setGlowing(stage == Stage.FINAL);
        }
        staggerUntil = 0;
    }
    private void hit(Player player, double damage, long now) {
        if (cleaned || !alive(player) || now - hitTimes.getOrDefault(player.getUniqueId(), Long.MIN_VALUE / 2) < 650) return;
        hitTimes.put(player.getUniqueId(), now);
        player.damage(damage, boss());
    }
    private void relocateAll(boolean lower) {
        List<Player> players = participants();
        for (int i = 0; i < players.size(); i++) teleport(players.get(i), center.clone().add((i - (players.size() - 1) / 2D) * 2,
                lower ? -FoundryGeometry.DROP : 0, lower ? 4 : 5));
    }
    private void teleport(Player player, Location destination) {
        Point point = new Point(destination.getBlockX(), destination.getBlockY(), destination.getBlockZ());
        permits.authorize(player.getUniqueId(), Set.of(new TeleportPermitService.Destination(destination.getWorld().getName(), point)),
                clock.instant().plusSeconds(2));
        if (!player.teleport(destination)) { permits.revoke(player.getUniqueId()); throw new IllegalStateException("foundry safety teleport failed"); }
        player.setFallDistance(0); player.setVelocity(new Vector());
    }
    private void ring(Location at, double radius, Particle.DustOptions dust) {
        for (int i = 0; i < 32; i++) {
            double angle = i * Math.PI / 16;
            particle(at.clone().add(Math.cos(angle) * radius, .15, Math.sin(angle) * radius), dust);
        }
    }
    private void line(Location at, double angle, Particle.DustOptions dust) {
        for (int r = -31; r <= 31; r += 2) particle(at.clone().add(Math.cos(angle) * r, .2, Math.sin(angle) * r), dust);
    }
    private void particle(Location at, Particle.DustOptions dust) {
        for (Player player : audience) player.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, dust);
    }
    private void music(long time) {
        if (time - lastMusic < (stage == Stage.FINAL ? 330 : stage == Stage.RIVEN ? 450 : 750)) return;
        lastMusic = time;
        int[] notes = {0, 7, 3, 10, 6, 3, 7, 1};
        sound(Sound.BLOCK_NOTE_BLOCK_CHIME, .2F, (float) Math.pow(2, (notes[beat++ % notes.length] - 6) / 12D));
        sound(Sound.BLOCK_NOTE_BLOCK_BASEDRUM, .18F, .5F);
    }
    private void sound(Sound sound, float volume, float pitch) { audience.forEach(p -> p.playSound(p.getLocation(), sound, volume, pitch)); }
    private void notice(String text) { audience.forEach(p -> DungeonMessages.send(p, text)); }
    private void title(String text, String subtitle) {
        Title title = Title.title(MiniMessageUtils.miniMessage(text), MiniMessageUtils.miniMessage(subtitle),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2200), Duration.ofMillis(500)));
        audience.forEach(p -> p.showTitle(title));
    }
    private static double horizontal(Location a, Location b) { return Math.pow(a.getX() - b.getX(), 2) + Math.pow(a.getZ() - b.getZ(), 2); }
    private static Player nearest(List<Player> players, Location at) {
        return players.stream().min(java.util.Comparator.comparingDouble(p -> horizontal(p.getLocation(), at))).orElse(null);
    }
    @Override public double healthFraction() {
        return MythicBukkit.inst().getMobManager().getActiveMob(bossId)
                .map(active -> active.getEntity().getHealth() / active.getEntity().getMaxHealth()).orElse(0D);
    }

    private double allowedDamage() {
        if (stage == Stage.INTRO || stage == Stage.TRANSFORM || stage == Stage.DYING) return 0;
        return Double.MAX_VALUE;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        if (!event.getEntity().getUniqueId().equals(bossId)) return;
        if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent attack) {
            Entity source = attack.getDamager();
            if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) source = shooter;
            if (source instanceof Player player && !alive(player)) { event.setCancelled(true); return; }
        }
        double allowed = allowedDamage();
        if (allowed <= 0) event.setCancelled(true);
        else if (event.getDamage() > allowed) event.setDamage(allowed);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void customDamage(DamageCalculationEvent event) {
        if (!event.getTarget().getUniqueId().equals(bossId)) return;
        if (!alive(event.getPlayer()) || allowedDamage() <= 0) event.setCancelled(true);
        else event.setDamage(Math.min(event.getDamage(), allowedDamage()));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void target(EntityTargetLivingEntityEvent event) {
        if (event.getEntity().getUniqueId().equals(bossId)
                && (!(event.getTarget() instanceof Player player) || !alive(player))) event.setCancelled(true);
    }
    @Override public void cleanup() {
        if (cleaned) return;
        cleaned = true;
        try { if (scene != null) scene.close(); }
        finally {
            barViewers.forEach(p -> p.hideBossBar(bar)); barViewers.clear();
            if (registered) HandlerList.unregisterAll(this);
            registered = false; hitTimes.clear(); brands.clear(); tethers.clear(); counterweightTrial = null; audience = List.of();
        }
    }
}
