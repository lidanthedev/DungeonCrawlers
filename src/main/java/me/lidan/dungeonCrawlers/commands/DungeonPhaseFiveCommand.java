package me.lidan.dungeonCrawlers.commands;

import me.lidan.cavecrawlers.utils.MiniMessageUtils;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ClassDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ConfigSnapshot;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.FloorDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigRegistryService;
import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.combat.CombatRoomService;
import me.lidan.dungeonCrawlers.core.party.PartySnapshotPolicy;
import me.lidan.dungeonCrawlers.core.protection.TeleportPermitService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.snapshot.PlayerSnapshotService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.integration.BukkitDoorBlockService;
import me.lidan.dungeonCrawlers.integration.BukkitPlayerRecovery;
import me.lidan.dungeonCrawlers.integration.DungeonActionBar;
import me.lidan.dungeonCrawlers.integration.PartyProvider;
import me.lidan.dungeonCrawlers.integration.ProgressBarService;
import me.lidan.dungeonCrawlers.integration.SpawnProvider;
import me.lidan.dungeonCrawlers.integration.spawn.BukkitSpawnProvider;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Player-facing start, class selection, and preparation-door commands. */
@Command("dungeon")
public final class DungeonPhaseFiveCommand {
    private final ConfigRegistryService configRegistry;
    private final PartyProvider parties;
    private final PartySnapshotPolicy partyPolicy = new PartySnapshotPolicy();
    private final GenerationService generation;
    private final RunPreparationService runs;
    private final PlayerSnapshotService snapshots;
    private final TeleportPermitService permits;
    private final Server server;
    private final Clock clock;
    private final String generationWorldName;
    private final DungeonActionBar actionBar;
    private final CombatRoomService combat;
    private final ProgressBarService progressBars;
    private final Executor mainThread;
    private final BukkitDoorBlockService doorBlocks = new BukkitDoorBlockService();
    private final Map<UUID, Map<UUID, me.lidan.dungeonCrawlers.persistence.model.PlayerRecoverySnapshot>> captured
            = new LinkedHashMap<>();

    public DungeonPhaseFiveCommand(ConfigRegistryService configRegistry, PartyProvider parties,
                                   GenerationService generation, RunPreparationService runs,
                                   PlayerSnapshotService snapshots, TeleportPermitService permits,
                                   Server server, Plugin plugin, Clock clock, String generationWorldName,
                                   DungeonActionBar actionBar) {
        this(configRegistry, parties, generation, runs, snapshots, permits, server, plugin, clock,
                generationWorldName, actionBar, null);
    }

    public DungeonPhaseFiveCommand(ConfigRegistryService configRegistry, PartyProvider parties,
                                   GenerationService generation, RunPreparationService runs,
                                   PlayerSnapshotService snapshots, TeleportPermitService permits,
                                   Server server, Plugin plugin, Clock clock, String generationWorldName,
                                   DungeonActionBar actionBar, CombatRoomService combat) {
        this(configRegistry, parties, generation, runs, snapshots, permits, server, plugin, clock,
                generationWorldName, actionBar, combat, null);
    }

    public DungeonPhaseFiveCommand(ConfigRegistryService configRegistry, PartyProvider parties,
                                   GenerationService generation, RunPreparationService runs,
                                   PlayerSnapshotService snapshots, TeleportPermitService permits,
                                   Server server, Plugin plugin, Clock clock, String generationWorldName,
                                   DungeonActionBar actionBar, CombatRoomService combat,
                                   ProgressBarService progressBars) {
        this.configRegistry = Objects.requireNonNull(configRegistry, "configRegistry");
        this.parties = Objects.requireNonNull(parties, "parties");
        this.generation = Objects.requireNonNull(generation, "generation");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.permits = Objects.requireNonNull(permits, "permits");
        this.server = Objects.requireNonNull(server, "server");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.generationWorldName = Objects.requireNonNull(generationWorldName, "generationWorldName");
        this.actionBar = Objects.requireNonNull(actionBar, "actionBar");
        this.combat = combat;
        this.progressBars = progressBars;
        this.mainThread = callback -> server.getScheduler().runTask(plugin, callback);
    }

