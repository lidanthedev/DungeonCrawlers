package me.lidan.dungeonCrawlers.core.chunk;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkTicketBudgetTest {
    @Test
    void acquisitionsAreAllOrNothingAndIdempotent() {
        ChunkTicketBudget budget = new ChunkTicketBudget(2, 3);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var a = new ChunkTicketBudget.ChunkKey(1, 1);
        var b = new ChunkTicketBudget.ChunkKey(2, 2);
        var c = new ChunkTicketBudget.ChunkKey(3, 3);

        assertTrue(budget.acquire(first, List.of(a, b)));
        assertTrue(budget.acquire(first, List.of(a)));
        assertFalse(budget.acquire(first, List.of(c)));
        assertEquals(2, budget.tickets(first).size());
        assertTrue(budget.acquire(second, List.of(c)));
        assertFalse(budget.acquire(UUID.randomUUID(), List.of(new ChunkTicketBudget.ChunkKey(4, 4))));
        assertEquals(3, budget.totalCount());
    }

    @Test
    void releaseAndReleaseAllReturnExactCounts() {
        ChunkTicketBudget budget = new ChunkTicketBudget(3, 3);
        UUID instance = UUID.randomUUID();
        var a = new ChunkTicketBudget.ChunkKey(1, 1);
        var b = new ChunkTicketBudget.ChunkKey(2, 2);
        assertTrue(budget.acquire(instance, List.of(a, b)));
        assertEquals(1, budget.release(instance, List.of(a, a)));
        assertEquals(1, budget.releaseAll(instance));
        assertEquals(0, budget.totalCount());
    }
}
