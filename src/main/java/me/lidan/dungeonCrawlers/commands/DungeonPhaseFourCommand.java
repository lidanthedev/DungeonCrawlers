package me.lidan.dungeonCrawlers.commands;

import me.lidan.dungeonCrawlers.core.door.DoorService;
import me.lidan.dungeonCrawlers.core.protection.TeleportPermitService;
import me.lidan.dungeonCrawlers.core.protection.WorldProtectionService;
import me.lidan.dungeonCrawlers.core.snapshot.PlayerSnapshotService;
import me.lidan.dungeonCrawlers.core.update.CentralUpdateService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Facing;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.integration.BukkitPlayerRecovery;
import me.lidan.dungeonCrawlers.integration.BukkitDoorBlockService;
import me.lidan.dungeonCrawlers.integration.SpawnProvider;
import me.lidan.dungeonCrawlers.integration.DungeonMessages;
import me.lidan.dungeonCrawlers.integration.spawn.BukkitSpawnProvider;
import org.bukkit.Server;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;

@Command("dungeon")
public final class DungeonPhaseFourCommand {
    private static final long MAX_INSTANCE_ADVANCE_SECONDS = Duration.ofDays(1).toSeconds();

    private final CentralUpdateService updates;
    private final DoorService doors;
    private final WorldProtectionService protection;
    private final TeleportPermitService permits;
    private final PlayerSnapshotService snapshots;
    private final Server server;
    private final Clock clock;
    private final Executor mainThread;
    private final String generationWorldName;
    private final BukkitDoorBlockService doorBlocks = new BukkitDoorBlockService();
    private final Supplier<List<WorldProtectionService.InstanceRegion>> regions;
    private final RunPreparationService runs;
    private final BooleanSupplier debugEnabled;

    public DungeonPhaseFourCommand(CentralUpdateService updates, DoorService doors,
                                   WorldProtectionService protection, TeleportPermitService permits,
                                   PlayerSnapshotService snapshots, Server server, Plugin plugin, Clock clock,
                                   String generationWorldName,
                                   Supplier<List<WorldProtectionService.InstanceRegion>> regions,
                                   RunPreparationService runs) {
        this(updates, doors, protection, permits, snapshots, server, plugin, clock, generationWorldName,
                regions, runs, () -> false);
    }

    public DungeonPhaseFourCommand(CentralUpdateService updates, DoorService doors,
                                   WorldProtectionService protection, TeleportPermitService permits,
                                   PlayerSnapshotService snapshots, Server server, Plugin plugin, Clock clock,
                                   String generationWorldName,
                                   Supplier<List<WorldProtectionService.InstanceRegion>> regions,
                                   RunPreparationService runs, BooleanSupplier debugEnabled) {
        this.updates = java.util.Objects.requireNonNull(updates);
        this.doors = java.util.Objects.requireNonNull(doors);
        this.protection = java.util.Objects.requireNonNull(protection);
        this.permits = java.util.Objects.requireNonNull(permits);
        this.snapshots = java.util.Objects.requireNonNull(snapshots);
        this.server = java.util.Objects.requireNonNull(server);
        this.clock = java.util.Objects.requireNonNull(clock);
        this.mainThread = callback -> server.getScheduler().runTask(plugin, callback);
        this.generationWorldName = java.util.Objects.requireNonNull(generationWorldName);
        if (this.generationWorldName.isBlank()) throw new IllegalArgumentException("generation world name is blank");
        this.regions = java.util.Objects.requireNonNull(regions);
        this.runs = java.util.Objects.requireNonNull(runs);
        this.debugEnabled = java.util.Objects.requireNonNull(debugEnabled);
    }

    @Subcommand("instance advance")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void instanceAdvance(CommandSender sender,
                                @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                                String amount) {
        if (!requireDebug(sender)) return;
        try {
            UUID id = resolve(sender, instanceId);
            Duration duration = parseAdvanceTime(amount);
            if (updates.time(id).isEmpty()) {
                DungeonMessages.send(sender, DungeonMessages.error("Unknown active instance: <white>" + id + "</white>"));
                return;
            }
            CentralUpdateService.TickReport report = updates.advanceInstanceTime(id, duration);
            DungeonMessages.send(sender, report.successful()
                    ? DungeonMessages.success("Instance <white>" + id + "</white> advanced by <white>"
                    + duration.getSeconds() + "s</white>; attempted=<white>" + report.attempted() + "</white>.")
                    : DungeonMessages.error("Instance time advance failed: " + report.failures()));
        } catch (RuntimeException exception) {
            DungeonMessages.send(sender, DungeonMessages.error(message(exception)));
        }
    }

