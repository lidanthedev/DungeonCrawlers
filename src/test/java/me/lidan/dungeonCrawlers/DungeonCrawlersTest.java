package me.lidan.dungeonCrawlers;

import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DungeonCrawlersTest {
    @Test
    void onlyRestoresRemovedPlayersWhoRemainInTheDungeonWorld() {
        Player inDungeon = playerIn("dungeon_instances");
        Player atDestination = playerIn("world");

        assertTrue(DungeonCrawlers.shouldRestoreRemovedPlayer(inDungeon, "dungeon_instances"));
        assertFalse(DungeonCrawlers.shouldRestoreRemovedPlayer(atDestination, "dungeon_instances"));
        assertTrue(DungeonCrawlers.shouldRestoreRemovedPlayer(null, "dungeon_instances"));
    }

    @Test
    void immediateWipeRequiresEveryParticipantToBeOffline() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        PlayerLifecycleService.InstanceSnapshot allOffline = new PlayerLifecycleService.InstanceSnapshot(
                UUID.randomUUID(), true, true, "no online active alive player remains", List.of(
                new PlayerLifecycleService.PlayerSnapshot(first, PlayerLifecycleService.PlayerState.GHOST, false,
                        Instant.EPOCH, null, 1),
                new PlayerLifecycleService.PlayerSnapshot(second, PlayerLifecycleService.PlayerState.GHOST, false,
                        Instant.EPOCH, null, 1)));
        PlayerLifecycleService.InstanceSnapshot oneOnline = new PlayerLifecycleService.InstanceSnapshot(
                allOffline.instanceId(), true, true, allOffline.detail(), List.of(
                allOffline.players().getFirst(),
                new PlayerLifecycleService.PlayerSnapshot(second, PlayerLifecycleService.PlayerState.GHOST, true,
                        Instant.EPOCH, null, 1)));

        assertTrue(DungeonCrawlers.allParticipantsOffline(allOffline));
        assertFalse(DungeonCrawlers.allParticipantsOffline(oneOnline));
    }

    private static Player playerIn(String worldName) {
        Player player = mock(Player.class);
        World world = mock(World.class);
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn(worldName);
        return player;
    }
}
