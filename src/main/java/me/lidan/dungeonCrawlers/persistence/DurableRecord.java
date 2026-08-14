package me.lidan.dungeonCrawlers.persistence;

import java.nio.file.Path;
import java.util.Objects;

public record DurableRecord(String namespace, String recordId, byte[] payload, String checksum, Path path) {
    public DurableRecord {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(recordId, "recordId");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(checksum, "checksum");
        Objects.requireNonNull(path, "path");
        payload = payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
