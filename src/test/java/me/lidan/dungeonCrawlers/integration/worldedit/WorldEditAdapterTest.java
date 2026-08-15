package me.lidan.dungeonCrawlers.integration.worldedit;

import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockType;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Rotation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorldEditAdapterTest {
    @Test
    void scansWithMinimumPointAsRelativeOriginAndEnforcesLimits() {
        Region region = mock(Region.class);
        Extent extent = mock(Extent.class);
        BaseBlock stone = mock(BaseBlock.class);
        BlockType stoneType = mock(BlockType.class);
        BlockVector3 minimum = BlockVector3.at(-7, 30, 11);
        BlockVector3 stonePosition = BlockVector3.at(-6, 31, 12);
        when(region.getWidth()).thenReturn(5);
        when(region.getHeight()).thenReturn(5);
        when(region.getLength()).thenReturn(5);
        when(region.getVolume()).thenReturn(125L);
        when(region.getMinimumPoint()).thenReturn(minimum);
        when(region.iterator()).thenReturn(List.of(stonePosition).iterator());
        when(extent.getFullBlock(stonePosition)).thenReturn(stone);
        when(stone.getBlockType()).thenReturn(stoneType);
        when(stoneType.id()).thenReturn("minecraft:gray_concrete_powder");
        when(stone.getStates()).thenReturn(Map.of());
        when(stone.getNbtReference()).thenReturn(null);

        var read = WorldEditAdapter.scan(region, extent, 5, 125);
        var tooNarrow = WorldEditAdapter.scan(region, extent, 4, 125);
        var tooLarge = WorldEditAdapter.scan(region, extent, 5, 124);

        assertTrue(read.successful(), read.detail());
        assertEquals("minecraft:gray_concrete_powder",
                read.selection().orElseThrow().block(new Point(1, 1, 1)).type());
        assertFalse(tooNarrow.successful());
        assertTrue(tooNarrow.detail().contains("dimension"));
        assertFalse(tooLarge.successful());
        assertTrue(tooLarge.detail().contains("volume"));
    }

    @Test
    void worldEditRotationMatchesPlannerRotationForAsymmetricNegativePoint() {
        Point point = new Point(-3, 7, 11);
        for (Rotation rotation : Rotation.values()) {
            double degrees = switch (rotation) {
                case NONE -> 0;
                case CLOCKWISE_90 -> -90;
                case CLOCKWISE_180 -> 180;
                case COUNTERCLOCKWISE_90 -> 90;
            };
            var transformed = new AffineTransform().rotateY(degrees)
                    .apply(Vector3.at(point.x(), point.y(), point.z())).toBlockPoint();
            assertEquals(rotation.apply(point), new Point(transformed.x(), transformed.y(), transformed.z()));
        }
    }
}
