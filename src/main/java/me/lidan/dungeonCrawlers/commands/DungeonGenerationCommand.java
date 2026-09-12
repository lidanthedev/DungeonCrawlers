package me.lidan.dungeonCrawlers.commands;

import me.lidan.cavecrawlers.utils.MiniMessageUtils;
import me.lidan.dungeonCrawlers.config.registry.ConfigRegistryService;
import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.generation.SlotAllocator;
import me.lidan.dungeonCrawlers.core.combat.CombatRoomService;
import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.party.PartySnapshotPolicy;
import me.lidan.dungeonCrawlers.core.portal.PortalEncounterService;
import me.lidan.dungeonCrawlers.core.protection.TeleportPermitService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.score.ScoreService;
import me.lidan.dungeonCrawlers.core.secret.SecretDiscoveryService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.integration.PartyProvider;
import me.lidan.dungeonCrawlers.integration.DungeonMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

@Command("dungeon")
public final class DungeonGenerationCommand {
    public static final Duration TELEPORT_PERMIT_DURATION = Duration.ofSeconds(5);
    private final ConfigRegistryService configRegistry;
    private final PartyProvider parties;
    private final PartySnapshotPolicy partyPolicy = new PartySnapshotPolicy();
    private final GenerationService generation;
    private final Server server;
    private final String generationWorldName;
    private final TeleportPermitService teleportPermits;
    private final Clock clock;
    private final Consumer<UUID> preparationCancel;
    private final RunPreparationService runs;
    private final BooleanSupplier debugEnabled;
    private final PlayerLifecycleService lifecycle;
    private final SecretDiscoveryService secrets;
    private final Function<UUID, ScoreService.FinalScoreSnapshot> scores;
    private final CombatRoomService combat;
    private final PortalEncounterService portal;

    public DungeonGenerationCommand(ConfigRegistryService configRegistry, PartyProvider parties,
                                    GenerationService generation, Server server, String generationWorldName,
                                    TeleportPermitService teleportPermits, Clock clock,
                                    Consumer<UUID> preparationCancel, RunPreparationService runs) {
        this(configRegistry, parties, generation, server, generationWorldName, teleportPermits, clock,
                preparationCancel, runs, () -> false);
    }

    public DungeonGenerationCommand(ConfigRegistryService configRegistry, PartyProvider parties,
                                    GenerationService generation, Server server, String generationWorldName,
                                    TeleportPermitService teleportPermits, Clock clock,
                                    Consumer<UUID> preparationCancel, RunPreparationService runs,
                                    BooleanSupplier debugEnabled) {
        this(configRegistry, parties, generation, server, generationWorldName, teleportPermits, clock,
                preparationCancel, runs, debugEnabled, null, null, ignored -> null, null, null);
    }

    public DungeonGenerationCommand(ConfigRegistryService configRegistry, PartyProvider parties,
                                    GenerationService generation, Server server, String generationWorldName,
                                    TeleportPermitService teleportPermits, Clock clock,
                                    Consumer<UUID> preparationCancel, RunPreparationService runs,
                                    BooleanSupplier debugEnabled, PlayerLifecycleService lifecycle,
                                    SecretDiscoveryService secrets,
                                    Function<UUID, ScoreService.FinalScoreSnapshot> scores) {
        this(configRegistry, parties, generation, server, generationWorldName, teleportPermits, clock,
                preparationCancel, runs, debugEnabled, lifecycle, secrets, scores, null, null);
    }

    public DungeonGenerationCommand(ConfigRegistryService configRegistry, PartyProvider parties,
                                    GenerationService generation, Server server, String generationWorldName,
                                    TeleportPermitService teleportPermits, Clock clock,
                                    Consumer<UUID> preparationCancel, RunPreparationService runs,
                                    BooleanSupplier debugEnabled, PlayerLifecycleService lifecycle,
                                    SecretDiscoveryService secrets,
                                    Function<UUID, ScoreService.FinalScoreSnapshot> scores,
                                    CombatRoomService combat, PortalEncounterService portal) {
        this.configRegistry = configRegistry;
        this.parties = parties;
        this.generation = generation;
        this.server = server;
        this.generationWorldName = generationWorldName;
        this.teleportPermits = teleportPermits;
        this.clock = clock;
        this.preparationCancel = preparationCancel;
        this.runs = runs;
        this.debugEnabled = debugEnabled;
        this.lifecycle = lifecycle;
        this.secrets = secrets;
        this.scores = scores;
        this.combat = combat;
        this.portal = portal;
    }

