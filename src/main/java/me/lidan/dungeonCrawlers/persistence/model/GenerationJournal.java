package me.lidan.dungeonCrawlers.persistence.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record GenerationJournal(UUID instanceId, long seed, int slotId, String world, List<PlannedBounds> plannedBounds,
                                List<UUID> participants, String configHash, String contentHash,
                                String algorithmVersion, Status status, Instant createdAt) {
    public GenerationJournal {
        Objects.requireNonNull(instanceId); Objects.requireNonNull(world); Objects.requireNonNull(configHash);
        Objects.requireNonNull(contentHash); Objects.requireNonNull(algorithmVersion); Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
        if (slotId < 0) throw new IllegalArgumentException("slotId must not be negative");
        if (world.isBlank() || configHash.isBlank() || contentHash.isBlank() || algorithmVersion.isBlank()) {
            throw new IllegalArgumentException("journal identifiers must not be blank");
        }
        plannedBounds = List.copyOf(plannedBounds);
        participants = participants.stream().sorted().toList();
        if (plannedBounds.isEmpty()) throw new IllegalArgumentException("planned bounds must not be empty");
        if (participants.isEmpty() || participants.stream().distinct().count() != participants.size()) {
            throw new IllegalArgumentException("participants must be non-empty and unique");
        }
    }

    public enum Status { PLANNED }

    public record PlannedBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public PlannedBounds {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("planned bounds minimum exceeds maximum");
            }
        }
    }
}
