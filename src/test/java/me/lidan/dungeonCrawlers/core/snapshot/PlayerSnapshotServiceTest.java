package me.lidan.dungeonCrawlers.core.snapshot;

import me.lidan.dungeonCrawlers.persistence.DurableRecord;
import me.lidan.dungeonCrawlers.persistence.DurableRepository;
import me.lidan.dungeonCrawlers.persistence.DurableSubmission;
import me.lidan.dungeonCrawlers.persistence.DurableWrite;
import me.lidan.dungeonCrawlers.persistence.DurableWriteReceipt;
import me.lidan.dungeonCrawlers.persistence.model.PlayerRecoverySnapshot;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSnapshotServiceTest {
    @Test
    void writesReadsAndDeletesByPlayerId() {
        FakeRepository repository = new FakeRepository();
        PlayerSnapshotService service = new PlayerSnapshotService(repository);
        PlayerRecoverySnapshot snapshot = new PlayerRecoverySnapshot(UUID.randomUUID(), UUID.randomUUID(), "world",
                1, 2, 3, 0, 0, "SURVIVAL", 20, 20, 0, 0, 0, 300, 400, Instant.EPOCH);

        assertTrue(service.save(snapshot).accepted());
        assertEquals(snapshot, service.read(snapshot.playerId()).join().orElseThrow());
        service.delete(snapshot.playerId()).join();
        assertTrue(service.read(snapshot.playerId()).join().isEmpty());
        assertEquals("player-snapshots", repository.lastWrite.namespace());
    }

    @Test
    void retriesReuseCaptureMetadataAndNewCapturesAdvanceVersion() {
        FakeRepository repository = new FakeRepository();
        PlayerSnapshotService service = new PlayerSnapshotService(repository);
        UUID player = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        Instant capturedAt = Instant.parse("2026-01-01T00:00:00Z");
        PlayerRecoverySnapshot first = new PlayerRecoverySnapshot(player, instance, "world",
                1, 2, 3, 0, 0, "SURVIVAL", 20, 20, 0, 0, 0, 300, 400, capturedAt);

        service.save(first);
        String firstKey = repository.lastWrite.idempotencyKey();
        long firstVersion = repository.lastWrite.recordVersion();
        service.save(first);
        assertEquals(firstKey, repository.lastWrite.idempotencyKey());
        assertEquals(firstVersion, repository.lastWrite.recordVersion());

        PlayerRecoverySnapshot second = new PlayerRecoverySnapshot(player, instance, "world",
                4, 2, 3, 0, 0, "SURVIVAL", 20, 20, 0, 0, 0, 300, 400, capturedAt.plusSeconds(1));
        service.save(second);
        assertNotEquals(firstKey, repository.lastWrite.idempotencyKey());
        assertTrue(repository.lastWrite.recordVersion() > firstVersion);
    }

    @Test
    void newServiceUsesVersionAbovePersistedSnapshot() {
        FakeRepository repository = new FakeRepository();
        UUID player = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        Instant capturedAt = Instant.parse("2026-01-01T00:00:00Z");
        PlayerRecoverySnapshot first = new PlayerRecoverySnapshot(player, instance, "world",
                1, 2, 3, 0, 0, "SURVIVAL", 20, 20, 0, 0, 0, 300, 400, capturedAt);

        assertTrue(new PlayerSnapshotService(repository).save(first).accepted());
        PlayerRecoverySnapshot second = new PlayerRecoverySnapshot(player, instance, "world",
                4, 2, 3, 0, 0, "SURVIVAL", 20, 20, 0, 0, 0, 300, 400,
                capturedAt.plusMillis(1));

        var submission = new PlayerSnapshotService(repository).save(second);

        assertTrue(submission.accepted());
        assertTrue(submission.runtimeAck().join() != null);
    }

    @Test
    void aFreshServiceCanReadAnOfflineRecoverySnapshotAfterRestart() {
        FakeRepository repository = new FakeRepository();
        UUID player = UUID.randomUUID();
        PlayerRecoverySnapshot snapshot = new PlayerRecoverySnapshot(player, UUID.randomUUID(), "world",
                10, 64, -4, 90, 0, "SURVIVAL", 20, 20, 0, 0, 0, 300, 400, Instant.EPOCH);

        assertTrue(new PlayerSnapshotService(repository).save(snapshot).accepted());

        assertEquals(snapshot, new PlayerSnapshotService(repository).read(player).join().orElseThrow());
        assertTrue(new PlayerSnapshotService(repository).read(UUID.randomUUID()).join().isEmpty());
    }

    @Test
    void failedDeleteLeavesDurableRestoreMarkerForRecovery() {
        FakeRepository repository = new FakeRepository();
        PlayerSnapshotService service = new PlayerSnapshotService(repository);
        PlayerRecoverySnapshot snapshot = new PlayerRecoverySnapshot(UUID.randomUUID(), UUID.randomUUID(), "world",
                1, 2, 3, 0, 0, "SURVIVAL", 20, 20, 0, 0, 0, 300, 400, Instant.EPOCH);
        assertTrue(service.save(snapshot).accepted());
        repository.failDeletes = true;

        assertThrows(CompletionException.class, () -> service.deleteAfterRestore(snapshot).join());

        PlayerSnapshotService afterRestart = new PlayerSnapshotService(repository);
        assertTrue(afterRestart.wasRestored(snapshot).join());
        assertEquals(snapshot, afterRestart.read(snapshot.playerId()).join().orElseThrow());
    }

    @Test
    void successfulDeleteRemovesSnapshotAndRestoreMarker() {
        FakeRepository repository = new FakeRepository();
        PlayerSnapshotService service = new PlayerSnapshotService(repository);
        PlayerRecoverySnapshot snapshot = new PlayerRecoverySnapshot(UUID.randomUUID(), UUID.randomUUID(), "world",
                1, 2, 3, 0, 0, "SURVIVAL", 20, 20, 0, 0, 0, 300, 400, Instant.EPOCH);
        assertTrue(service.save(snapshot).accepted());

        service.deleteAfterRestore(snapshot).join();

        assertTrue(service.read(snapshot.playerId()).join().isEmpty());
        assertFalse(service.wasRestored(snapshot).join());
    }

    private static final class FakeRepository implements DurableRepository {
        private DurableWrite lastWrite;
        private final Map<Key, DurableRecord> records = new HashMap<>();
        private final Map<Key, Long> persistedVersions = new HashMap<>();
        private final Map<Key, String> persistedKeys = new HashMap<>();
        private boolean failDeletes;

        @Override public boolean reserveTerminalLane(UUID instanceId) { return true; }
        @Override public void releaseTerminalLane(UUID instanceId) { }
        @Override public DurableSubmission submit(DurableWrite write) {
            Key key = new Key(write.namespace(), write.recordId());
            long persistedVersion = persistedVersions.getOrDefault(key, -1L);
            String persistedKey = persistedKeys.get(key);
            if (persistedVersion >= write.recordVersion()
                    && !(write.recordVersion() == persistedVersion
                    && write.idempotencyKey().equals(persistedKey))) {
                var failure = new IllegalStateException("stale record version");
                return new DurableSubmission(true, CompletableFuture.failedFuture(failure),
                        CompletableFuture.failedFuture(failure), "accepted");
            }
            lastWrite = write;
            persistedVersions.put(key, write.recordVersion());
            persistedKeys.put(key, write.idempotencyKey());
            records.put(key,
                    new DurableRecord(write.namespace(), write.recordId(), write.payload(), "checksum",
                            Path.of("snapshot.bin")));
            var receipt = new DurableWriteReceipt(write.operationId(), write.idempotencyKey(), write.recordVersion(),
                    "checksum", Path.of("snapshot.bin"), Instant.EPOCH);
            return new DurableSubmission(true, CompletableFuture.completedFuture(receipt),
                    CompletableFuture.completedFuture(receipt), "accepted");
        }
        @Override public DurableSubmission submitTerminal(DurableWrite write) { return submit(write); }
        @Override public CompletableFuture<Optional<DurableRecord>> read(String namespace, String recordId) {
            return CompletableFuture.completedFuture(Optional.ofNullable(records.get(new Key(namespace, recordId))));
        }
        @Override public CompletableFuture<List<DurableRecord>> list(String namespace) { return CompletableFuture.completedFuture(List.of()); }
        @Override public CompletableFuture<Void> delete(String namespace, String recordId) {
            if (failDeletes) return CompletableFuture.failedFuture(new IllegalStateException("delete failed"));
            Key key = new Key(namespace, recordId);
            records.remove(key);
            persistedVersions.remove(key);
            persistedKeys.remove(key);
            return CompletableFuture.completedFuture(null);
        }
        @Override public RepositoryDiagnostics diagnostics() { return new RepositoryDiagnostics(1, 0, 0, 0, 0, false); }
        @Override public void close() { }

        private record Key(String namespace, String recordId) { }
    }
}