    @Subcommand("instance generate-debug")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void generateDebug(Player player, @SuggestWith(FloorIdSuggestionProvider.class) String floorId, long seed) {
        if (!requireDebug(player)) return;
        generate(player, floorId, seed, 0);
    }

    @Subcommand("instance generate-debug-slow")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void generateDebugSlow(Player player, @SuggestWith(FloorIdSuggestionProvider.class) String floorId,
                                  long seed, long delayMillis) {
        if (!requireDebug(player)) return;
        generate(player, floorId, seed, delayMillis);
    }

    @Subcommand("instance cancel")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void cancel(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        UUID id = resolve(sender, instanceId);
        if (id == null) return;
        preparationCancel.accept(id);
        report(sender, generation.cancel(id));
    }

    @Subcommand("instance cleanup")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void cleanup(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        UUID id = resolve(sender, instanceId);
        if (id == null) return;
        preparationCancel.accept(id);
        report(sender, generation.cleanup(id));
    }

    @Subcommand("instance info")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void info(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        UUID id = resolve(sender, instanceId);
        if (id == null) return;
        generation.info(id).ifPresentOrElse(value -> {
            boolean teleportable = slotFor(id) != null && generation.playerSpawn(id).isPresent();
            sendInstance(sender, value, teleportable ? "/dungeon instance tp " + id : null);
        }, () -> DungeonMessages.send(sender, DungeonMessages.error("Unknown instance: <white>"
                + instanceId + "</white>")));
    }

    @Subcommand("instance tp")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void teleport(Player player, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        try {
            UUID id = DungeonInstanceResolver.require(player, instanceId, runs);
            generation.info(id)
                    .orElseThrow(() -> new IllegalArgumentException("unknown instance " + instanceId));
            SlotAllocator.SlotLease slot = slotFor(id);
            if (slot == null) throw new IllegalStateException("instance slot is no longer available");
            GenerationService.PlayerSpawn spawn = generation.playerSpawn(id)
                    .orElseThrow(() -> new IllegalStateException("instance has no generated EMERALD_BLOCK player spawn"));
            World world = server.getWorld(generationWorldName);
            if (world == null) throw new IllegalStateException("generation world is not loaded: " + generationWorldName);

            Point destinationPoint = new Point(spawn.point().x(), spawn.point().y() + 1, spawn.point().z());
            Instant permitExpiry = clock.instant().plus(TELEPORT_PERMIT_DURATION);
            teleportPermits.authorize(player.getUniqueId(), Set.of(
                    new TeleportPermitService.Destination(generationWorldName, destinationPoint)),
                    permitExpiry);
            Location destination = new Location(world, spawn.point().x() + 0.5,
                    spawn.point().y() + 1.0, spawn.point().z() + 0.5, spawn.yaw(), 0.0f);
            if (!player.teleport(destination)) {
                teleportPermits.revoke(player.getUniqueId());
                DungeonMessages.send(player, DungeonMessages.error("Teleport to instance <white>" + id
                        + "</white> was rejected."));
                return;
            }
            suggest(player, "<green>Teleported to instance " + id
                    + " <gray>(click for instance info)</gray>", "/dungeon instance info " + id);
        } catch (RuntimeException exception) {
            DungeonMessages.send(player, DungeonMessages.error(message(exception)));
        }
    }

    @Subcommand("instance list")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void instances(CommandSender sender) {
        var instances = generation.instances();
        DungeonMessages.send(sender, DungeonMessages.info("Instances: <white>" + instances.size() + "</white>"));
        instances.forEach(instance -> sendInstance(sender, instance,
                "/dungeon instance info " + instance.instanceId()));
    }

    @Subcommand("slots")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void slots(CommandSender sender) {
        var slots = generation.slots();
        long free = slots.stream().filter(slot -> slot.state() == me.lidan.dungeonCrawlers.core.generation
                .SlotAllocator.SlotState.FREE).count();
        DungeonMessages.send(sender, DungeonMessages.info("Slots free: <white>" + free + "/" + slots.size()
                + "</white>"));
        slots.forEach(slot -> DungeonMessages.send(sender, "<gray>Slot <white>" + slot.id()
                + "</white>  <white>" + slot.state().name() + "</white>  Owner: <white>"
                + (slot.instanceId() == null ? "none" : slot.instanceId()) + "</white>  Origin: <white>"
                + point(slot.origin()) + "</white>  Bounds: <white>" + bounds(slot.usableBounds())
                + "</white></gray>"));
    }