    @Subcommand("start")
    @CommandPermission("dungeoncrawlers.use")
    public void start(Player player, @SuggestWith(FloorIdSuggestionProvider.class) String floorId) {
        ConfigSnapshot config = configRegistry.snapshot();
        FloorDefinition floor = config.floors().get(floorId);
        if (floor == null) {
            player.sendMessage("[FAIL] unknown floor " + floorId);
            return;
        }
        PartySnapshotPolicy.SnapshotResult party = partyPolicy.snapshot(player.getUniqueId(),
                parties.lookup(player.getUniqueId()), floor.limits().maxPartySize());
        if (!party.successful()) {
            player.sendMessage("[FAIL] " + party.error());
            return;
        }
        GenerationService.StartResult result = generation.start(new GenerationService.StartRequest(
                config, floor, party.snapshot(), player.getWorld().getSeed(), 0));
        if (!result.accepted()) {
            player.sendMessage("[FAIL] " + result.detail());
            return;
        }
        if (progressBars != null) {
            List<Player> players = party.snapshot().onlineMembers().stream()
                    .map(server::getPlayer).filter(Objects::nonNull).toList();
            progressBars.begin(result.instanceId(), players, "Dungeon generation",
                    "planning dungeon", 0.02);
        }
        boolean waiting = generation.whenGenerated(result.instanceId(), snapshot -> {
            if (snapshot.status() != GenerationService.InstanceStatus.GENERATED) {
                if (progressBars != null) progressBars.fail(result.instanceId(), snapshot.detail());
                player.sendMessage("[FAIL] generation did not complete: " + snapshot.detail());
                return;
            }
            if (progressBars != null) progressBars.complete(result.instanceId(), "generation complete");
            preparePlayers(result.instanceId(), party.snapshot(), floor, config);
        });
        if (!waiting) {
            player.sendMessage("[FAIL] generation was no longer available");
            return;
        }
        DungeonGenerationCommand.suggest(player, "<green>[PASS]</green> start admitted instance="
                        + result.instanceId() + " <gray>(click for instance info)</gray>",
                "/dungeon instance info " + result.instanceId());
    }

    @Subcommand("class list")
    @CommandPermission("dungeoncrawlers.use")
    public void classList(Player player) {
        UUID instanceId = runs.instanceFor(player.getUniqueId()).orElse(null);
        if (instanceId == null) {
            player.sendMessage("[FAIL] you are not preparing or running a dungeon");
            return;
        }
        RunPreparationService.RunSnapshot snapshot = runs.info(instanceId).orElseThrow();
        player.sendMessage("[PASS] class selection instance=" + instanceId + " state=" + snapshot.state());
        snapshot.allowedClasses().forEach(classId -> {
            String selected = snapshot.selectedClasses().entrySet().stream()
                    .filter(entry -> entry.getValue().equals(classId)).map(entry -> entry.getKey().toString())
                    .findFirst().orElse("none");
            DungeonGenerationCommand.suggest(player, "<green>" + classId + "</green> selectedBy=" + selected
                            + " <gray>(click to select)</gray>", "/dungeon class select " + classId);
        });
    }

    @Subcommand("class select")
    @CommandPermission("dungeoncrawlers.use")
    public void classSelect(Player player,
                            @SuggestWith(ClassIdSuggestionProvider.class) String classId) {
        UUID instanceId = runs.instanceFor(player.getUniqueId()).orElse(null);
        if (instanceId == null) {
            player.sendMessage("[FAIL] you are not preparing or running a dungeon");
            return;
        }
        var result = runs.selectClass(instanceId, player.getUniqueId(), classId.toLowerCase(java.util.Locale.ROOT));
        if (!result.successful()) {
            player.sendMessage("[FAIL] " + result.detail());
            return;
        }
        render(result.door());
        player.sendMessage("[PASS] " + result.detail() + " class=" + classId);
        actionBar.show(player, MiniMessageUtils.miniMessage("<green>Class selected: <white>" + classId
                + "</white>. Choose a class, then open the start door.</green>"));
    }

    /** Called by the admin instance-cancel path before generation cleanup. */
    public void cancelFromAdmin(UUID instanceId) {
        if (runs.info(instanceId).isPresent()) abort(instanceId, "preparation cancelled");
        else if (combat != null) combat.cleanup(instanceId);
    }

    /** Called by the interaction listener when a player clicks a preparation door. */
    public void openDoorAt(Player player, Point point) {
        var door = runs.doorAt(point);
        if (door.isEmpty()) return;
        var result = runs.openDoor(door.orElseThrow().instanceId(), player.getUniqueId());
        if (result.successful()) {
            render(result.door());
            player.sendMessage("[PASS] " + result.detail() + " state=" + result.door().state());
            if (result.snapshot().state() == RunPreparationService.RunState.RUNNING) {
                captured.remove(result.snapshot().instanceId());
                actionBar.show(player, Component.text("Dungeon started — first room active"));
            }
        } else {
            player.sendMessage("[FAIL] " + result.detail());
        }
    }

    public java.util.Optional<ClassDefinition> selectedClass(UUID playerId) {
        return runs.selectedClass(playerId);
    }

