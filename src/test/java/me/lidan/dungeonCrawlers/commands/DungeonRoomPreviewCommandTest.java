package me.lidan.dungeonCrawlers.commands;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.*;
import me.lidan.dungeonCrawlers.config.registry.ConfigRegistryService;
import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner;
import me.lidan.dungeonCrawlers.core.protection.TeleportPermitService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.*;
import me.lidan.dungeonCrawlers.integration.ProgressBarService;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DungeonRoomPreviewCommandTest {
    private final ConfigRegistryService config = mock(ConfigRegistryService.class);
    private final GenerationService generation = mock(GenerationService.class);
    private final DungeonPhaseFiveCommand recovery = mock(DungeonPhaseFiveCommand.class);
    private final Server server = mock(Server.class);
    private final Player player = mock(Player.class);
    private final World world = mock(World.class);
    private final TeleportPermitService permits = new TeleportPermitService();
    private final UUID playerId = UUID.randomUUID(), instanceId = UUID.randomUUID();
    private final Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
    private final CompletableFuture<Chunk> chunk = new CompletableFuture<>();
    private final CompletableFuture<Void> saved = new CompletableFuture<>();
    private Consumer<GenerationService.InstanceSnapshot> generated;
    private DungeonRoomPreviewCommand command;

    @BeforeEach void setup() {
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        World origin = mock(World.class);
        when(origin.getName()).thenReturn("world");
        when(player.getWorld()).thenReturn(origin);
        when(world.getName()).thenReturn("dungeon_instances");
        when(server.getWorld("dungeon_instances")).thenReturn(world);
        when(world.getChunkAtAsync(any(Location.class))).thenReturn(chunk);
        when(config.snapshot()).thenReturn(new ConfigSnapshot(1, Map.of("floor_1", mock(FloorDefinition.class)),
                Map.of("crypt", mock(RoomDefinition.class)), Map.of(), Map.of(), java.util.Set.of(), "hash", Instant.EPOCH));
        when(generation.startPreview(any(), eq("crypt"))).thenReturn(new GenerationService.StartResult(true, instanceId, 0, "admitted"));
        when(generation.whenGenerated(eq(instanceId), any())).thenAnswer(invocation -> {
            generated = invocation.getArgument(1);
            return true;
        });
        when(generation.info(instanceId)).thenReturn(Optional.of(state(GenerationService.InstanceStatus.GENERATED)));
        var placement = mock(LayoutPlanner.Placement.class);
        when(placement.bounds()).thenReturn(new Bounds(new Point(100, 64, 100), new Point(104, 70, 104)));
        var plan = mock(LayoutPlanner.LayoutPlan.class);
        when(plan.placements()).thenReturn(List.of(placement));
        when(generation.layoutPlan(instanceId)).thenReturn(Optional.of(plan));
        when(recovery.savePreviewRecovery(player, instanceId)).thenReturn(saved);
        when(player.teleport(any(Location.class))).thenReturn(true);
        command = new DungeonRoomPreviewCommand(config, generation, recovery, server, "dungeon_instances",
                permits, clock, Duration.ofSeconds(5), Runnable::run, mock(ProgressBarService.class));
    }

    @Test void waitsForPasteChunkAndDurableReturnStateBeforeChangingModeOrTeleporting() {
        command.preview(player, "crypt");
        var request = ArgumentCaptor.forClass(GenerationService.StartRequest.class);
        verify(generation).startPreview(request.capture(), eq("crypt"));
        assertEquals(List.of(playerId), request.getValue().party().onlineMembers());
        assertFalse(request.getValue().progressionEnabled());
        verify(player, never()).teleport(any(Location.class));
        generated.accept(state(GenerationService.InstanceStatus.GENERATED));
        verify(recovery, never()).savePreviewRecovery(any(), any());
        chunk.complete(mock(Chunk.class));
        verify(recovery).savePreviewRecovery(player, instanceId);
        verify(player, never()).setGameMode(any());
        saved.complete(null);
        var destination = ArgumentCaptor.forClass(Location.class);
        verify(player).teleport(destination.capture());
        verify(player).setGameMode(GameMode.SPECTATOR);
        assertEquals(world, destination.getValue().getWorld());
        assertEquals(102.5, destination.getValue().getX());
        assertTrue(permits.consume(playerId, "dungeon_instances", new Point(102, 67, 102), Instant.EPOCH));
        command.stop(player);
        verify(recovery).cancelFromAdmin(instanceId);
        verify(generation).cancel(instanceId);
    }

    @Test void cancellationWhileChunkLoadsCannotLaterSaveOrTeleport() {
        command.preview(player, "crypt");
        generated.accept(state(GenerationService.InstanceStatus.GENERATED));
        command.stop(player);
        chunk.complete(mock(Chunk.class));
        verify(recovery, never()).savePreviewRecovery(any(), any());
        verify(player, never()).teleport(any(Location.class));
        verify(generation).cancel(instanceId);
    }

    @Test void quitWhileSnapshotAckWaitsCannotLaterTeleport() {
        command.preview(player, "crypt");
        generated.accept(state(GenerationService.InstanceStatus.GENERATED));
        chunk.complete(mock(Chunk.class));
        command.onQuit(new PlayerQuitEvent(player, (net.kyori.adventure.text.Component) null));
        saved.complete(null);
        verify(player, never()).teleport(any(Location.class));
        verify(recovery).cancelFromAdmin(instanceId);
    }

    @Test void snapshotFailureClosesPreviewWithoutChangingPlayerMode() {
        command.preview(player, "crypt");
        generated.accept(state(GenerationService.InstanceStatus.GENERATED));
        chunk.complete(mock(Chunk.class));
        saved.completeExceptionally(new IllegalStateException("disk failure"));
        verify(player, never()).setGameMode(any());
        verify(recovery).cancelFromAdmin(instanceId);
    }

    @Test void rejectedTeleportClosesAndRestoresPreview() {
        chunk.complete(mock(Chunk.class));
        saved.complete(null);
        when(player.teleport(any(Location.class))).thenReturn(false);
        command.preview(player, "crypt");
        generated.accept(state(GenerationService.InstanceStatus.GENERATED));
        verify(player).setGameMode(GameMode.SPECTATOR);
        verify(recovery).cancelFromAdmin(instanceId);
        verify(generation).cancel(instanceId);
        assertFalse(permits.consume(playerId, "dungeon_instances", new Point(102, 67, 102), Instant.EPOCH));
    }

    @Test void chunkFailureClosesPreviewAndCannotSavePlayerState() {
        command.preview(player, "crypt");
        generated.accept(state(GenerationService.InstanceStatus.GENERATED));
        chunk.completeExceptionally(new IllegalStateException("failed chunk"));
        verify(recovery, never()).savePreviewRecovery(any(), any());
        verify(player, never()).setGameMode(any());
        verify(generation).cancel(instanceId);
    }

    @Test void adminCleanupWhileReturnStateIsSavingCannotLaterTeleport() {
        command.preview(player, "crypt");
        generated.accept(state(GenerationService.InstanceStatus.GENERATED));
        chunk.complete(mock(Chunk.class));
        when(generation.info(instanceId)).thenReturn(Optional.of(state(GenerationService.InstanceStatus.CLEARING)));
        saved.complete(null);
        verify(player, never()).teleport(any(Location.class));
        verify(recovery).cancelFromAdmin(instanceId);
    }

    @Test void spectatorModeIsReappliedAfterDeferredWorldModeEnforcement() {
        var tasks = new java.util.ArrayDeque<Runnable>();
        command = new DungeonRoomPreviewCommand(config, generation, recovery, server, "dungeon_instances",
                permits, clock, Duration.ofSeconds(5), tasks::add, mock(ProgressBarService.class));
        when(player.teleport(any(Location.class))).thenAnswer(invocation -> {
            when(player.getWorld()).thenReturn(world);
            return true;
        });
        command.preview(player, "crypt");
        generated.accept(state(GenerationService.InstanceStatus.GENERATED));
        chunk.complete(mock(Chunk.class));
        saved.complete(null);
        tasks.remove().run();
        tasks.remove().run();
        verify(player).setGameMode(GameMode.SPECTATOR);
        player.setGameMode(GameMode.SURVIVAL);
        tasks.remove().run();
        verify(player, times(2)).setGameMode(GameMode.SPECTATOR);
        assertTrue(tasks.isEmpty());
    }

    @Test void unknownRoomActiveReservationAndDuplicatePreviewCannotTeleport() {
        command.preview(player, "missing");
        verify(generation, never()).startPreview(any(), any());
        when(generation.startPreview(any(), any())).thenReturn(GenerationService.StartResult.failure("player already active"));
        command.preview(player, "crypt");
        verify(generation, never()).whenGenerated(any(), any());
        when(generation.startPreview(any(), any())).thenReturn(new GenerationService.StartResult(true, instanceId, 0, "admitted"));
        command.preview(player, "crypt");
        command.preview(player, "crypt");
        verify(generation, times(2)).startPreview(any(), any());
        verify(player, never()).teleport(any(Location.class));
    }

    private GenerationService.InstanceSnapshot state(GenerationService.InstanceStatus status) {
        return new GenerationService.InstanceSnapshot(instanceId, 0, status, List.of(playerId), 0, "Room preview: crypt", "ready");
    }
}
