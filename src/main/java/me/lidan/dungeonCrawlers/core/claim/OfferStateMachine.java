package me.lidan.dungeonCrawlers.core.claim;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class OfferStateMachine {
    public TransitionResult transition(OfferSnapshot offer, Event event, Instant observedNow, UUID attemptId) {
        Objects.requireNonNull(offer, "offer");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(observedNow, "observedNow");
        OfferState current = offer.state();
        boolean open = offer.isOpen(observedNow);
        OfferState target = target(current, offer.quarantinePrior(), event, open);
        if (target == null) return new TransitionResult(offer, false, false, "illegal transition");
        if (target == current) return new TransitionResult(offer, true, false, "already in " + current);
        if (target == OfferState.DEBIT_ATTEMPTED && attemptId == null) {
            return new TransitionResult(offer, false, false, "debit attempt requires attempt id");
        }
        UUID retainedAttempt = target == OfferState.AVAILABLE || target == OfferState.EXPIRED ? null
                : (attemptId != null ? attemptId : offer.attemptId());
        OfferState prior = target == OfferState.OWNED_DELIVERY_QUARANTINED ? current
                : (target == OfferState.OWNED || target == OfferState.DELIVERY_PENDING ? null : offer.quarantinePrior());
        return new TransitionResult(offer.with(target, prior, observedNow, retainedAttempt), true, true,
                current + " -> " + target);
    }

    public OfferSnapshot recoverAfterRestart(OfferSnapshot offer, Instant observedNow) {
        if (offer.state() != OfferState.DEBIT_ATTEMPTED) return offer;
        return offer.with(OfferState.RECONCILIATION_REQUIRED, null, observedNow, offer.attemptId());
    }

    private static OfferState target(OfferState state, OfferState prior, Event event, boolean open) {
        if (event == Event.SIBLING_CLAIMED) {
            return switch (state) {
                case AVAILABLE, OFFER_BLOCKED_PROVIDER, OFFER_PAYLOAD_QUARANTINED -> OfferState.EXPIRED;
                default -> null;
            };
        }
        if (event == Event.DEADLINE_REACHED) {
            return switch (state) {
                case AVAILABLE, OFFER_BLOCKED_PROVIDER, OFFER_PAYLOAD_QUARANTINED -> OfferState.EXPIRED;
                default -> null;
            };
        }
        return switch (state) {
            case AVAILABLE -> switch (event) {
                case PROVIDER_MISSING -> open ? OfferState.OFFER_BLOCKED_PROVIDER : OfferState.EXPIRED;
                case PAYLOAD_INVALID -> open ? OfferState.OFFER_PAYLOAD_QUARANTINED : OfferState.EXPIRED;
                case CONFIRM_DEBIT -> open ? OfferState.DEBIT_ATTEMPTED : OfferState.EXPIRED;
                default -> null;
            };
            case OFFER_BLOCKED_PROVIDER -> event == Event.PROVIDER_RESTORED
                    ? (open ? OfferState.AVAILABLE : OfferState.EXPIRED) : null;
            case OFFER_PAYLOAD_QUARANTINED -> event == Event.PAYLOAD_REPAIRED
                    ? (open ? OfferState.AVAILABLE : OfferState.EXPIRED) : null;
            case DEBIT_ATTEMPTED -> switch (event) {
                case DEBIT_FAILED -> open ? OfferState.AVAILABLE : OfferState.EXPIRED;
                case DEBIT_SUCCEEDED -> OfferState.OWNED;
                case DEBIT_AMBIGUOUS, OWNED_PERSISTENCE_FAILED -> OfferState.RECONCILIATION_REQUIRED;
                default -> null;
            };
            case RECONCILIATION_REQUIRED -> switch (event) {
                case RECONCILE_CHARGED -> OfferState.OWNED;
                case RECONCILE_NOT_CHARGED -> open ? OfferState.AVAILABLE : OfferState.EXPIRED;
                default -> null;
            };
            case OWNED -> switch (event) {
                case REQUEST_DELIVERY -> OfferState.DELIVERY_PENDING;
                case PAYLOAD_INVALID -> OfferState.OWNED_DELIVERY_QUARANTINED;
                default -> null;
            };
            case DELIVERY_PENDING -> switch (event) {
                case DELIVERY_VERIFIED -> OfferState.DELIVERED;
                case DELIVERY_UNSAFE -> OfferState.OWNED_DELIVERY_QUARANTINED;
                default -> null;
            };
            case OWNED_DELIVERY_QUARANTINED -> event == Event.PAYLOAD_REPAIRED ? prior : null;
            case DELIVERED, EXPIRED -> null;
        };
    }

    public enum Event {
        DEADLINE_REACHED,
        PROVIDER_MISSING,
        PROVIDER_RESTORED,
        PAYLOAD_INVALID,
        PAYLOAD_REPAIRED,
        CONFIRM_DEBIT,
        DEBIT_FAILED,
        DEBIT_SUCCEEDED,
        DEBIT_AMBIGUOUS,
        OWNED_PERSISTENCE_FAILED,
        RECONCILE_CHARGED,
        RECONCILE_NOT_CHARGED,
        REQUEST_DELIVERY,
        DELIVERY_VERIFIED,
        DELIVERY_UNSAFE,
        SIBLING_CLAIMED
    }

    public record TransitionResult(OfferSnapshot snapshot, boolean accepted, boolean changed, String detail) {
    }
}
