package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
