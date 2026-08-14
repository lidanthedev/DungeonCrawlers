package me.lidan.dungeonCrawlers.persistence;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DurableWriteReceipt(UUID operationId, String idempotencyKey, long recordVersion,
                                  String checksum, Path durablePath, Instant committedAt) {
    public DurableWriteReceipt {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(checksum, "checksum");
        Objects.requireNonNull(durablePath, "durablePath");
        Objects.requireNonNull(committedAt, "committedAt");
        if (recordVersion < 0) throw new IllegalArgumentException("recordVersion must not be negative: " + recordVersion);
        if (idempotencyKey.isEmpty()) throw new IllegalArgumentException("idempotencyKey must not be empty");
        if (checksum.isEmpty()) throw new IllegalArgumentException("checksum must not be empty");
    }
}
