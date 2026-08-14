package me.lidan.dungeonCrawlers.core.claim;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static me.lidan.dungeonCrawlers.core.claim.OfferStateMachine.Event;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferStateMachineTest {
    private static final Instant COMPLETED = Instant.parse("2026-01-01T00:00:00Z");
    private final OfferStateMachine machine = new OfferStateMachine();

    @Test
    void debitAttemptRestartRequiresReconciliation() {
        UUID attempt = UUID.randomUUID();
        OfferSnapshot attempted = transition(offer(OfferState.AVAILABLE), Event.CONFIRM_DEBIT,
                COMPLETED.plusSeconds(1), attempt);

        OfferSnapshot recovered = machine.recoverAfterRestart(attempted, COMPLETED.plusSeconds(2));

        assertEquals(OfferState.RECONCILIATION_REQUIRED, recovered.state());
        assertEquals(attempt, recovered.attemptId());
    }

    @Test
    void sessionExpiryMustBeStrictlyAfterSessionStart() {
        Instant started = COMPLETED.plusSeconds(10);
        for (Instant expires : List.of(started, started.minusSeconds(1))) {
            assertThrows(IllegalArgumentException.class, () -> new OfferSnapshot(UUID.randomUUID(),
                    OfferMode.RECOVERED, OfferState.AVAILABLE, null, COMPLETED, COMPLETED,
                    COMPLETED.plus(Duration.ofMinutes(5)), started, expires, COMPLETED, null,
                    "VaultProvider", UUID.randomUUID(), 100, List.of()));
        }
    }

    @Test
    void deadlinesAreExclusiveAndClockNeverMovesBackward() {
        OfferSnapshot available = offer(OfferState.AVAILABLE);
        assertTrue(available.isOpen(COMPLETED.plus(Duration.ofMinutes(5)).minusMillis(1)));
        assertFalse(available.isOpen(COMPLETED.plus(Duration.ofMinutes(5))));
        OfferSnapshot expired = transition(available, Event.DEADLINE_REACHED,
                COMPLETED.plus(Duration.ofMinutes(5)), null);
        assertEquals(OfferState.EXPIRED, expired.state());
        assertFalse(machine.transition(expired, Event.PROVIDER_RESTORED, COMPLETED, null).accepted());
    }

    @Test
    void ownedOffersNeverReturnToUnownedOrExpire() {
        OfferSnapshot owned = offer(OfferState.OWNED);
        for (Event event : Event.values()) {
            var result = machine.transition(owned, event, COMPLETED.plus(Duration.ofDays(1)), UUID.randomUUID());
            if (event == Event.REQUEST_DELIVERY || event == Event.PAYLOAD_INVALID) assertTrue(result.accepted());
            else assertFalse(result.accepted(), event.toString());
        }
    }

    @Test
    void deliveryQuarantineReturnsToItsPriorState() {
        OfferSnapshot pending = offer(OfferState.DELIVERY_PENDING);
        OfferSnapshot quarantined = transition(pending, Event.DELIVERY_UNSAFE, COMPLETED.plusSeconds(1), null);
        assertEquals(OfferState.DELIVERY_PENDING, quarantined.quarantinePrior());
        assertEquals(OfferState.DELIVERY_PENDING,
                transition(quarantined, Event.PAYLOAD_REPAIRED, COMPLETED.plusSeconds(2), null).state());
    }

    @Test
    void claimGroupRejectsStaleAttemptsAndLocksAfterClaim() {
        UUID offer = UUID.randomUUID();
        UUID attempt = UUID.randomUUID();
        ClaimGroup locked = ClaimGroup.none().attempt(offer, attempt);
        assertThrows(IllegalStateException.class, () -> locked.release(offer, UUID.randomUUID()));
        ClaimGroup claimed = locked.claim(offer, attempt);
        assertEquals(ClaimGroup.State.CLAIMED, claimed.state());
        assertThrows(IllegalStateException.class, () -> claimed.attempt(UUID.randomUUID(), UUID.randomUUID()));
    }

    private OfferSnapshot transition(OfferSnapshot source, Event event, Instant now, UUID attempt) {
        var result = machine.transition(source, event, now, attempt);
        assertTrue(result.accepted(), result.detail());
        return result.snapshot();
    }

    private static OfferSnapshot offer(OfferState state) {
        return new OfferSnapshot(UUID.randomUUID(), OfferMode.LIVE, state, null, COMPLETED,
                null, null, null, null, COMPLETED, null, "VaultProvider", UUID.randomUUID(), 100, List.of());
    }
}
