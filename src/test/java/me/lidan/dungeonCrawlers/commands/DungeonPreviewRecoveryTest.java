package me.lidan.dungeonCrawlers.commands;

import me.lidan.dungeonCrawlers.config.registry.ConfigRegistryService;
import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.protection.TeleportPermitService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.snapshot.PlayerSnapshotService;
import me.lidan.dungeonCrawlers.integration.DungeonActionBar;
import me.lidan.dungeonCrawlers.integration.PartyProvider;
import me.lidan.dungeonCrawlers.persistence.DurableSubmission;
import me.lidan.dungeonCrawlers.persistence.DurableWriteReceipt;
import me.lidan.dungeonCrawlers.persistence.model.PlayerRecoverySnapshot;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DungeonPreviewRecoveryTest {
    private final UUID playerId = UUID.randomUUID(), instanceId = UUID.randomUUID();
    private final Player player = mock(Player.class);
    private final Server server = mock(Server.class);
    private final PlayerSnapshotService snapshots = mock(PlayerSnapshotService.class);
    private final GenerationService generation = mock(GenerationService.class);
    private final RunPreparationService runs = mock(RunPreparationService.class);
    private final CompletableFuture<DurableWriteReceipt> ack = new CompletableFuture<>();
    private final Plugin plugin = mock(Plugin.class);
    private final List<Runnable> nextTick = new ArrayList<>();
    private final AtomicReference<GameMode> gameMode = new AtomicReference<>(GameMode.CREATIVE);
    private DungeonPhaseFiveCommand command;
    private Location original;

    @BeforeEach void setup() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        original = new Location(world, 1.25, 64.5, -2.75, 40, 5);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(original);
        when(player.getWorld()).thenReturn(world);
        when(player.getGameMode()).thenAnswer(ignored -> gameMode.get());
        doAnswer(invocation -> { gameMode.set(invocation.getArgument(0)); return null; }).when(player).setGameMode(any());
        when(player.getHealth()).thenReturn(18.0);
        when(player.getFoodLevel()).thenReturn(17);
        when(player.getMaximumAir()).thenReturn(300);
        when(player.getRemainingAir()).thenReturn(300);
        when(player.teleport(any(Location.class))).thenReturn(true);
        var health = mock(AttributeInstance.class);
        when(health.getValue()).thenReturn(20.0);
        when(player.getAttribute(Attribute.MAX_HEALTH)).thenReturn(health);
        when(server.getPlayer(playerId)).thenReturn(player);
        when(server.getWorld("world")).thenReturn(world);
        when(server.getWorlds()).thenReturn(List.of(world));
        when(world.getSpawnLocation()).thenReturn(original);
        var scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);
        when(plugin.isEnabled()).thenReturn(true);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), eq(3L))).thenAnswer(invocation -> {
            nextTick.add(invocation.getArgument(1));
            return mock(BukkitTask.class);
        });
        when(snapshots.save(any())).thenReturn(new DurableSubmission(true, ack, ack, "accepted"));
        when(snapshots.deleteAfterRestore(any())).thenReturn(CompletableFuture.completedFuture(null));
        command = new DungeonPhaseFiveCommand(mock(ConfigRegistryService.class), mock(PartyProvider.class),
                generation, runs, snapshots, new TeleportPermitService(), server, plugin,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), "dungeon_instances", mock(DungeonActionBar.class),
                new DungeonPhaseFiveCommand.PhaseServices(null, null, null, null, null));
    }

    @Test void adminCleanupRestoresOriginalLocationAndGameModeThroughDurableSnapshot() {
        var saved = command.savePreviewRecovery(player, instanceId);
        assertFalse(saved.isDone());
        ack.complete(mock(DurableWriteReceipt.class));
        assertTrue(saved.isDone());
        command.cancelFromAdmin(instanceId);
        assertRestored();
        verify(snapshots, never()).deleteAfterRestore(any());
        // Simulate Multiverse's one-tick destination game mode enforcement.
        gameMode.set(GameMode.SURVIVAL);
        nextTick.forEach(Runnable::run);
        assertEquals(GameMode.CREATIVE, gameMode.get());
        verify(generation).cancel(instanceId);
        verify(snapshots).deleteAfterRestore(any());
        verify(player, never()).removePotionEffect(any());
    }

    @Test void pluginDisableRestoresPreviewBeforeRepositoryCloses() {
        command.savePreviewRecovery(player, instanceId);
        ack.complete(mock(DurableWriteReceipt.class));
        when(plugin.isEnabled()).thenReturn(false);
        assertEquals(1, command.restoreOnlinePlayersForDisable());
        assertRestored();
        verify(snapshots, never()).deleteAfterRestore(any());
        assertTrue(nextTick.isEmpty());
        verify(player, never()).removePotionEffect(any());
    }

    @Test void disconnectBeforeModeSettlesRetainsDurableReturnStateForJoinRecovery() {
        command.savePreviewRecovery(player, instanceId);
        ack.complete(mock(DurableWriteReceipt.class));
        command.cancelFromAdmin(instanceId);
        when(player.isOnline()).thenReturn(false);
        nextTick.forEach(Runnable::run);
        verify(snapshots, never()).deleteAfterRestore(any());
    }

    @Test void rejectedSnapshotDoesNotCaptureAStateOrAlterPlayer() {
        when(snapshots.save(any())).thenReturn(new DurableSubmission(false, ack, ack, "full"));
        assertTrue(command.savePreviewRecovery(player, instanceId).isCompletedExceptionally());
        command.cancelFromAdmin(instanceId);
        verify(player, never()).teleport(any(Location.class));
        verify(snapshots, never()).deleteAfterRestore(any());
    }

    private void assertRestored() {
        var snapshot = ArgumentCaptor.forClass(PlayerRecoverySnapshot.class);
        verify(snapshots).save(snapshot.capture());
        assertEquals("CREATIVE", snapshot.getValue().gameMode());
        assertEquals(original.getX(), snapshot.getValue().x());
        var destination = ArgumentCaptor.forClass(Location.class);
        verify(player).teleport(destination.capture());
        assertEquals(original, destination.getValue());
        verify(player).setGameMode(GameMode.CREATIVE);
    }
}