    @Subcommand("recovery status")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void recoveryStatus(CommandSender sender) {
        var status = generation.recoveryStatus();
        DungeonMessages.send(sender, "<gray>Recovery <white>" + (status.running() ? "RUNNING" : "IDLE")
                + "</white>  Starts: <white>" + (status.startsEnabled() ? "enabled" : "paused")
                + "</white>  Found: <white>" + status.discovered() + "</white>  Cleared: <white>"
                + status.cleared() + "</white>  Blockers: <white>"
                + (status.blockers().isEmpty() ? "none" : String.join(", ", status.blockers()))
                + "</white></gray>");
    }

    @Subcommand("recovery run")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void recoveryRun(CommandSender sender) {
        report(sender, generation.recover());
    }

    private void generate(Player player, String floorId, long seed, long delayMillis) {
        var snapshot = configRegistry.snapshot();
        var floor = snapshot.floors().get(floorId);
        if (floor == null) {
            DungeonMessages.send(player, DungeonMessages.error("Unknown floor: <white>" + floorId + "</white>"));
            return;
        }
        PartySnapshotPolicy.SnapshotResult party = partyPolicy.snapshot(player.getUniqueId(),
                parties.lookup(player.getUniqueId()), floor.limits().maxPartySize());
        if (!party.successful()) {
            DungeonMessages.send(player, DungeonMessages.error(party.error()));
            return;
        }
        GenerationService.StartResult result = generation.start(new GenerationService.StartRequest(snapshot, floor,
                party.snapshot(), seed, delayMillis));
        if (!result.accepted()) {
            DungeonMessages.send(player, DungeonMessages.error(playerError(result.detail())));
            return;
        }
        suggest(player, "<green>Preparing " + floor.displayName() + " for "
                + party.snapshot().onlineMembers().size()
                + (party.snapshot().onlineMembers().size() == 1 ? " player" : " players") + ".</green> "
                + "<gray>Click for instance details.</gray>",
                "/dungeon instance info " + result.instanceId());
    }

    private SlotAllocator.SlotLease slotFor(UUID instanceId) {
        return generation.slots().stream()
                .filter(slot -> instanceId.equals(slot.instanceId()))
                .findFirst()
                .orElse(null);
    }

    private void sendInstance(CommandSender sender, GenerationService.InstanceSnapshot instance, String command) {
        UUID id = instance.instanceId();
        var run = runs.info(id).orElse(null);
        var players = lifecycle == null ? null : lifecycle.info(id).orElse(null);
        var secretState = secrets == null ? null : secrets.info(id).orElse(null);
        var score = scores.apply(id);
        var combatState = combat == null ? null : combat.info(id).orElse(null);
        var bossState = portal == null ? null : portal.info(id).orElse(null);
        var slot = generation.slots().stream().filter(value -> value.id() == instance.slotId())
                .findFirst().orElse(null);
        var layout = generation.layoutPlan(id).orElse(null);
        int playerCount = run == null ? instance.participants().size() : run.participants().size();
        String state = instance.status() == GenerationService.InstanceStatus.DESTROYED || run == null
                ? instance.status().name().replace('_', ' ')
                : run.state().name().replace('_', ' ');
        Duration elapsed = run == null || run.startedAt() == null ? null
                : Duration.between(run.startedAt(), run.completedAt() == null ? clock.instant() : run.completedAt());
        String runtime = elapsed == null ? "--" : duration(elapsed);
        String count = playerCount + (playerCount == 1 ? " player" : " players");
        Component line = Component.text(id.toString(), NamedTextColor.AQUA)
                .append(Component.text("  " + instance.floorName() + "  " + state + "  " + count
                        + "  " + runtime, NamedTextColor.GRAY));
        List<String> details = new ArrayList<>();
        details.add("Instance #" + id);
        details.add("");
        details.add("Floor: " + instance.floorName());
        details.add("State: " + state);
        details.add("Seed: " + instance.seed());
        details.add("Players: " + playerCount);
        if (players != null) {
            long alive = players.players().stream().filter(value -> value.state()
                    == PlayerLifecycleService.PlayerState.ALIVE).count();
            long ghosts = players.players().stream().filter(value -> value.state()
                    == PlayerLifecycleService.PlayerState.GHOST).count();
            int deaths = players.players().stream().mapToInt(PlayerLifecycleService.PlayerSnapshot::deaths).sum();
            details.add("Alive: " + alive);
            details.add("Ghosts: " + ghosts);
            details.add("Deaths: " + deaths);
        }
        if (secretState != null) {
            long found = secretState.secrets().stream().filter(SecretDiscoveryService.SecretSnapshot::discovered).count();
            details.add("Secrets: " + found + "/" + secretState.secrets().size());
        }
        if (score != null) details.add("Score: " + score.total() + " ("
                + score.rank().name().replace('_', '+') + ")");
        details.add("Runtime: " + (elapsed == null ? "Not started" : durationWords(elapsed)));
        if (layout != null) details.add("Rooms: " + layout.placements().size());
        if (combatState != null) {
            var currentRoom = combatState.rooms().stream()
                    .filter(room -> room.state() == CombatRoomService.RoomState.ACTIVE)
                    .findFirst()
                    .or(() -> combatState.rooms().stream()
                            .filter(room -> room.state() == CombatRoomService.RoomState.CLEARED)
                            .reduce((first, second) -> second));
            details.add("Current room: " + currentRoom.map(room -> room.index() + " ("
                    + room.state().name() + ")").orElse("not started"));
        }
        if (bossState != null) details.add("Boss: " + bossState.status().name());
        if (run != null && run.runDeadline() != null) {
            details.add("Timeout seconds remaining: " + secondsRemaining(run.runDeadline()));
        }
        if (run != null && run.completionDeadline() != null) {
            details.add("Reward seconds remaining: " + secondsRemaining(run.completionDeadline()));
        }
        details.add("Slot: " + instance.slotId());
        if (slot != null) details.add("Origin: " + point(slot.origin()));
        if (instance.status() == GenerationService.InstanceStatus.CLEAR_FAILED) {
            details.add("Cleanup issue: " + instance.detail());
        }
        if (command != null) {
            line = line.clickEvent(ClickEvent.suggestCommand(command));
            details.add("");
            details.add("Click to suggest " + command);
        }
        DungeonMessages.send(sender, line.hoverEvent(HoverEvent.showText(
                Component.text(String.join("\n", details), NamedTextColor.GRAY))));
    }

