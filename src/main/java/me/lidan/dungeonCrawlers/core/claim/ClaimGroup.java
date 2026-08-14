package me.lidan.dungeonCrawlers.core.claim;

import java.util.Objects;
import java.util.UUID;

public record ClaimGroup(State state, UUID winnerOfferId, UUID attemptId) {
    public ClaimGroup {
        Objects.requireNonNull(state, "state");
        if (state == State.NONE && (winnerOfferId != null || attemptId != null)) {
            throw new IllegalArgumentException("empty claim group cannot have a winner");
        }
        if (state == State.ATTEMPTED && (winnerOfferId == null || attemptId == null)) {
            throw new IllegalArgumentException("attempted claim group requires winner and attempt");
        }
        if (state == State.CLAIMED && winnerOfferId == null) {
            throw new IllegalArgumentException("claimed group requires winner");
        }
    }

    public static ClaimGroup none() {
        return new ClaimGroup(State.NONE, null, null);
    }

    public ClaimGroup attempt(UUID offerId, UUID newAttemptId) {
        if (state != State.NONE) throw new IllegalStateException("claim group is already locked");
        return new ClaimGroup(State.ATTEMPTED, offerId, newAttemptId);
    }

    public ClaimGroup release(UUID offerId, UUID expectedAttemptId) {
        requireAttempt(offerId, expectedAttemptId);
        return none();
    }

    public ClaimGroup claim(UUID offerId, UUID expectedAttemptId) {
        requireAttempt(offerId, expectedAttemptId);
        return new ClaimGroup(State.CLAIMED, offerId, expectedAttemptId);
    }

    private void requireAttempt(UUID offerId, UUID expectedAttemptId) {
        if (state != State.ATTEMPTED || !Objects.equals(winnerOfferId, offerId)
                || !Objects.equals(attemptId, expectedAttemptId)) {
            throw new IllegalStateException("claim attempt does not match the durable lock");
        }
    }

    public enum State { NONE, ATTEMPTED, CLAIMED }
}
