package me.lidan.dungeonCrawlers.core.difficulty;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.FloorDefinition;
import me.lidan.dungeonCrawlers.persistence.FileDurableRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DungeonProgressionServiceTest {
    @TempDir Path root;
    private FloorDefinition floor() {
        var floor = mock(FloorDefinition.class);
        when(floor.id()).thenReturn("floor_3"); when(floor.completionXp()).thenReturn(900D);
        when(floor.failureXpFactor()).thenReturn(.1); return floor;
    }
    @Test void completionReplacesStartedFailureAndOnlyUnlocksItsFloor() {
        UUID run = UUID.randomUUID(), player = UUID.randomUUID();
        try (var repository = new FileDurableRepository(root, 100, Runnable::run)) {
            var service = new DungeonProgressionService(repository);
            service.begin(run, player, floor(), Difficulty.HARD.defaults());
            var grants = new AtomicInteger();
            service.deliver(player, award -> { grants.incrementAndGet(); return CompletableFuture.completedFuture(true); },
                    Runnable::run, fail -> { throw new AssertionError(fail); });
            assertEquals(0, grants.get());
            var outcome = service.record(run, player, floor(), Difficulty.HARD.defaults(), true);
            assertEquals(1125, outcome.xp());
            assertTrue(service.isUnlocked(player, "floor_3", Difficulty.INSANE));
            assertFalse(service.isUnlocked(player, "floor_2", Difficulty.HARD));
            assertEquals(outcome, service.record(run, player, floor(), Difficulty.HARD.defaults(), false));
            service.deliver(player, award -> { grants.incrementAndGet(); return CompletableFuture.completedFuture(true); }, Runnable::run, fail -> { });
            var restored = new DungeonProgressionService(repository);
            restored.deliver(player, award -> { grants.incrementAndGet(); return CompletableFuture.completedFuture(true); }, Runnable::run, fail -> { });
            assertEquals(1, grants.get());
            assertTrue(restored.isUnlocked(player, "floor_3", Difficulty.INSANE));
        }
    }
    @Test void interruptedCombatRecoversFailureXpWithoutUnlock() {
        UUID player = UUID.randomUUID();
        try (var repository = new FileDurableRepository(root, 100, Runnable::run)) {
            var service = new DungeonProgressionService(repository);
            service.begin(UUID.randomUUID(), player, floor(), Difficulty.VOID.defaults());
            var restored = new DungeonProgressionService(repository);
            restored.deliver(player, award -> { assertEquals(315, award.xp()); return CompletableFuture.completedFuture(false); }, Runnable::run, fail -> { });
            assertFalse(restored.isUnlocked(player, "floor_3", Difficulty.HARD));
        }
    }
}
