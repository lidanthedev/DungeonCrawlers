package me.lidan.dungeonCrawlers.persistence;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface DurableRepository extends AutoCloseable {
    boolean reserveTerminalLane(UUID instanceId);

    void releaseTerminalLane(UUID instanceId);

    DurableSubmission submit(DurableWrite write);

    DurableSubmission submitTerminal(DurableWrite write);

    CompletableFuture<Optional<DurableRecord>> read(String namespace, String recordId);

    CompletableFuture<List<DurableRecord>> list(String namespace);

    CompletableFuture<Void> delete(String namespace, String recordId);

    RepositoryDiagnostics diagnostics();

    @Override
    void close();

    record RepositoryDiagnostics(int normalCapacity, int normalInFlight, int queuedOperations,
                                 int terminalReservations, int terminalInFlight, boolean closed) {
    }
}
