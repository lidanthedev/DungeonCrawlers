package me.lidan.dungeonCrawlers.commands;

import me.lidan.dungeonCrawlers.core.door.DoorService;
import me.lidan.dungeonCrawlers.core.protection.TeleportPermitService;
import me.lidan.dungeonCrawlers.core.protection.WorldProtectionService;
import me.lidan.dungeonCrawlers.core.snapshot.PlayerSnapshotService;
import me.lidan.dungeonCrawlers.core.update.CentralUpdateService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Facing;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.integration.BukkitPlayerRecovery;
import me.lidan.dungeonCrawlers.integration.BukkitDoorBlockService;
import me.lidan.dungeonCrawlers.integration.SpawnProvider;
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
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.List;
import java.util.function.Supplier;

@Command("dungeon")
public final class DungeonPhaseFourCommand {
    private final CentralUpdateService updates;
    private final DoorService doors;
    private final WorldProtectionService protection;
    private final TeleportPermitService permits;
    private final PlayerSnapshotService snapshots;
    private final Server server;
    private final Clock clock;
    private final Executor mainThread;
    private final org.bukkit.World generationWorld;
    private final BukkitDoorBlockService doorBlocks = new BukkitDoorBlockService();
    private final Supplier<List<WorldProtectionService.InstanceRegion>> regions;

    public DungeonPhaseFourCommand(CentralUpdateService updates, DoorService doors,
                                   WorldProtectionService protection, TeleportPermitService permits,
                                   PlayerSnapshotService snapshots, Server server, Plugin plugin, Clock clock,
                                   String generationWorldName,
                                   Supplier<List<WorldProtectionService.InstanceRegion>> regions) {
        this.updates = java.util.Objects.requireNonNull(updates);
        this.doors = java.util.Objects.requireNonNull(doors);
        this.protection = java.util.Objects.requireNonNull(protection);
        this.permits = java.util.Objects.requireNonNull(permits);
        this.snapshots = java.util.Objects.requireNonNull(snapshots);
        this.server = java.util.Objects.requireNonNull(server);
        this.clock = java.util.Objects.requireNonNull(clock);
        this.mainThread = callback -> server.getScheduler().runTask(plugin, callback);
        this.generationWorld = server.getWorld(java.util.Objects.requireNonNull(generationWorldName));
        this.regions = java.util.Objects.requireNonNull(regions);
    }

    @Subcommand("tick advance-test")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void tickAdvance(CommandSender sender, long seconds) {
        if (seconds < 0 || seconds > 3_600) {
            sender.sendMessage("[FAIL] test tick seconds must be in 0..3600");
            return;
        }
        CentralUpdateService.TickReport report = updates.tick(clock.instant().plusSeconds(seconds));
        sender.sendMessage("[" + (report.successful() ? "PASS" : "FAIL") + "] tick=" + report.now()
                + " attempted=" + report.attempted() + " failures=" + report.failures());
    }

    @Subcommand("door register-test")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void registerDoor(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                              int x, int y, int z, String facing) {
        try {
            if (generationWorld == null) throw new IllegalStateException("generation world is not loaded");
            DoorService.DoorSnapshot door = doors.register(UUID.fromString(instanceId), new Point(x, y, z),
                    Facing.valueOf(facing.toUpperCase(Locale.ROOT)));
            doorBlocks.render(generationWorld, door);
            sender.sendMessage("[PASS] " + door + " blocks=" + door.blocks().size());
        } catch (RuntimeException exception) {
            sender.sendMessage("[FAIL] " + message(exception));
        }
    }

    @Subcommand("door info")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void doorInfo(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        try {
            doors.info(UUID.fromString(instanceId)).ifPresentOrElse(
                    door -> sender.sendMessage("[PASS] " + door + " blocks=" + door.blocks().size()),
                    () -> sender.sendMessage("[FAIL] unknown door " + instanceId));
        } catch (RuntimeException exception) { sender.sendMessage("[FAIL] " + message(exception)); }
    }

    @Subcommand("door set")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void doorSet(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                        String state) {
        try {
            UUID id = UUID.fromString(instanceId);
            if (generationWorld == null) throw new IllegalStateException("generation world is not loaded");
            DoorService.DoorSnapshot result = switch (state.toUpperCase(Locale.ROOT)) {
                case "LOCKED" -> doors.setLocked(id);
                case "READY" -> doors.setReady(id);
                default -> throw new IllegalArgumentException("state must be LOCKED or READY");
            };
            doorBlocks.render(generationWorld, result);
            sender.sendMessage("[PASS] " + result);
        } catch (RuntimeException exception) { sender.sendMessage("[FAIL] " + message(exception)); }
    }

    @Subcommand("door open")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void doorOpen(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        try {
            DoorService.OpenResult result = doors.open(UUID.fromString(instanceId),
                    () -> {
                        if (generationWorld == null) throw new IllegalStateException("generation world is not loaded");
                        doorBlocks.render(generationWorld, doors.info(UUID.fromString(instanceId)).orElseThrow());
                        sender.sendMessage("[PASS] door open callback invoked once");
                    });
            sender.sendMessage("[" + (result.opened() || result.alreadyOpen() ? "PASS" : "FAIL") + "] "
                    + result.detail() + " state=" + result.door().state());
        } catch (RuntimeException exception) { sender.sendMessage("[FAIL] " + message(exception)); }
    }

    @Subcommand("protection inspect")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void protectionInspect(CommandSender sender) {
        List<WorldProtectionService.InstanceRegion> active = regions.get();
        sender.sendMessage("[PASS] regions=" + active.size() + "; permits=" + permits.size()
                + "; policy=" + protection.getClass().getSimpleName());
        active.forEach(region -> sender.sendMessage("instance=" + region.instanceId() + " world=" + region.world()
                + " bounds=" + region.bounds() + " participants=" + region.participants()));
    }

    @Subcommand("player snapshot")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void playerSnapshot(Player player, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        try {
            var snapshot = BukkitPlayerRecovery.capture(player, UUID.fromString(instanceId), clock);
            var submission = snapshots.save(snapshot);
            if (!submission.accepted()) {
                player.sendMessage("[FAIL] snapshot rejected: " + submission.detail());
                return;
            }
            submission.runtimeAck().whenCompleteAsync((ignored, failure) -> {
                if (failure != null) player.sendMessage("[FAIL] snapshot ACK: " + message(failure));
                else DungeonGenerationCommand.suggest(player,
                        "<green>[PASS]</green> snapshot persisted for " + player.getUniqueId()
                                + " <gray>(click to restore)</gray>",
                        "/dungeon player restore-test");
            }, mainThread);
        } catch (RuntimeException exception) { player.sendMessage("[FAIL] " + message(exception)); }
    }

    @Subcommand("player restore-test")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void playerRestore(Player player) {
        snapshots.read(player.getUniqueId()).whenCompleteAsync((snapshot, failure) -> {
            if (failure != null) {
                player.sendMessage("[FAIL] snapshot read: " + message(failure));
                return;
            }
            if (snapshot.isEmpty()) {
                player.sendMessage("[FAIL] no recovery snapshot for " + player.getUniqueId());
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
            permits.authorize(player.getUniqueId(), destinations, clock.instant().plusSeconds(5));
            var result = BukkitPlayerRecovery.restore(player, saved, server, fallback);
            player.sendMessage("[" + (result.successful() ? "PASS" : "FAIL") + "] restore source="
                    + result.source() + " detail=" + result.detail());
        }, mainThread);
    }

    private static String message(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
