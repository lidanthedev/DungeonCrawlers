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
    @Test void adminCompletionsPersistAddToRealCountsAndNeverAwardXp() {
        UUID player = UUID.randomUUID();
        try (var repository = new FileDurableRepository(root, 100, Runnable::run)) {
            var service = new DungeonProgressionService(repository);
            service.record(UUID.randomUUID(), player, floor(), Difficulty.HARDCORE.defaults(), true);
            assertEquals(3, service.addCompletions(player, "floor_3", Difficulty.HARDCORE, 2));
            assertEquals(6, service.addCompletions(player, "floor_3", Difficulty.HARDCORE, 3));
            service.addCompletions(player, "floor_3", Difficulty.NORMAL, 1);
        }
        try (var repository = new FileDurableRepository(root, 100, Runnable::run)) {
            var restored = new DungeonProgressionService(repository);
            assertEquals(6, restored.completions(player, "floor_3", Difficulty.HARDCORE));
            assertEquals(1, restored.completions(player, "floor_3", Difficulty.NORMAL));
            for (Difficulty tier : Difficulty.values()) assertTrue(restored.isUnlocked(player, "floor_3", tier));
            assertFalse(restored.isUnlocked(player, "floor_2", Difficulty.HARD));
            assertFalse(restored.isUnlocked(UUID.randomUUID(), "floor_3", Difficulty.HARD));
            var grants = new AtomicInteger();
            restored.deliver(player, award -> {
                grants.incrementAndGet();
                assertEquals(3600, award.xp());
                return CompletableFuture.completedFuture(true);
            }, Runnable::run, fail -> { throw new AssertionError(fail); });
            assertEquals(1, grants.get());
        }
    }
    @Test void rejectsInvalidAdminCreditsWithoutUnlocking() {
        UUID player = UUID.randomUUID();
        try (var repository = new FileDurableRepository(root, 100, Runnable::run)) {
            var service = new DungeonProgressionService(repository);
            for (int amount : new int[]{0, -1}) {
                assertThrows(IllegalArgumentException.class,
                        () -> service.addCompletions(player, "floor_3", Difficulty.HARDCORE, amount));
            }
            assertThrows(IllegalArgumentException.class,
                    () -> service.addCompletions(player, "../bad", Difficulty.HARDCORE, 1));
            assertFalse(service.isUnlocked(player, "floor_3", Difficulty.IMPOSSIBLE));
            assertEquals(0, service.completions(player, "floor_3", Difficulty.HARDCORE));
        }
    }
    @Test void failedAdminWriteDoesNotChangeCountsOrUnlocks() {
        var repository = mock(me.lidan.dungeonCrawlers.persistence.DurableRepository.class);
        when(repository.list(anyString())).thenReturn(CompletableFuture.completedFuture(java.util.List.of()));
        var service = new DungeonProgressionService(repository);
        UUID player = UUID.randomUUID();
        var failure = CompletableFuture.<me.lidan.dungeonCrawlers.persistence.DurableWriteReceipt>failedFuture(
                new IllegalStateException("disk failure"));
        when(repository.submit(any())).thenReturn(new me.lidan.dungeonCrawlers.persistence.DurableSubmission(
                false, failure, failure, "queue full"));
        assertThrows(IllegalStateException.class,
                () -> service.addCompletions(player, "floor_3", Difficulty.HARDCORE, 1));
        when(repository.submit(any())).thenReturn(new me.lidan.dungeonCrawlers.persistence.DurableSubmission(
                true, failure, failure, "accepted"));
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> service.addCompletions(player, "floor_3", Difficulty.HARDCORE, 1));
        assertFalse(service.isUnlocked(player, "floor_3", Difficulty.IMPOSSIBLE));
        assertEquals(0, service.completions(player, "floor_3", Difficulty.HARDCORE));
    }
}
