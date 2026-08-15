package me.lidan.dungeonCrawlers.core.snapshot;

import me.lidan.dungeonCrawlers.persistence.DurableRepository;
import me.lidan.dungeonCrawlers.persistence.DurableSubmission;
import me.lidan.dungeonCrawlers.persistence.DurableWrite;
import me.lidan.dungeonCrawlers.persistence.model.PlayerRecoverySnapshot;
import me.lidan.dungeonCrawlers.persistence.model.PlayerRecoverySnapshotCodec;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Async durable storage for one active recovery snapshot per player. */
public final class PlayerSnapshotService {
    public static final String NAMESPACE = "player-snapshots";
    private final DurableRepository repository;
    private final PlayerRecoverySnapshotCodec codec;
    private final Map<CaptureKey, Long> captureVersions = new HashMap<>();
    private final Map<UUID, Long> latestVersions = new HashMap<>();

    public PlayerSnapshotService(DurableRepository repository) {
        this(repository, new PlayerRecoverySnapshotCodec());
    }

    public PlayerSnapshotService(DurableRepository repository, PlayerRecoverySnapshotCodec codec) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public synchronized DurableSubmission save(PlayerRecoverySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        byte[] payload = codec.encode(snapshot);
        String idempotencyKey = "player-snapshot-" + UUID.nameUUIDFromBytes(payload);
        CaptureKey capture = new CaptureKey(snapshot.playerId(), snapshot.instanceId(), snapshot.capturedAt(),
                idempotencyKey);
        long recordVersion = captureVersions.computeIfAbsent(capture, ignored ->
                nextVersion(snapshot.playerId(), snapshot.capturedAt()));
        DurableWrite write = new DurableWrite(UUID.randomUUID(), snapshot.instanceId(), NAMESPACE,
                snapshot.playerId().toString(), idempotencyKey, recordVersion, payload);
        return repository.submit(write);
    }

    private long nextVersion(UUID playerId, Instant capturedAt) {
        long timestampVersion;
        try {
            timestampVersion = Math.max(1L, capturedAt.toEpochMilli());
        } catch (ArithmeticException exception) {
            timestampVersion = Math.max(1L, capturedAt.getEpochSecond());
        }
        long previous = latestVersions.getOrDefault(playerId, 0L);
        long next = Math.max(timestampVersion, previous == Long.MAX_VALUE ? Long.MAX_VALUE : previous + 1);
        latestVersions.put(playerId, next);
        return next;
    }

    public CompletableFuture<Optional<PlayerRecoverySnapshot>> read(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return repository.read(NAMESPACE, playerId.toString()).thenApply(record ->
                record.map(value -> codec.decode(value.payload())));
    }

    public CompletableFuture<Void> delete(UUID playerId) {
        return repository.delete(NAMESPACE, Objects.requireNonNull(playerId, "playerId").toString());
    }

    private record CaptureKey(UUID playerId, UUID instanceId, java.time.Instant capturedAt,
                              String idempotencyKey) { }
}
