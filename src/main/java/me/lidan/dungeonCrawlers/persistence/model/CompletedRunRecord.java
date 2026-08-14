package me.lidan.dungeonCrawlers.persistence.model;

import me.lidan.dungeonCrawlers.core.claim.ClaimGroup;
import me.lidan.dungeonCrawlers.core.claim.OfferSnapshot;
import me.lidan.dungeonCrawlers.core.score.ScoreService.ScoreResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CompletedRunRecord(UUID instanceId, String floorId, long seed, String configHash,
                                 String contentHash, String algorithmVersion, ScoreResult score,
                                 Map<UUID, ParticipantResult> participants, Set<UUID> entitledPlayers,
                                 Map<UUID, List<OfferSnapshot>> offers, Map<UUID, ClaimGroup> claimGroups,
                                 Instant completedAt, Instant claimDeadline, RecoveryStatus recoveryStatus,
                                 Map<UUID, String> playerSnapshotReferences, List<JournalBounds> worldBounds) {
    public CompletedRunRecord {
        Objects.requireNonNull(instanceId); Objects.requireNonNull(floorId); Objects.requireNonNull(configHash);
        Objects.requireNonNull(contentHash); Objects.requireNonNull(algorithmVersion); Objects.requireNonNull(score);
        Objects.requireNonNull(completedAt); Objects.requireNonNull(claimDeadline); Objects.requireNonNull(recoveryStatus);
        participants = Map.copyOf(participants); entitledPlayers = Set.copyOf(entitledPlayers);
        offers = offers.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        claimGroups = Map.copyOf(claimGroups); playerSnapshotReferences = Map.copyOf(playerSnapshotReferences);
        worldBounds = List.copyOf(worldBounds);
        if (!participants.keySet().containsAll(entitledPlayers) || !offers.keySet().equals(entitledPlayers)
                || !claimGroups.keySet().equals(entitledPlayers)) {
            throw new IllegalArgumentException("entitlements, offers, and claim groups must match participants");
        }
    }

    public enum RecoveryStatus { LIVE, RECOVERY_REQUIRED, CONVERTING, RECOVERED, CLEANED }

    public record ParticipantResult(UUID playerId, int deaths, boolean activeAtCompletion) {
        public ParticipantResult { Objects.requireNonNull(playerId); if (deaths < 0) throw new IllegalArgumentException(); }
    }

    public record JournalBounds(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public JournalBounds {
            Objects.requireNonNull(world);
            if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("invalid bounds");
        }
    }
}
