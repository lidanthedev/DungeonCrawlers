package me.lidan.dungeonCrawlers.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BukkitRewardChestVisualsTest {
    private final World world = mock(World.class);
    private final List<BlockDisplay> displays = new ArrayList<>();
    private final List<ItemDisplay> chests = new ArrayList<>();
    private final Set<UUID> open = new HashSet<>();
    private final UUID instance = UUID.randomUUID();
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach void setUp() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createBlockData(any(Material.class))).thenAnswer(call -> {
            BlockData data = mock(BlockData.class);
            when(data.getMaterial()).thenReturn(call.getArgument(0));
            return data;
        });
        when(world.getMaxHeight()).thenReturn(320);
        when(world.spawn(any(Location.class), eq(BlockDisplay.class))).thenAnswer(call -> {
            BlockDisplay display = mock(BlockDisplay.class);
            when(display.isValid()).thenReturn(true);
            displays.add(display);
            return display;
        });
        when(world.spawn(any(Location.class), eq(ItemDisplay.class))).thenAnswer(call -> {
            ItemDisplay display = mock(ItemDisplay.class);
            when(display.isValid()).thenReturn(true);
            chests.add(display);
            return display;
        });
        open.add(instance);
    }

    @AfterEach void tearDown() { bukkit.close(); }

    @Test void beaconAndBeamReachWorldCeilingWithoutReadingOrChangingObstructions() {
        var visuals = new BukkitRewardChestVisuals(open::contains);
        visuals.show(instance, new Location(world, 10, 40, 12, 90, 75));
        assertEquals(3, displays.size());
        Material[] materials = {Material.BEACON, Material.YELLOW_STAINED_GLASS, Material.WHITE_STAINED_GLASS};
        for (int index = 0; index < displays.size(); index++) {
            var data = ArgumentCaptor.forClass(BlockData.class);
            verify(displays.get(index)).setBlock(data.capture());
            assertEquals(materials[index], data.getValue().getMaterial());
            verify(displays.get(index)).setPersistent(false);
            verify(displays.get(index)).setGravity(false);
        }
        var matrix = ArgumentCaptor.forClass(Matrix4f.class);
        verify(displays.get(1)).setTransformationMatrix(matrix.capture());
        assertEquals(320, 41 + matrix.getValue().m31() + matrix.getValue().m11(), .001);
        verify(world, times(3)).spawn(argThat(location -> location.getYaw() == 0 && location.getPitch() == 0),
                eq(BlockDisplay.class));
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        assertEquals(1, chests.size());
        var chestItem = ArgumentCaptor.forClass(ItemStack.class);
        verify(chests.getFirst()).setItemStack(chestItem.capture());
        assertEquals(Material.ENDER_CHEST, chestItem.getValue().getType());
        verify(chests.getFirst()).setGlowing(true);
        verify(chests.getFirst()).setGlowColorOverride(org.bukkit.Color.YELLOW);
        verify(chests.getFirst()).setPersistent(false);
        verify(world).spawn(argThat(location -> location.equals(new Location(world, 10.5, 40.5, 12.5))),
                eq(ItemDisplay.class));
        visuals.tick();
        verify(world).spawnParticle(eq(Particle.END_ROD), any(Location.class), eq(12), eq(.7), eq(.5), eq(.7), eq(.02));
        verify(world, times(16)).spawnParticle(eq(Particle.DUST), any(Location.class), eq(1),
                eq(0D), eq(0D), eq(0D), eq(0D), any(Particle.DustOptions.class));
        visuals.close();
    }

    @Test void replacementInvalidEntityInstanceClosureAndShutdownRemoveAllDisplays() {
        var visuals = new BukkitRewardChestVisuals(open::contains);
        Location chest = new Location(world, 0, 64, 0);
        visuals.show(instance, chest);
        visuals.show(instance, chest);
        when(displays.get(3).isValid()).thenReturn(false);
        visuals.tick();
        visuals.show(instance, chest);
        open.clear();
        visuals.tick();
        visuals.show(instance, chest);
        visuals.close();
        visuals.close();
        visuals.tick();
        for (BlockDisplay display : displays) verify(display, times(1)).remove();
        for (ItemDisplay display : chests) verify(display, times(1)).remove();
    }

    @Test void invalidGlowingChestRemovesTheWholeVisual() {
        var visuals = new BukkitRewardChestVisuals(open::contains);
        visuals.show(instance, new Location(world, 0, 64, 0));
        when(chests.getFirst().isValid()).thenReturn(false);
        visuals.tick();
        visuals.close();
        verify(chests.getFirst()).remove();
        for (BlockDisplay display : displays) verify(display).remove();
    }

    @Test void failedDisplayInitializationLeavesNoPartialBeacon() {
        when(world.spawn(any(Location.class), eq(BlockDisplay.class))).thenAnswer(call -> {
            BlockDisplay display = mock(BlockDisplay.class);
            displays.add(display);
            if (displays.size() == 2) doThrow(new IllegalStateException("initialization failed")).when(display)
                    .setBlock(any(BlockData.class));
            return display;
        });
        var visuals = new BukkitRewardChestVisuals(open::contains);
        assertThrows(IllegalStateException.class, () -> visuals.show(instance, new Location(world, 0, 64, 0)));
        visuals.close();
        for (BlockDisplay display : displays) verify(display, times(1)).remove();
    }
}
