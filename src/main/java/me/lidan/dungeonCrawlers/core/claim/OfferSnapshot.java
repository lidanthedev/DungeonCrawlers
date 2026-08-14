package me.lidan.dungeonCrawlers.core.claim;

import me.lidan.dungeonCrawlers.core.reward.RewardModels.ItemPayload;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OfferSnapshot(UUID offerId, OfferMode mode, OfferState state, OfferState quarantinePrior,
                            Instant completedAt, Instant recoveredAt, Instant outerStartDeadline,
                            Instant sessionStartedAt, Instant sessionExpiresAt, Instant clockHighWater,
                            UUID attemptId, String provider, UUID accountId, long price,
                            List<ItemPayload> items) {
    private static final Duration LIVE_WINDOW = Duration.ofMinutes(5);

    public OfferSnapshot {
        Objects.requireNonNull(offerId, "offerId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(completedAt, "completedAt");
        Objects.requireNonNull(clockHighWater, "clockHighWater");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(accountId, "accountId");
        items = List.copyOf(items);
        if (price < 0) throw new IllegalArgumentException("price must not be negative");
        if (mode == OfferMode.RECOVERED && outerStartDeadline == null) {
            throw new IllegalArgumentException("recovered offer requires outer deadline");
        }
        if ((sessionStartedAt == null) != (sessionExpiresAt == null)) {
            throw new IllegalArgumentException("session timestamps must be set together");
        }
        if (sessionStartedAt != null && !sessionExpiresAt.isAfter(sessionStartedAt)) {
            throw new IllegalArgumentException("session expiry must be after session start");
        }
        if (state == OfferState.OWNED_DELIVERY_QUARANTINED
                && quarantinePrior != OfferState.OWNED && quarantinePrior != OfferState.DELIVERY_PENDING) {
            throw new IllegalArgumentException("delivery quarantine requires an owned prior state");
        }
    }

    public Instant claimDeadline() {
        if (mode == OfferMode.LIVE) return completedAt.plus(LIVE_WINDOW);
        return sessionExpiresAt != null ? sessionExpiresAt : outerStartDeadline;
    }

    public Instant effectiveNow(Instant observedNow) {
        return observedNow.isAfter(clockHighWater) ? observedNow : clockHighWater;
    }

    public boolean isOpen(Instant observedNow) {
        return effectiveNow(observedNow).isBefore(claimDeadline());
    }

    public OfferSnapshot with(OfferState target, OfferState prior, Instant observedNow, UUID newAttemptId) {
        return new OfferSnapshot(offerId, mode, target, prior, completedAt, recoveredAt, outerStartDeadline,
                sessionStartedAt, sessionExpiresAt, max(clockHighWater, observedNow), newAttemptId,
                provider, accountId, price, items);
    }

    private static Instant max(Instant left, Instant right) {
        return right.isAfter(left) ? right : left;
    }
}
