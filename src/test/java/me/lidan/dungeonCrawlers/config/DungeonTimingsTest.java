package me.lidan.dungeonCrawlers.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DungeonTimingsTest {
    @Test
    void defaultsShareTheCompletionDeadline() {
        DungeonTimings timings = DungeonTimings.defaults();
        assertEquals(timings.completionTimeout(), timings.liveRewardWindow());
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 60, 300})
    void acceptsMatchingPositiveDurations(long seconds) {
        assertDoesNotThrow(() -> withCompletionAndRewardWindow(seconds, seconds));
    }

    @ParameterizedTest
    @CsvSource({
            "300, 299, completionTimeout must equal liveRewardWindow",
            "300, 301, completionTimeout must equal liveRewardWindow",
            "0, 0, completionTimeout must be positive",
            "-1, -1, completionTimeout must be positive",
            "0, 300, completionTimeout must be positive",
            "300, 0, liveRewardWindow must be positive",
            "-1, 300, completionTimeout must be positive",
            "300, -1, liveRewardWindow must be positive"
    })
    void rejectsDivergentOrNonPositiveDurations(long completion, long rewardWindow, String message) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> withCompletionAndRewardWindow(completion, rewardWindow));
        assertEquals(message, exception.getMessage());
    }

    private static DungeonTimings withCompletionAndRewardWindow(long completion, long rewardWindow) {
        DungeonTimings defaults = DungeonTimings.defaults();
        return new DungeonTimings(
                defaults.preparationWarning(), defaults.preparationTimeout(),
                defaults.runWarning(), defaults.runTimeout(), defaults.failedReadingPeriod(),
                defaults.completionWarning(), Duration.ofSeconds(completion), defaults.completionFinalCountdown(),
                defaults.reviveDuration(), defaults.adminReviveDuration(), defaults.portalCountdown(),
                defaults.bossSpawnDelay(), Duration.ofSeconds(rewardWindow), defaults.recoveredRewardWindow(),
                defaults.recoveredRewardSessionWindow(), defaults.scoreFreeTime(), defaults.scorePenaltyInterval(),
                defaults.generationCleanupDeadline(), defaults.teleportPermit(), defaults.actionBarCooldown(),
                defaults.persistenceShutdownGrace(), defaults.pendingRecoveryMaxAge());
    }
}
