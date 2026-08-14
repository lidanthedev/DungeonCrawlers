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
    }
}
