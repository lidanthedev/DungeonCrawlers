package me.lidan.dungeonCrawlers.config;

import java.time.Duration;
import java.util.Objects;

/** Immutable runtime periods shared by the plugin's deadline and integration services. */
public record DungeonTimings(
        Duration preparationWarning,
        Duration preparationTimeout,
        Duration runWarning,
        Duration runTimeout,
        Duration failedReadingPeriod,
        Duration completionWarning,
        Duration completionTimeout,
        Duration completionFinalCountdown,
        Duration reviveDuration,
        Duration adminReviveDuration,
        Duration portalCountdown,
        Duration bossSpawnDelay,
        Duration liveRewardWindow,
        Duration recoveredRewardWindow,
        Duration recoveredRewardSessionWindow,
        Duration scoreFreeTime,
        Duration scorePenaltyInterval,
        Duration generationCleanupDeadline,
        Duration teleportPermit,
        Duration actionBarCooldown,
        Duration persistenceShutdownGrace,
        Duration pendingRecoveryMaxAge) {

    public DungeonTimings {
        requirePositive("preparationWarning", preparationWarning);
        requirePositive("preparationTimeout", preparationTimeout);
        requirePositive("runWarning", runWarning);
        requirePositive("runTimeout", runTimeout);
        requirePositive("failedReadingPeriod", failedReadingPeriod);
        requirePositive("completionWarning", completionWarning);
        requirePositive("completionTimeout", completionTimeout);
        requirePositive("completionFinalCountdown", completionFinalCountdown);
        requirePositive("reviveDuration", reviveDuration);
        requirePositive("adminReviveDuration", adminReviveDuration);
        requirePositive("portalCountdown", portalCountdown);
        requirePositive("bossSpawnDelay", bossSpawnDelay);
        requirePositive("liveRewardWindow", liveRewardWindow);
        requirePositive("recoveredRewardWindow", recoveredRewardWindow);
        requirePositive("recoveredRewardSessionWindow", recoveredRewardSessionWindow);
        requirePositive("scoreFreeTime", scoreFreeTime);
        requirePositive("scorePenaltyInterval", scorePenaltyInterval);
        requirePositive("generationCleanupDeadline", generationCleanupDeadline);
        requirePositive("teleportPermit", teleportPermit);
        requirePositive("actionBarCooldown", actionBarCooldown);
        requirePositive("persistenceShutdownGrace", persistenceShutdownGrace);
        requirePositive("pendingRecoveryMaxAge", pendingRecoveryMaxAge);
    }

    public static DungeonTimings defaults() {
        return new DungeonTimings(
                Duration.ofMinutes(1),
                Duration.ofMinutes(5),
                Duration.ofMinutes(1),
                Duration.ofMinutes(60),
                Duration.ofSeconds(10),
                Duration.ofMinutes(1),
                Duration.ofMinutes(5),
                Duration.ofSeconds(10),
                Duration.ofSeconds(60),
                Duration.ofSeconds(3),
                Duration.ofSeconds(5),
                Duration.ofSeconds(1),
                Duration.ofMinutes(5),
                Duration.ofHours(24),
                Duration.ofMinutes(5),
                Duration.ofMinutes(8),
                Duration.ofMinutes(1),
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                Duration.ofSeconds(1),
                Duration.ofSeconds(5),
                Duration.ofHours(24));
    }

    private static void requirePositive(String name, Duration value) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
