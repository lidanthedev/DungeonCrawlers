package me.lidan.dungeonCrawlers.commands;

import me.lidan.dungeonCrawlers.config.registry.ConfigRegistryService;
import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.party.PartySnapshot;
import me.lidan.dungeonCrawlers.core.protection.TeleportPermitService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.integration.DungeonMessages;
import me.lidan.dungeonCrawlers.integration.ProgressBarService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

@CommandPermission("dungeoncrawlers.admin.authoring")
public final class DungeonRoomPreviewCommand implements Listener {
    private final ConfigRegistryService config;
    private final GenerationService generation;
    private final DungeonPhaseFiveCommand recovery;
    private final Server server;
    private final String worldName;
    private final TeleportPermitService permits;
    private final Clock clock;
    private final Duration permitDuration;
    private final Executor mainThread;
    private final ProgressBarService progress;
    private final Map<UUID, UUID> previews = new LinkedHashMap<>();

    public DungeonRoomPreviewCommand(ConfigRegistryService config, GenerationService generation,
                                     DungeonPhaseFiveCommand recovery, Server server, String worldName,
                                     TeleportPermitService permits, Clock clock, Duration permitDuration,
                                     Executor mainThread, ProgressBarService progress) {
        this.config = config;
        this.generation = generation;
        this.recovery = recovery;
        this.server = server;
        this.worldName = worldName;
        this.permits = permits;
        this.clock = clock;
        this.permitDuration = permitDuration;
        this.mainThread = mainThread;
        this.progress = progress;
    }

    @Command("dungeon room preview")
    public void preview(Player player, @SuggestWith(RoomIdSuggestionProvider.class) String roomId) {
        UUID playerId = player.getUniqueId();
        if (previews.containsKey(playerId)) {
            DungeonMessages.send(player, DungeonMessages.error("Close your current preview with /dungeon room preview stop first."));
            return;
        }
        if (player.getWorld().getName().equals(worldName)) {
            DungeonMessages.send(player, DungeonMessages.error("Leave the dungeon instance world before starting a preview."));
            return;
        }
        var snapshot = config.snapshot();
        if (!snapshot.rooms().containsKey(roomId)) {
            DungeonMessages.send(player, DungeonMessages.error("Unknown room: <white>" + roomId + "</white>."));
            return;
        }
        var floor = snapshot.floors().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue).findFirst().orElse(null);
        if (floor == null) {
            DungeonMessages.send(player, DungeonMessages.error("No dungeon floors are configured."));
            return;
        }
        var result = generation.startPreview(new GenerationService.StartRequest(snapshot, floor,
                new PartySnapshot(playerId, List.of(playerId), true), 0, 0), roomId);
        if (!result.accepted()) {
            DungeonMessages.send(player, DungeonMessages.error(result.detail()));
            return;
        }
        UUID instanceId = result.instanceId();
        previews.put(playerId, instanceId);
        progress.begin(instanceId, List.of(player), "Room preview", "Pasting " + roomId, 0.02);
        DungeonMessages.send(player, DungeonMessages.info("Pasting room <white>" + roomId + "</white> for preview."));
        if (!generation.whenGenerated(instanceId, state -> {
            if (!instanceId.equals(previews.get(playerId))) return;
            if (state.status() != GenerationService.InstanceStatus.GENERATED) {
                fail(player, instanceId, state.detail());
                return;
            }
            teleportWhenReady(player, instanceId, roomId);
        })) fail(player, instanceId, "preview generation is no longer available");
    }

    private void teleportWhenReady(Player player, UUID instanceId, String roomId) {
        try {
            var world = server.getWorld(worldName);
            if (world == null) throw new IllegalStateException("instance world is not loaded");
            var bounds = generation.layoutPlan(instanceId).orElseThrow().placements().getFirst().bounds();
            var min = bounds.minimum();
            var max = bounds.maximum();
            Location destination = new Location(world, (min.x() + max.x()) / 2.0 + 0.5,
                    (min.y() + max.y()) / 2.0 + 0.5, (min.z() + max.z()) / 2.0 + 0.5);
            world.getChunkAtAsync(destination).whenCompleteAsync((chunk, failure) -> {
                if (!current(player, instanceId)) return;
                if (failure != null) {
                    fail(player, instanceId, "preview chunk could not be loaded");
                    return;
                }
                try {
                    recovery.savePreviewRecovery(player, instanceId).whenCompleteAsync((ignored, saveFailure) -> {
                        if (!current(player, instanceId)) return;
                        if (saveFailure != null) {
                            fail(player, instanceId, "your return state could not be saved");
                            return;
                        }
                        try {
                            Point point = new Point(destination.getBlockX(), destination.getBlockY(), destination.getBlockZ());
                            permits.authorize(player.getUniqueId(), Set.of(new TeleportPermitService.Destination(worldName, point)),
                                    clock.instant().plus(permitDuration));
                            player.setGameMode(GameMode.SPECTATOR);
                            if (!player.teleport(destination)) throw new IllegalStateException("preview teleport was rejected");
                            mainThread.execute(() -> {
                                if (current(player, instanceId) && player.getWorld().equals(world)) {
                                    player.setGameMode(GameMode.SPECTATOR);
                                }
                            });
                            DungeonMessages.send(player, DungeonMessages.success("Previewing <white>" + roomId
                                    + "</white>. Use /dungeon room preview stop to return."));
                        } catch (RuntimeException exception) {
                            fail(player, instanceId, exception.getMessage());
                        }
                    }, mainThread);
                } catch (RuntimeException exception) {
                    fail(player, instanceId, exception.getMessage());
                }
            }, mainThread);
        } catch (RuntimeException exception) {
            fail(player, instanceId, exception.getMessage());
        }
    }

    private boolean current(Player player, UUID instanceId) {
        if (!instanceId.equals(previews.get(player.getUniqueId()))) return false;
        if (!player.isOnline() || generation.info(instanceId)
                .map(value -> value.status() != GenerationService.InstanceStatus.GENERATED).orElse(true)) {
            stop(player);
            return false;
        }
        return true;
    }

    @Command("dungeon room preview stop")
    public void stop(Player player) {
        UUID instanceId = previews.remove(player.getUniqueId());
        if (instanceId == null) return;
        progress.cancel(instanceId);
        permits.revoke(player.getUniqueId());
        recovery.cancelFromAdmin(instanceId);
        generation.cancel(instanceId);
    }

    private void fail(Player player, UUID instanceId, String detail) {
        if (!instanceId.equals(previews.get(player.getUniqueId()))) return;
        stop(player);
        if (player.isOnline()) DungeonMessages.send(player, DungeonMessages.error("Room preview failed: " + detail));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) { stop(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (event.getFrom().getName().equals(worldName)) stop(event.getPlayer());
    }
}
