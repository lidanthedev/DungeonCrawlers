package me.lidan.dungeonCrawlers.persistence.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Durable player state needed to restore a run without touching inventory or experience. */
public record PlayerRecoverySnapshot(UUID playerId, UUID instanceId, String world, double x, double y, double z,
                                     float yaw, float pitch, String gameMode, double health, int foodLevel,
                                     float saturation, float exhaustion, int fireTicks, int remainingAir,
                                     int maximumAir, Instant capturedAt) {
    public PlayerRecoverySnapshot {
        Objects.requireNonNull(playerId); Objects.requireNonNull(instanceId); Objects.requireNonNull(world);
        Objects.requireNonNull(gameMode); Objects.requireNonNull(capturedAt);
        if (world.isBlank() || gameMode.isBlank()) throw new IllegalArgumentException("snapshot identifiers are blank");
        if (!finite(x) || !finite(y) || !finite(z) || !finite(yaw) || !finite(pitch)
                || !finite(health) || !finite(saturation) || !finite(exhaustion)) {
            throw new IllegalArgumentException("snapshot contains non-finite values");
        }
        if (health < 0 || foodLevel < 0 || foodLevel > 20 || saturation < 0 || exhaustion < 0
                || fireTicks < 0 || maximumAir < 1 || remainingAir < 0 || remainingAir > maximumAir) {
            throw new IllegalArgumentException("snapshot contains invalid player attributes");
        }
    }

    private static boolean finite(double value) { return Double.isFinite(value); }

    private static boolean finite(float value) { return Float.isFinite(value); }
}