    private void preparePlayers(UUID instanceId, me.lidan.dungeonCrawlers.core.party.PartySnapshot party,
                                FloorDefinition floor, ConfigSnapshot config) {
        try {
            GenerationService.StartDoor startDoor = generation.startDoor(instanceId)
                    .orElseThrow(() -> new IllegalStateException("generated START room has no exit"));
            if (combat != null) {
                GenerationService.CombatPlan combatPlan = generation.combatPlan(instanceId)
                        .orElseThrow(() -> new IllegalStateException("generated combat plan is unavailable"));
                var combatRegistration = combat.register(combatPlan);
                if (!combatRegistration.successful()) {
                    abort(instanceId, combatRegistration.detail());
                    return;
                }
            }
            var registration = runs.registerGenerated(instanceId, party, floor.allowedClasses(), config.classes(),
                    startDoor.center(), startDoor.outward());
            if (!registration.successful()) {
                abort(instanceId, registration.detail());
                return;
            }
            render(registration.snapshot().door());
            Map<UUID, me.lidan.dungeonCrawlers.persistence.model.PlayerRecoverySnapshot> saved = new LinkedHashMap<>();
            List<CompletableFuture<?>> acknowledgements = new java.util.ArrayList<>();
            for (UUID playerId : party.onlineMembers()) {
                Player player = server.getPlayer(playerId);
                if (player == null) throw new IllegalStateException("active member went offline before preparation");
                var snapshot = BukkitPlayerRecovery.capture(player, instanceId, clock);
                var submission = snapshots.save(snapshot);
                if (!submission.accepted()) throw new IllegalStateException("snapshot rejected: " + submission.detail());
                captured.computeIfAbsent(instanceId, ignored -> new LinkedHashMap<>()).put(playerId, snapshot);
                saved.put(playerId, snapshot);
                acknowledgements.add(submission.runtimeAck());
            }
            CompletableFuture.allOf(acknowledgements.toArray(CompletableFuture[]::new))
                    .whenCompleteAsync((ignored, failure) -> {
                        if (failure != null) abort(instanceId, "recovery snapshot ACK failed: " + message(failure));
                        else finishPreparation(instanceId, party);
                    }, mainThread);
        } catch (RuntimeException exception) {
            abort(instanceId, message(exception));
        }
    }

    private void finishPreparation(UUID instanceId, me.lidan.dungeonCrawlers.core.party.PartySnapshot party) {
        if (!runs.markSnapshotsReady(instanceId).successful()) {
            abort(instanceId, "preparation was no longer active");
            return;
        }
        GenerationService.PlayerSpawn spawn = generation.playerSpawn(instanceId)
                .orElse(null);
        if (spawn == null) {
            abort(instanceId, "generated player spawn is unavailable");
            return;
        }
        World world = generationWorld();
        for (UUID playerId : party.onlineMembers()) {
            Player player = server.getPlayer(playerId);
            if (player == null) {
                abort(instanceId, "active member went offline before teleport");
                return;
            }
            Point destinationPoint = new Point(spawn.point().x(), spawn.point().y() + 1, spawn.point().z());
            permits.authorize(playerId, Set.of(new TeleportPermitService.Destination(generationWorldName,
                    destinationPoint)), clock.instant().plus(DungeonGenerationCommand.TELEPORT_PERMIT_DURATION));
            Location destination = new Location(world, spawn.point().x() + 0.5, spawn.point().y() + 1.0,
                    spawn.point().z() + 0.5, spawn.yaw(), 0.0f);
            if (!player.teleport(destination)) {
                permits.revoke(playerId);
                abort(instanceId, "teleport rejected for " + playerId);
                return;
            }
            actionBar.show(player, Component.text("Choose your dungeon class, then open the coal door"));
        }
    }

    private void abort(UUID instanceId, String reason) {
        if (combat != null) combat.cleanup(instanceId);
        runs.cleanup(instanceId);
        generation.cancel(instanceId);
        Map<UUID, me.lidan.dungeonCrawlers.persistence.model.PlayerRecoverySnapshot> saved = captured.remove(instanceId);
        if (saved == null) return;
        SpawnProvider fallback = new BukkitSpawnProvider(server, "");
        saved.forEach((playerId, snapshot) -> {
            Player player = server.getPlayer(playerId);
            if (player != null) {
                Set<TeleportPermitService.Destination> destinations = new LinkedHashSet<>();
                destinations.add(new TeleportPermitService.Destination(snapshot.world(),
                        new Point((int) Math.floor(snapshot.x()), (int) Math.floor(snapshot.y()),
                                (int) Math.floor(snapshot.z()))));
                fallback.spawn().map(Location::clone).ifPresent(location -> destinations.add(
                        new TeleportPermitService.Destination(location.getWorld().getName(),
                                new Point(location.getBlockX(), location.getBlockY(), location.getBlockZ()))));
                permits.authorize(playerId, destinations,
                        clock.instant().plus(DungeonGenerationCommand.TELEPORT_PERMIT_DURATION));
                BukkitPlayerRecovery.restore(player, snapshot, server, fallback);
                player.sendMessage("[FAIL] " + reason + "; preparation cancelled and player restored");
            }
            snapshots.delete(playerId);
        });
    }

    private void render(me.lidan.dungeonCrawlers.core.door.DoorService.DoorSnapshot door) {
        World world = generationWorld();
        doorBlocks.render(world, door);
    }

    private World generationWorld() {
        World world = server.getWorld(generationWorldName);
        if (world == null) throw new IllegalStateException("generation world is not loaded: " + generationWorldName);
        return world;
    }

    private static String message(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
