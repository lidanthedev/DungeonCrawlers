package me.lidan.dungeonCrawlers.integration;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.BlockDisplay;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BukkitFoundrySceneTest {
    @Test void fullTransformationRetractsRealFloorAndRestoresEveryOriginalExactlyOnce() {
        World world = mock(World.class);
        Map<String, Block> blocks = new HashMap<>();
        List<BlockState> saved = new ArrayList<>();
        List<BlockDisplay> displays = new ArrayList<>();
        when(world.getBlockAt(any(Location.class))).thenAnswer(call -> {
            Location at = call.getArgument(0);
            String key = at.getBlockX() + ":" + at.getBlockY() + ":" + at.getBlockZ();
            return blocks.computeIfAbsent(key, ignored -> {
                Block block = mock(Block.class); BlockState state = mock(BlockState.class);
                when(block.getState()).thenReturn(state); saved.add(state); return block;
            });
        });
        when(world.spawn(any(Location.class), eq(BlockDisplay.class), any(Consumer.class))).thenAnswer(call -> {
            BlockDisplay display = mock(BlockDisplay.class); displays.add(display); return display;
        });
        var scene = new BukkitFoundryScene(new Location(world, 40.5, 76, 40.5), UUID.randomUUID());
        scene.bossCrown(new Location(world, 40, 76, 40), 1, 1000);
        scene.beginTransformation();
        scene.transform(.2); assertEquals(0, scene.changedBlockCount());
        scene.transform(.5); assertTrue(scene.changedBlockCount() > 1000);
        scene.reveal();
        scene.drawBridge(0, true); scene.drawBridge(0, false);
        scene.guillotine(2, .5, true);
        for (int i = 0; i < 100; i++) scene.combatChain(i % 6,
                new Location(world, 35, 66, 40), new Location(world, 60, 68, 40), .5);
        assertEquals(190, scene.displayCount(), "combat chains reuse a fixed entity pool");
        scene.counterweights(true, false); scene.counterweights(true, true); scene.hideCounterweights();
        scene.hideCombatChains(); scene.death(.5);
        assertTrue(scene.changedBlockCount() > 3500, "the actual arena changes, not only the decoration");
        assertTrue(scene.displayCount() < BukkitFoundryScene.DISPLAY_CAP);
        assertTrue(scene.changedBlockCount() < BukkitFoundryScene.BLOCK_CAP);
        Block centerDeck = blocks.get("40:75:40");
        verify(centerDeck).setType(Material.AIR, false);
        Block bridge = blocks.get("50:65:40");
        verify(bridge).setType(Material.AIR, false);
        verify(bridge).setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
        scene.close(); scene.close();
        assertEquals(0, scene.displayCount()); assertEquals(0, scene.changedBlockCount());
        for (BlockState state : saved) verify(state).update(true, false);
        for (BlockDisplay display : displays) verify(display).remove();
        scene.change(0, -1, 0, Material.AIR);
        verify(centerDeck, times(1)).setType(Material.AIR, false);
    }
    @Test void mutationBoundaryAndDisplayCapAreEnforced() {
        World world = mock(World.class);
        when(world.spawn(any(Location.class), eq(BlockDisplay.class), any(Consumer.class))).thenAnswer(call -> mock(BlockDisplay.class));
        var scene = new BukkitFoundryScene(new Location(world, 0, 64, 0), UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> scene.change(39, 0, 0, Material.AIR));
        for (int i = 0; i < BukkitFoundryScene.DISPLAY_CAP; i++) scene.block(Material.ANVIL, 0, 0, 0, 1, 1, 1);
        assertThrows(IllegalStateException.class, () -> scene.block(Material.ANVIL, 0, 0, 0, 1, 1, 1));
        scene.close();
    }
}