    @Subcommand("instance time")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void instanceTime(CommandSender sender,
                             @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        if (!requireDebug(sender)) return;
        try {
            UUID id = resolve(sender, instanceId);
            updates.time(id).ifPresentOrElse(
                    time -> DungeonMessages.send(sender, "<gray>Instance <white>" + id
                            + "</white>: real now=<white>" + time.realNow() + "</white>, dungeon now=<white>"
                            + time.schedulerNow() + "</white>, speed=<white>" + time.timeScale()
                            + "x</white>, offset=<white>" + time.instanceTimeOffset().getSeconds() + "s</white></gray>"),
                    () -> DungeonMessages.send(sender, DungeonMessages.error("Unknown active instance: <white>"
                            + id + "</white>")));
        } catch (RuntimeException exception) {
            DungeonMessages.send(sender, DungeonMessages.error(message(exception)));
        }
    }

    @Subcommand("tick speed-test")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void tickSpeed(CommandSender sender, long multiplier) {
        if (!requireDebug(sender)) return;
        if (multiplier < CentralUpdateService.MIN_TIME_SCALE
                || multiplier > CentralUpdateService.MAX_TIME_SCALE) {
            DungeonMessages.send(sender, DungeonMessages.error("Test tick speed must be in "
                    + CentralUpdateService.MIN_TIME_SCALE + ".." + CentralUpdateService.MAX_TIME_SCALE + "."));
            return;
        }
        updates.setTimeScale(multiplier);
        DungeonMessages.send(sender, DungeonMessages.success("Dungeon test time speed: <white>" + multiplier
                + "x</white> real time."));
    }

    @Subcommand("tick speed-reset-test")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void resetTickSpeed(CommandSender sender) {
        if (!requireDebug(sender)) return;
        updates.resetTimeScale();
        DungeonMessages.send(sender, DungeonMessages.success("Dungeon test time speed reset to <white>1x</white>."));
    }

