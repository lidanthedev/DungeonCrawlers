package me.lidan.dungeonCrawlers.core.reservation;

import me.lidan.dungeonCrawlers.core.party.PartySnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerReservationServiceTest {
    @Test
    void overlappingConcurrentReservationsHaveExactlyOneWinner() throws Exception {
        PlayerReservationService service = new PlayerReservationService();
        UUID shared = UUID.randomUUID();
        PartySnapshot first = new PartySnapshot(shared, List.of(shared, UUID.randomUUID()), false);
        PartySnapshot second = new PartySnapshot(shared, List.of(shared, UUID.randomUUID()), false);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> a = executor.submit(() -> { start.await(); return service.reserve(UUID.randomUUID(), first).successful(); });
            Future<Boolean> b = executor.submit(() -> { start.await(); return service.reserve(UUID.randomUUID(), second).successful(); });
            start.countDown();
            assertEquals(1, (a.get() ? 1 : 0) + (b.get() ? 1 : 0));
        }
        assertEquals(2, service.snapshot().size());
    }

    @Test
    void promotionAndReleaseAreInstanceGuardedAndIdempotent() {
        PlayerReservationService service = new PlayerReservationService();
        UUID player = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        assertTrue(service.reserve(instance, new PartySnapshot(player, List.of(player), true)).successful());
        assertTrue(service.promote(instance));
        assertFalse(service.promote(instance));
        assertEquals(PlayerReservationService.PlayerState.ACTIVE, service.lookup(player).orElseThrow().state());
        assertEquals(1, service.release(instance));
        assertEquals(0, service.release(instance));
    }

    @Test
    void admissionPauseKeepsReservationCountStableThroughReloadOperation() throws Exception {
        PlayerReservationService service = new PlayerReservationService();
        UUID firstPlayer = UUID.randomUUID();
        assertTrue(service.reserve(UUID.randomUUID(), new PartySnapshot(firstPlayer, List.of(firstPlayer), true))
                .successful());
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Integer> reload = executor.submit(() -> service.withAdmissionPaused(active -> {
                entered.countDown();
                try {
                    release.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return active;
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            UUID secondPlayer = UUID.randomUUID();
            Future<Boolean> reservation = executor.submit(() -> service.reserve(UUID.randomUUID(),
                    new PartySnapshot(secondPlayer, List.of(secondPlayer), true)).successful());
            assertThrows(java.util.concurrent.TimeoutException.class,
                    () -> reservation.get(100, TimeUnit.MILLISECONDS));
            release.countDown();
            assertEquals(1, reload.get(5, TimeUnit.SECONDS));
            assertTrue(reservation.get(5, TimeUnit.SECONDS));
        }
    }
}
