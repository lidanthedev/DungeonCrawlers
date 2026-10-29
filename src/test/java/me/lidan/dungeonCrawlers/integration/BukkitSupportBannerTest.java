package me.lidan.dungeonCrawlers.integration;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BukkitSupportBannerTest {
    private final World world = mock(World.class);
    private final List<BlockDisplay> displays = new ArrayList<>();
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach void mockBlockDisplays() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createBlockData(any(Material.class))).thenAnswer(call -> {
            BlockData data = mock(BlockData.class);
            when(data.getMaterial()).thenReturn(call.getArgument(0));
            return data;
        });
        when(world.spawn(any(Location.class), eq(BlockDisplay.class))).thenAnswer(call -> {
            BlockDisplay display = mock(BlockDisplay.class);
            when(display.isValid()).thenReturn(true);
            displays.add(display);
            return display;
        });
    }

    @AfterEach void closeBukkitMock() {
        bukkit.close();
    }

    @Test void clothStaysUprightAtEveryFacingAndHasAPlantedBase() {
        for (float yaw : new float[]{0, 90, 137, -80}) {
            displays.clear();
            var banner = BukkitSupportBanner.spawn(new Location(world, 20, 64, 20, yaw, 75),
                    Material.WHITE_BANNER, Color.WHITE);
            assertEquals(17, displays.size());
            float minY = Float.POSITIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            for (BlockDisplay display : displays) {
                Matrix4f matrix = transform(display);
                Vector3f up = matrix.transformDirection(new Vector3f(0, 1, 0));
                assertEquals(0, up.x, .0001, "player pitch must never tip the banner");
                assertEquals(0, up.z, .0001);
                assertTrue(up.y > 0);
                for (int y : new int[]{0, 1}) {
                    float height = matrix.transformPosition(new Vector3f(0, y, 0)).y;
                    minY = Math.min(minY, height);
                    maxY = Math.max(maxY, height);
                }
                verify(display).setPersistent(false);
                verify(display).setInvulnerable(true);
            }
            assertEquals(0, minY, .0001);
            assertEquals(3.88, maxY, .0001);
            Vector3f clothDimensions = transform(displays.get(6)).getScale(new Vector3f());
            assertEquals(2.35, clothDimensions.y, .0001);
            assertTrue(clothDimensions.z < .1);
            banner.close();
            for (BlockDisplay display : displays) verify(display).remove();
        }
        verify(world, times(68)).spawn(argThat(location -> location.getYaw() == 0 && location.getPitch() == 0), eq(BlockDisplay.class));
        verify(world, never()).getBlockAt(any(Location.class));
    }

    @Test void standardsUseDistinctClothAndTwoSidedEmblems() {
        Material[] bannerMaterials = {Material.WHITE_BANNER, Material.RED_BANNER, Material.CYAN_BANNER};
        Material[] clothMaterials = {Material.WHITE_WOOL, Material.RED_WOOL, Material.CYAN_WOOL};
        for (int i = 0; i < bannerMaterials.length; i++) {
            displays.clear();
            var banner = BukkitSupportBanner.spawn(new Location(world, 0, 64, 0), bannerMaterials[i], Color.WHITE);
            for (int panel : new int[]{5, 6, 7, 11, 12}) {
                assertEquals(clothMaterials[i], blockData(displays.get(panel)).getMaterial());
            }
            Material metal = i == 2 ? Material.IRON_BLOCK : Material.GOLD_BLOCK;
            assertEquals(metal, blockData(displays.get(3)).getMaterial());
            assertTrue(transform(displays.get(13)).m32() < -.1, "front emblem sits above the cloth");
            assertTrue(transform(displays.get(15)).m32() > 0, "back emblem is independently visible");
            banner.close();
        }
    }

    @Test void gentleClothAnimationLeavesPoleFixedAndCleanupIsIdempotent() {
        var banner = BukkitSupportBanner.spawn(new Location(world, 0, 64, 0, 90, 0), Material.CYAN_BANNER, Color.AQUA);
        assertTrue(banner.isValid());
        banner.tick();
        verify(displays.get(2), times(1)).setTransformationMatrix(any());
        var animated = ArgumentCaptor.forClass(Matrix4f.class);
        verify(displays.get(6), times(2)).setTransformationMatrix(animated.capture());
        assertNotEquals(animated.getAllValues().get(0).m30(), animated.getAllValues().get(1).m30());
        Vector3f up = animated.getValue().transformDirection(new Vector3f(0, 1, 0));
        assertEquals(0, up.x, .0001); assertEquals(0, up.z, .0001);
        when(displays.get(6).isValid()).thenReturn(false);
        assertFalse(banner.isValid());
        banner.close(); banner.close(); banner.tick();
        assertFalse(banner.isValid());
        for (BlockDisplay display : displays) verify(display, times(1)).remove();
    }

    @Test void failedConfigurationRemovesEveryPartIncludingTheFailedEntity() {
        when(world.spawn(any(Location.class), eq(BlockDisplay.class))).thenAnswer(call -> {
            BlockDisplay display = mock(BlockDisplay.class);
            displays.add(display);
            if (displays.size() == 4) doThrow(new IllegalStateException("display failure"))
                    .when(display).setBlock(any(BlockData.class));
            return display;
        });
        assertThrows(IllegalStateException.class, () -> BukkitSupportBanner.spawn(
                new Location(world, 0, 64, 0), Material.RED_BANNER, Color.RED));
        assertEquals(4, displays.size());
        for (BlockDisplay display : displays) verify(display, times(1)).remove();
    }

    private Matrix4f transform(BlockDisplay display) {
        var matrix = ArgumentCaptor.forClass(Matrix4f.class);
        verify(display).setTransformationMatrix(matrix.capture());
        return matrix.getValue();
    }

    private BlockData blockData(BlockDisplay display) {
        var data = ArgumentCaptor.forClass(BlockData.class);
        verify(display).setBlock(data.capture());
        return data.getValue();
    }
}
