package me.lidan.dungeonCrawlers.persistence;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record DurableWrite(UUID operationId, UUID instanceId, String namespace, String recordId,
                           String idempotencyKey, long recordVersion, byte[] payload) {
    private static final Pattern SAFE_PATH_PART = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,63}$");

    public DurableWrite {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(recordId, "recordId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(payload, "payload");
        if (!SAFE_PATH_PART.matcher(namespace).matches() || !SAFE_PATH_PART.matcher(recordId).matches()) {
            throw new IllegalArgumentException("namespace and record id must be safe IDs");
        }
        if (idempotencyKey.isBlank() || recordVersion < 0) {
            throw new IllegalArgumentException("invalid durable write metadata");
        }
        payload = payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
