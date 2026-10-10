package me.lidan.dungeonCrawlers.integration;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BukkitRingmasterVisualsTest {
    private final World world = mock(World.class);
    private final List<Display> spawned = new ArrayList<>();
    private final Location at = new Location(world, 20, 64, 20);

    private BukkitRingmasterVisuals visuals() {
        when(world.spawn(any(Location.class), eq(BlockDisplay.class), any(Consumer.class))).thenAnswer(call -> {
            BlockDisplay display = mock(BlockDisplay.class); spawned.add(display); return display;
        });
        when(world.spawn(any(Location.class), eq(TextDisplay.class), any(Consumer.class))).thenAnswer(call -> {
            TextDisplay display = mock(TextDisplay.class); spawned.add(display); return display;
        });
        return new BukkitRingmasterVisuals(world, UUID.randomUUID());
    }

    @Test void transitionAndHazardModelsAreAllRemovedExactlyOnce() {
        var visuals = visuals();
        var boss = visuals.jester(at, true);
        var card = visuals.card(at, 3);
        visuals.scythe(at);
        boss.move(at.clone().add(0, 8, 0), 180, .1, 2);
        assertTrue(visuals.entityCount() > 30);
        card.close(); card.close();
        visuals.clear(); visuals.clear();
        assertEquals(0, visuals.entityCount());
        for (Display display : spawned) verify(display).remove();
    }

    @Test void displayBudgetIsBoundedAndReclaimedAfterCleanup() {
        var visuals = visuals(); var rig = visuals.rig(at);
        for (int i = 0; i < BukkitRingmasterVisuals.MAX_ENTITIES; i++) rig.block(Material.GOLD_BLOCK, 0, 0, 0, 1, 1, 1);
        assertThrows(IllegalStateException.class, () -> rig.block(Material.GOLD_BLOCK, 0, 0, 0, 1, 1, 1));
        visuals.clear();
        assertEquals(0, visuals.entityCount());
        var next = visuals.rig(at); next.block(Material.GOLD_BLOCK, 0, 0, 0, 1, 1, 1);
        assertEquals(1, visuals.entityCount());
        assertThrows(IllegalStateException.class, () -> rig.block(Material.GOLD_BLOCK, 0, 0, 0, 1, 1, 1));
    }
}