    private static String duration(Duration elapsed) {
        long seconds = Math.max(0, elapsed.toSeconds());
        long hours = seconds / 3600;
        return hours == 0 ? "%d:%02d".formatted(seconds / 60, seconds % 60)
                : "%d:%02d:%02d".formatted(hours, seconds / 60 % 60, seconds % 60);
    }

    private static String durationWords(Duration elapsed) {
        long seconds = Math.max(0, elapsed.toSeconds());
        long hours = seconds / 3600;
        return (hours == 0 ? "" : hours + "h ") + (seconds / 60 % 60) + "m " + seconds % 60 + "s";
    }

    private long secondsRemaining(Instant deadline) {
        return Math.max(0, Duration.between(clock.instant(), deadline).toSeconds());
    }

    static void suggest(CommandSender sender, String message, String command) {
        Component component = DungeonMessages.prefix(MiniMessageUtils.miniMessage(message))
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(MiniMessageUtils.miniMessage("<gray>Suggest <white>" + command
                        + "</white></gray>")));
        sender.sendMessage(component);
    }

    private static void report(CommandSender sender, GenerationService.ActionResult result) {
        DungeonMessages.send(sender, result.successful()
                ? DungeonMessages.success(readableDetail(result.detail()))
                : DungeonMessages.error(readableDetail(result.detail())));
    }

    private UUID resolve(CommandSender sender, String value) {
        try {
            return DungeonInstanceResolver.require(sender, value, runs);
        } catch (IllegalArgumentException exception) {
            DungeonMessages.send(sender, DungeonMessages.error(exception.getMessage()));
            return null;
        }
    }

    private static String message(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    private boolean requireDebug(CommandSender sender) {
        if (debugEnabled.getAsBoolean()) return true;
        DungeonMessages.send(sender, DungeonMessages.warning(
                "This administrative test command is unavailable while debug mode is disabled."));
        return false;
    }

    private static String playerError(String detail) {
        String normalized = detail == null ? "" : detail.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("capacity") || normalized.contains("slot")) {
            return "The dungeon is temporarily full. Please try again shortly.";
        }
        if (normalized.contains("starts are blocked") || normalized.contains("recovery")) {
            return "Dungeon starts are temporarily paused while recovery finishes.";
        }
        return "The dungeon could not be started right now. Please try again later.";
    }

    private static String readableDetail(String detail) {
        if (detail == null || detail.isBlank()) return "No additional details.";
        return detail.replace("=", ": ").replace("; ", " · ");
    }

    private static String point(Point point) {
        return point.x() + ", " + point.y() + ", " + point.z();
    }

    private static String bounds(me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds bounds) {
        return "[" + point(bounds.minimum()) + "] to [" + point(bounds.maximum()) + "]";
    }
}
