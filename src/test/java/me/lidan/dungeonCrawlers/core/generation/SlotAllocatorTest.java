package me.lidan.dungeonCrawlers.core.generation;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotAllocatorTest {
    @Test
    void clearAckIsRequiredBeforeReuse() {
        SlotAllocator allocator = new SlotAllocator(new SlotAllocator.Settings(1, 10_000, 500, 64, -64, 319));
        UUID first = UUID.randomUUID();
        var lease = allocator.allocate(first).orElseThrow();

        allocator.markPasting(lease.id(), first);
        allocator.markClearing(lease.id(), first);

        assertTrue(allocator.allocate(UUID.randomUUID()).isEmpty());
        assertThrows(IllegalStateException.class, () -> allocator.releaseUnmodified(lease.id(), first));

        allocator.acknowledgeClear(lease.id(), first);
        assertEquals(SlotAllocator.SlotState.ALLOCATED,
                allocator.allocate(UUID.randomUUID()).orElseThrow().state());
    }

    @Test
    void usesExplicitTenThousandBlockCellsAndMargins() {
        SlotAllocator allocator = new SlotAllocator(new SlotAllocator.Settings(4, 10_000, 500, 64, -64, 319));

        assertEquals(5_000, allocator.snapshot().get(0).origin().x());
        assertEquals(5_000, allocator.snapshot().get(0).origin().z());
        assertEquals(15_000, allocator.snapshot().get(1).origin().x());
        assertEquals(15_000, allocator.snapshot().get(2).origin().z());
        assertEquals(500, allocator.snapshot().get(0).usableBounds().minimum().x());
        assertEquals(9_499, allocator.snapshot().get(0).usableBounds().maximum().z());
    }

    @Test
    void rejectsGridOutsideMinecraftCoordinateLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> new SlotAllocator.Settings(10_000, Integer.MAX_VALUE, 1, 64, -64, 319));
    }
}