    private static Duration parseAdvanceTime(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("time must be seconds, or use a s, m, or h suffix");
        }
        String value = input.trim().toLowerCase(Locale.ROOT);
        long unitSeconds = 1;
        String number = value;
        switch (value.charAt(value.length() - 1)) {
            case 's' -> { unitSeconds = 1; number = value.substring(0, value.length() - 1); }
            case 'm' -> { unitSeconds = 60; number = value.substring(0, value.length() - 1); }
            case 'h' -> { unitSeconds = 3_600; number = value.substring(0, value.length() - 1); }
            default -> { }
        }
        long amount;
        try {
            amount = Long.parseLong(number);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("time must be a whole number of seconds, minutes, or hours");
        }
        if (amount < 0) throw new IllegalArgumentException("time must not be negative");
        long seconds;
        try {
            seconds = Math.multiplyExact(amount, unitSeconds);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("time is too large");
        }
        if (seconds > MAX_INSTANCE_ADVANCE_SECONDS) {
            throw new IllegalArgumentException("time must be in 0..86400 seconds");
        }
        return Duration.ofSeconds(seconds);
    }

    @Subcommand("door register-test")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void registerDoor(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                              int x, int y, int z, String facing) {
        if (!requireDebug(sender)) return;
        try {
            DoorService.DoorSnapshot door = doors.register(resolve(sender, instanceId), new Point(x, y, z),
                    Facing.valueOf(facing.toUpperCase(Locale.ROOT)));
            doorBlocks.render(generationWorld(), door);
            sendDoor(sender, door);
        } catch (RuntimeException exception) {
            DungeonMessages.send(sender, DungeonMessages.error(message(exception)));
        }
    }

    @Subcommand("door info")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void doorInfo(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        if (!requireDebug(sender)) return;
        try {
            doors.info(resolve(sender, instanceId)).ifPresentOrElse(
                    door -> sendDoor(sender, door),
                    () -> DungeonMessages.send(sender, DungeonMessages.error("Unknown door: <white>"
                            + instanceId + "</white>")));
        } catch (RuntimeException exception) { DungeonMessages.send(sender, DungeonMessages.error(message(exception))); }
    }

    @Subcommand("door set")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void doorSet(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                        String state) {
        if (!requireDebug(sender)) return;
        try {
            UUID id = resolve(sender, instanceId);
            DoorService.DoorSnapshot result = switch (state.toUpperCase(Locale.ROOT)) {
                case "LOCKED" -> doors.setLocked(id);
                case "READY" -> doors.setReady(id);
                default -> throw new IllegalArgumentException("state must be LOCKED or READY");
            };
            doorBlocks.render(generationWorld(), result);
            sendDoor(sender, result);
        } catch (RuntimeException exception) { DungeonMessages.send(sender, DungeonMessages.error(message(exception))); }
    }

    @Subcommand("door open")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void doorOpen(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        if (!requireDebug(sender)) return;
        try {
            UUID id = resolve(sender, instanceId);
            DoorService.OpenResult result = doors.open(id,
                () -> {
                        doorBlocks.render(generationWorld(), doors.info(id).orElseThrow());
                        DungeonMessages.send(sender, DungeonMessages.success("Door open callback invoked once."));
                    });
            DungeonMessages.send(sender, result.opened() || result.alreadyOpen()
                    ? DungeonMessages.success(result.detail() + " state=<white>"
                    + result.door().state().name().toLowerCase(Locale.ROOT) + "</white>")
                    : DungeonMessages.error(result.detail()));
        } catch (RuntimeException exception) { DungeonMessages.send(sender, DungeonMessages.error(message(exception))); }
    }

    @Subcommand("protection inspect")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void protectionInspect(CommandSender sender) {
        if (!requireDebug(sender)) return;
        List<WorldProtectionService.InstanceRegion> active = regions.get();
        DungeonMessages.send(sender, "<gray>Protection regions=<white>" + active.size()
                + "</white>, teleport permits=<white>" + permits.size() + "</white>, policy=<white>"
                + protection.getClass().getSimpleName() + "</white></gray>");
        active.forEach(region -> DungeonMessages.send(sender, "<gray>instance=<white>" + region.instanceId()
                + "</white>, world=<white>" + region.world() + "</white>, bounds=<white>"
                + bounds(region.bounds()) + "</white>, participants=<white>" + region.participants().size()
                + "</white></gray>"));
    }

    @Subcommand("player snapshot")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void playerSnapshot(Player player, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        if (!requireDebug(player)) return;
        try {
            var snapshot = BukkitPlayerRecovery.capture(player, resolve(player, instanceId), clock);
            var submission = snapshots.save(snapshot);
            if (!submission.accepted()) {
                DungeonMessages.send(player, DungeonMessages.error("Snapshot rejected: " + submission.detail()));
                return;
            }
            submission.runtimeAck().whenCompleteAsync((ignored, failure) -> {
                if (failure != null) DungeonMessages.send(player, DungeonMessages.error("Snapshot acknowledgement failed: "
                        + message(failure)));
                else DungeonGenerationCommand.suggest(player,
                        "<green>Snapshot persisted for " + player.getUniqueId()
                                + " <gray>(click to restore)</gray>",
                        "/dungeon player restore-test");
            }, mainThread);
        } catch (RuntimeException exception) { DungeonMessages.send(player, DungeonMessages.error(message(exception))); }
    }

    @Subcommand("player restore-test")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void playerRestore(Player player) {
        if (!requireDebug(player)) return;
        snapshots.read(player.getUniqueId()).whenCompleteAsync((snapshot, failure) -> {
            if (failure != null) {
                DungeonMessages.send(player, DungeonMessages.error("Snapshot read failed: " + message(failure)));
                return;
            }
            if (snapshot.isEmpty()) {
                DungeonMessages.send(player, DungeonMessages.error("No recovery snapshot exists for this player."));
                return;
            }
            SpawnProvider fallback = new BukkitSpawnProvider(server, "");
            var saved = snapshot.orElseThrow();
            java.util.Set<TeleportPermitService.Destination> destinations = new java.util.LinkedHashSet<>();
            destinations.add(new TeleportPermitService.Destination(saved.world(),
                    new Point((int) Math.floor(saved.x()), (int) Math.floor(saved.y()), (int) Math.floor(saved.z()))));
            fallback.spawn().map(Location::clone).ifPresent(location -> destinations.add(
                    new TeleportPermitService.Destination(location.getWorld().getName(),
                            new Point(location.getBlockX(), location.getBlockY(), location.getBlockZ()))));
            permits.authorize(player.getUniqueId(), destinations,
                    clock.instant().plus(DungeonGenerationCommand.TELEPORT_PERMIT_DURATION));
            var result = BukkitPlayerRecovery.restore(player, saved, server, fallback);
            DungeonMessages.send(player, result.successful()
                    ? DungeonMessages.success("Player restored from <white>" + result.source()
                    + "</white>: " + result.detail())
                    : DungeonMessages.error("Player restore failed: " + result.detail()));
        }, mainThread);
    }

    private org.bukkit.World generationWorld() {
        org.bukkit.World world = server.getWorld(generationWorldName);
        if (world == null) throw new IllegalStateException("generation world is not loaded: " + generationWorldName);
        return world;
    }

    private static String message(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private UUID resolve(CommandSender sender, String value) {
        return DungeonInstanceResolver.require(sender, value, runs);
    }

    private boolean requireDebug(CommandSender sender) {
        if (debugEnabled.getAsBoolean()) return true;
        DungeonMessages.send(sender, DungeonMessages.warning(
                "This is a debug-only command and is disabled while config.yml debug is false."));
        return false;
    }

    private static void sendDoor(CommandSender sender, DoorService.DoorSnapshot door) {
        DungeonMessages.send(sender, DungeonMessages.success("Door <white>" + door.instanceId()
                + "</white>: state=<white>" + door.state().name().toLowerCase(Locale.ROOT)
                + "</white>, center=<white>" + point(door.center()) + "</white>, facing=<white>"
                + door.outward().name().toLowerCase(Locale.ROOT) + "</white>, blocks=<white>"
                + door.blocks().size() + "</white>."));
    }

    private static String point(Point point) {
        return point.x() + ", " + point.y() + ", " + point.z();
    }

    private static String bounds(me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds bounds) {
        return "[" + point(bounds.minimum()) + "] to [" + point(bounds.maximum()) + "]";
    }
}
