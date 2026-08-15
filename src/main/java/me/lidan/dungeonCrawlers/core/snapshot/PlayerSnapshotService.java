package me.lidan.dungeonCrawlers.core.snapshot;

import me.lidan.dungeonCrawlers.persistence.DurableRepository;
import me.lidan.dungeonCrawlers.persistence.DurableSubmission;
import me.lidan.dungeonCrawlers.persistence.DurableWrite;
import me.lidan.dungeonCrawlers.persistence.model.PlayerRecoverySnapshot;
import me.lidan.dungeonCrawlers.persistence.model.PlayerRecoverySnapshotCodec;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Async durable storage for one active recovery snapshot per player. */
public final class PlayerSnapshotService {
    public static final String NAMESPACE = "player-snapshots";
    private final DurableRepository repository;
    private final PlayerRecoverySnapshotCodec codec;

    public PlayerSnapshotService(DurableRepository repository) {
        this(repository, new PlayerRecoverySnapshotCodec());
    }

    public PlayerSnapshotService(DurableRepository repository, PlayerRecoverySnapshotCodec codec) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public DurableSubmission save(PlayerRecoverySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        DurableWrite write = new DurableWrite(UUID.randomUUID(), snapshot.instanceId(), NAMESPACE,
                snapshot.playerId().toString(), "player-snapshot-" + snapshot.playerId(), 1, codec.encode(snapshot));
        return repository.submit(write);
    }

    public CompletableFuture<Optional<PlayerRecoverySnapshot>> read(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return repository.read(NAMESPACE, playerId.toString()).thenApply(record ->
                record.map(value -> codec.decode(value.payload())));
    }

    public CompletableFuture<Void> delete(UUID playerId) {
        return repository.delete(NAMESPACE, Objects.requireNonNull(playerId, "playerId").toString());
    }
}
