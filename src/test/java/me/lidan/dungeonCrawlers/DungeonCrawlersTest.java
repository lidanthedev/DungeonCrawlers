package me.lidan.dungeonCrawlers;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

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

    private static Player playerIn(String worldName) {
        Player player = mock(Player.class);
        World world = mock(World.class);
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn(worldName);
        return player;
    }
}
