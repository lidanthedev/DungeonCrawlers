package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.core.door.DoorService;
import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Facing;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.core.update.CentralUpdateService;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BukkitDungeonLifecycleListenerTest {
    @Test
    void leavingGenerationWorldRequestsDungeonLeave() {
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        World dungeonWorld = world("dungeon_instances");
        RunPreparationService runs = mock(RunPreparationService.class);
        Consumer<Player> leaveHandler = mock();
        when(player.getUniqueId()).thenReturn(playerId);
        when(runs.instanceFor(playerId)).thenReturn(Optional.of(UUID.randomUUID()));

        BukkitDungeonLifecycleListener listener = listener(runs, leaveHandler);
        listener.onChangedWorld(new PlayerChangedWorldEvent(player, dungeonWorld));

        verify(leaveHandler).accept(player);
    }

    @Test
    void enteringGenerationWorldDoesNotRequestDungeonLeave() {
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        World normalWorld = world("world");
        RunPreparationService runs = mock(RunPreparationService.class);
        Consumer<Player> leaveHandler = mock();
        when(player.getUniqueId()).thenReturn(playerId);
        when(runs.instanceFor(playerId)).thenReturn(Optional.of(UUID.randomUUID()));

        BukkitDungeonLifecycleListener listener = listener(runs, leaveHandler);
        listener.onChangedWorld(new PlayerChangedWorldEvent(player, normalWorld));

        verify(leaveHandler, never()).accept(player);
    }

    @Test
    void wipedReconnectDoesNotScheduleGhostPresentation() {
        UUID playerId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        Player player = mock(Player.class);
        RunPreparationService runs = mock(RunPreparationService.class);
        PlayerLifecycleService lifecycle = mock(PlayerLifecycleService.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(runs.instanceFor(playerId)).thenReturn(Optional.of(instanceId));
        when(lifecycle.reconnect(instanceId, playerId))
                .thenReturn(PlayerLifecycleService.TransitionResult.failure("instance is wiped"));

        BukkitDungeonLifecycleListener listener = new BukkitDungeonLifecycleListener(lifecycle, runs, mock(Plugin.class),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), "dungeon_instances", ignored -> { }, ignored -> { });
        listener.onJoin(new PlayerJoinEvent(player, "join"));

        verify(lifecycle, never()).player(instanceId, playerId);
    }

    @Test
    void disconnectingDuringCompletedRewardPeriodDoesNotCreateGhost() {
        UUID playerId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        Player player = mock(Player.class);
        RunPreparationService runs = mock(RunPreparationService.class);
        CentralUpdateService updates = new CentralUpdateService(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                ignored -> { });
        PlayerLifecycleService lifecycle = new PlayerLifecycleService(updates,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), ignored -> { });
        when(player.getUniqueId()).thenReturn(playerId);
        when(runs.instanceFor(playerId)).thenReturn(Optional.of(instanceId));
        when(runs.info(instanceId)).thenReturn(Optional.of(runSnapshot(instanceId, playerId,
                RunPreparationService.RunState.COMPLETED)));
        assertTrue(updates.register(instanceId, ignored -> { }));
        assertTrue(lifecycle.register(instanceId, List.of(playerId)).successful());
        assertTrue(lifecycle.start(instanceId).successful());

        BukkitDungeonLifecycleListener listener = new BukkitDungeonLifecycleListener(lifecycle, runs, mock(Plugin.class),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), "dungeon_instances", ignored -> { }, ignored -> { });
        listener.onQuit(new PlayerQuitEvent(player, "quit"));

        var state = lifecycle.player(instanceId, playerId).orElseThrow();
        assertEquals(PlayerLifecycleService.PlayerState.ALIVE, state.state());
        assertFalse(state.online());
        assertEquals(0, state.deaths());
    }

    private static RunPreparationService.RunSnapshot runSnapshot(UUID instanceId, UUID playerId,
                                                                   RunPreparationService.RunState state) {
        DoorService.DoorSnapshot door = new DoorService.DoorSnapshot(instanceId, new Point(0, 64, 0),
                Facing.NORTH, DoorService.DoorState.OPEN);
        return new RunPreparationService.RunSnapshot(instanceId, state, List.of(playerId), List.of("archer"),
                Map.of(playerId, "archer"), true, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, null,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(300), false, Instant.EPOCH, door);
    }

    private static BukkitDungeonLifecycleListener listener(RunPreparationService runs,
                                                            Consumer<Player> leaveHandler) {
        return new BukkitDungeonLifecycleListener(mock(PlayerLifecycleService.class), runs, mock(Plugin.class),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), "dungeon_instances", ignored -> { }, leaveHandler);
    }

    private static World world(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        return world;
    }
}
