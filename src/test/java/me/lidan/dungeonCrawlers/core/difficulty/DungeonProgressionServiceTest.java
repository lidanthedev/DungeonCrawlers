package me.lidan.dungeonCrawlers.core.difficulty;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.FloorDefinition;
import me.lidan.dungeonCrawlers.persistence.FileDurableRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DungeonProgressionServiceTest {
    @TempDir Path root;
    private final List<DungeonProgressionService.Outcome> awards = new ArrayList<>();
    private FloorDefinition floor() {
        var floor = mock(FloorDefinition.class);
        when(floor.id()).thenReturn("floor_3"); when(floor.completionXp()).thenReturn(900D);
        when(floor.failureXpFactor()).thenReturn(.1); return floor;
    }
    @Test void completionAwardsOnceAndOnlyUnlocksItsFloor() {
        UUID run = UUID.randomUUID(), player = UUID.randomUUID();
        try (var repository = new FileDurableRepository(root, 100, Runnable::run)) {
            var service = new DungeonProgressionService(repository);
            var outcome = service.record(run, player, floor(), Difficulty.HARD.defaults(), true);
            assertEquals(1125, outcome.xp());
            assertTrue(awards.isEmpty());
            service.deliver(UUID.randomUUID(), awards::add);
            assertTrue(awards.isEmpty());
            service.deliver(player, awards::add);
            service.deliver(player, awards::add);
            assertEquals(List.of(outcome), awards);
            assertEquals(2, outcome.schemaVersion());
            assertTrue(service.isUnlocked(player, "floor_3", Difficulty.INSANE));
            assertFalse(service.isUnlocked(player, "floor_2", Difficulty.HARD));
            assertEquals(outcome, service.record(run, player, floor(), Difficulty.HARD.defaults(), false));
            service.deliver(player, awards::add);
            assertEquals(1, awards.size());
            var restored = new DungeonProgressionService(repository);
            assertEquals(1, awards.size());
            assertTrue(restored.isUnlocked(player, "floor_3", Difficulty.INSANE));
            assertEquals(outcome, restored.record(run, player, floor(), Difficulty.HARD.defaults(), true));
            restored.deliver(player, awards::add);
            assertEquals(1, awards.size());
        }
    }
    @Test void failureAwardsScaledXpWithoutUnlockOrRestartReplay() {
        UUID player = UUID.randomUUID();
        try (var repository = new FileDurableRepository(root, 100, Runnable::run)) {
            var service = new DungeonProgressionService(repository);
            service.record(UUID.randomUUID(), player, floor(), Difficulty.VOID.defaults(), false);
            service.deliver(player, awards::add);
            assertEquals(315, awards.getFirst().xp());
            var restored = new DungeonProgressionService(repository);
            assertEquals(1, awards.size());
            assertFalse(restored.isUnlocked(player, "floor_3", Difficulty.HARD));
        }
    }
    @Test void legacyPendingAndDeliveredOutcomesRestoreUnlocksWithoutReplayingXp() {
        UUID player = UUID.randomUUID();
        try (var repository = new FileDurableRepository(root, 100, Runnable::run)) {
            for (boolean delivered : new boolean[]{false, true}) {
                UUID run = UUID.randomUUID();
                UUID id = UUID.nameUUIDFromBytes((run + ":" + player).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var json = new com.google.gson.JsonObject();
                json.addProperty("schemaVersion", 1);
                json.addProperty("id", id.toString());
                json.addProperty("instance", run.toString());
                json.addProperty("player", player.toString());
                json.addProperty("floor", "floor_3");
                json.addProperty("difficulty", "HARD");
                json.addProperty("successful", true);
                json.addProperty("xp", 1125);
                json.addProperty("delivered", delivered);
                json.addProperty("settled", true);
                repository.submit(new me.lidan.dungeonCrawlers.persistence.DurableWrite(UUID.randomUUID(), run,
                        "dungeon-outcomes", id.toString(), id + ":outcome", delivered ? 3 : 2,
                        json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))).receipt().join();
            }
            var restored = new DungeonProgressionService(repository);
            assertTrue(restored.isUnlocked(player, "floor_3", Difficulty.INSANE));
            assertEquals(2, restored.completions(player, "floor_3", Difficulty.HARD));
            restored.deliver(player, awards::add);
            assertTrue(awards.isEmpty());
        }
    }
    @Test void legacyInterruptedRunIsIgnoredWithoutFailureXpOrWrites() {
        UUID run = UUID.randomUUID(), player = UUID.randomUUID(), id = UUID.randomUUID();
        var repository = mock(me.lidan.dungeonCrawlers.persistence.DurableRepository.class);
        var json = new com.google.gson.JsonObject();
        json.addProperty("schemaVersion", 1);
        json.addProperty("settled", false);
        var record = new me.lidan.dungeonCrawlers.persistence.DurableRecord("dungeon-outcomes", id.toString(),
                json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), "unused", root.resolve("legacy.json"));
        when(repository.list("dungeon-outcomes")).thenReturn(CompletableFuture.completedFuture(List.of(record)));
        when(repository.list("dungeon-admin-completions")).thenReturn(CompletableFuture.completedFuture(List.of()));
        var restored = new DungeonProgressionService(repository);
        assertFalse(restored.isUnlocked(player, "floor_3", Difficulty.HARD));
        assertTrue(awards.isEmpty());
        verify(repository, never()).submit(any());
    }
    @Test void offlineAwardsAreMemoryOnlyAndNotRecoveredAfterRestart() {
        UUID player = UUID.randomUUID(), run = UUID.randomUUID();
        try (var repository = new FileDurableRepository(root, 100, Runnable::run)) {
            var service = new DungeonProgressionService(repository);
            service.record(run, player, floor(), Difficulty.NORMAL.defaults(), true);
            var restored = new DungeonProgressionService(repository);
            restored.deliver(player, awards::add);
            assertTrue(awards.isEmpty());
            service.deliver(player, awards::add);
            assertEquals(1, awards.size());
            assertEquals(900, awards.getFirst().xp());
        }
    }
    @Test void nativeXpCallbackCanFinalizeAnotherOutcomeWithoutLosingItsAward() {
        UUID player = UUID.randomUUID();
        try (var repository = new FileDurableRepository(root, 100, Runnable::run)) {
            var service = new DungeonProgressionService(repository);
            service.record(UUID.randomUUID(), player, floor(), Difficulty.NORMAL.defaults(), true);
            service.deliver(player, outcome -> {
                awards.add(outcome);
                service.record(UUID.randomUUID(), player, floor(), Difficulty.HARD.defaults(), true);
            });
            assertEquals(1, awards.size());
            service.deliver(player, awards::add);
            assertEquals(2, awards.size());
        }
    }
    @Test void adminCompletionsPersistAddToRealCountsAndNeverAwardXp() {
        UUID player = UUID.randomUUID();
        try (var repository = new FileDurableRepository(root, 100, Runnable::run)) {
            var service = new DungeonProgressionService(repository);
            service.record(UUID.randomUUID(), player, floor(), Difficulty.HARDCORE.defaults(), true);
            service.deliver(player, awards::add);
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
            restored.deliver(player, awards::add);
            assertEquals(1, awards.size());
            assertEquals(3600, awards.getFirst().xp());
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
