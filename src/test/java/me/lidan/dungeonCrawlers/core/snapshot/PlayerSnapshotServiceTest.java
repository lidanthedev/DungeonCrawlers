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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static final class FakeRepository implements DurableRepository {
        private DurableWrite lastWrite;
        private byte[] payload;

        @Override public boolean reserveTerminalLane(UUID instanceId) { return true; }
        @Override public void releaseTerminalLane(UUID instanceId) { }
        @Override public DurableSubmission submit(DurableWrite write) {
            lastWrite = write; payload = write.payload();
            var receipt = new DurableWriteReceipt(write.operationId(), write.idempotencyKey(), write.recordVersion(),
                    "checksum", Path.of("snapshot.bin"), Instant.EPOCH);
            return new DurableSubmission(true, CompletableFuture.completedFuture(receipt),
                    CompletableFuture.completedFuture(receipt), "accepted");
        }
        @Override public DurableSubmission submitTerminal(DurableWrite write) { return submit(write); }
        @Override public CompletableFuture<Optional<DurableRecord>> read(String namespace, String recordId) {
            return CompletableFuture.completedFuture(payload == null ? Optional.empty()
                    : Optional.of(new DurableRecord(namespace, recordId, payload, "checksum", Path.of("snapshot.bin"))));
        }
        @Override public CompletableFuture<List<DurableRecord>> list(String namespace) { return CompletableFuture.completedFuture(List.of()); }
        @Override public CompletableFuture<Void> delete(String namespace, String recordId) { payload = null; return CompletableFuture.completedFuture(null); }
        @Override public RepositoryDiagnostics diagnostics() { return new RepositoryDiagnostics(1, 0, 0, 0, 0, false); }
        @Override public void close() { }
    }
}
