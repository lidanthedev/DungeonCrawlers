package me.lidan.dungeonCrawlers.core.update;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CentralUpdateServiceTest {
    @Test
    void ticksAllInstancesWithOneTimestampAndIsolatesFailure() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        List<String> diagnostics = new ArrayList<>();
        List<Instant> observed = new ArrayList<>();
        CentralUpdateService service = new CentralUpdateService(Clock.fixed(start, ZoneOffset.UTC), diagnostics::add);
        UUID failing = UUID.randomUUID();
        UUID healthy = UUID.randomUUID();

        assertTrue(service.register(failing, ignored -> { throw new IllegalStateException("boom"); }));
        assertTrue(service.register(healthy, observed::add));
        assertFalse(service.register(healthy, ignored -> { }));

        var report = service.tick();

        assertEquals(2, report.attempted());
        assertEquals(List.of(failing), report.failures());
        assertEquals(List.of(start), observed);
        assertEquals(1, diagnostics.size());
        assertEquals(2, service.size());
    }

    @Test
    void registrationRemovalAndRepeatedTicksAreDeterministic() {
        CentralUpdateService service = new CentralUpdateService(Clock.systemUTC(), ignored -> { });
        UUID instance = UUID.randomUUID();
        int[] calls = {0};
        service.register(instance, ignored -> calls[0]++);

        service.tick(Instant.EPOCH);
        service.tick(Instant.EPOCH.plusSeconds(1));
        assertEquals(2, calls[0]);
        assertTrue(service.remove(instance));
        assertFalse(service.remove(instance));
        service.tick(Instant.EPOCH.plusSeconds(2));
        assertEquals(2, calls[0]);
    }

    @Test
    void diagnosticsFailureDoesNotStopLaterUpdates() {
        List<Instant> observed = new ArrayList<>();
        CentralUpdateService service = new CentralUpdateService(Clock.systemUTC(), ignored -> {
            throw new IllegalStateException("diagnostics unavailable");
        });
        UUID failing = UUID.randomUUID();
        UUID healthy = UUID.randomUUID();
        service.register(failing, ignored -> { throw new IllegalStateException("boom"); });
        service.register(healthy, observed::add);

        var report = service.tick(Instant.EPOCH);

        assertEquals(List.of(failing), report.failures());
        assertEquals(List.of(Instant.EPOCH), observed);
    }

    @Test
    void supplementalCallbacksRunAlongsidePrimaryCallback() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        List<String> calls = new ArrayList<>();
        CentralUpdateService service = new CentralUpdateService(Clock.fixed(start, ZoneOffset.UTC), ignored -> { });
        UUID instance = UUID.randomUUID();
        assertTrue(service.register(instance, ignored -> calls.add("primary")));
        assertTrue(service.registerSupplemental(instance, ignored -> calls.add("supplemental")));

        CentralUpdateService.TickReport report = service.tick(start.plusSeconds(1));

        assertTrue(report.successful());
        assertEquals(List.of("primary", "supplemental"), calls);
    }

    @Test
    void supplementalRegistrationAndRemovalPreserveThePrimaryCallback() {
        CentralUpdateService service = new CentralUpdateService(Clock.systemUTC(), ignored -> { });
        UUID instance = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        var primary = (java.util.function.Consumer<Instant>) ignored -> calls.add("primary");
        var supplemental = (java.util.function.Consumer<Instant>) ignored -> calls.add("supplemental");

        assertFalse(service.registerSupplemental(UUID.randomUUID(), supplemental));
        assertTrue(service.register(instance, primary));
        assertTrue(service.registerSupplemental(instance, supplemental));
        assertTrue(service.removeSupplemental(instance, supplemental));
        assertFalse(service.removeSupplemental(instance, primary));

        service.tick(Instant.EPOCH);
        assertEquals(List.of("primary"), calls);
    }

    @Test
    void schedulerTickUsesAcceleratedElapsedTimeAndCanBeReset() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        AdvancingClock clock = new AdvancingClock(start);
        List<Instant> observed = new ArrayList<>();
        CentralUpdateService service = new CentralUpdateService(clock, ignored -> { });
        service.register(UUID.randomUUID(), observed::add);

        service.setTimeScale(60);
        clock.advanceSeconds(2);
        service.tick();
        assertEquals(List.of(start.plusSeconds(120)), observed);

        service.resetTimeScale();
        clock.advanceSeconds(2);
        service.tick();
        assertEquals(List.of(start.plusSeconds(120), start.plusSeconds(122)), observed);
    }

    @Test
    void instanceAdvanceOnlyMovesTargetAndPersistsAcrossSchedulerTicks() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        AdvancingClock clock = new AdvancingClock(start);
        List<Instant> targetObserved = new ArrayList<>();
        List<Instant> otherObserved = new ArrayList<>();
        CentralUpdateService service = new CentralUpdateService(clock, ignored -> { });
        UUID target = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        service.register(target, targetObserved::add);
        service.register(other, otherObserved::add);

        CentralUpdateService.TickReport advanced = service.advanceInstanceTime(target, Duration.ofSeconds(60));
        assertEquals(start.plusSeconds(60), advanced.now());
        assertEquals(List.of(start.plusSeconds(60)), targetObserved);
        assertTrue(otherObserved.isEmpty());

        clock.advanceSeconds(2);
        service.tick();
        assertEquals(List.of(start.plusSeconds(60), start.plusSeconds(62)), targetObserved);
        assertEquals(List.of(start.plusSeconds(2)), otherObserved);
        assertEquals(start.plusSeconds(62), service.time(target).orElseThrow().schedulerNow());
        assertEquals(Duration.ofSeconds(60), service.time(target).orElseThrow().instanceTimeOffset());
        assertEquals(Duration.ZERO, service.time(other).orElseThrow().instanceTimeOffset());
    }

    @Test
    void removingLastInstanceResetsTestTimeControlsBeforeTheNextRun() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        AdvancingClock clock = new AdvancingClock(start);
        CentralUpdateService service = new CentralUpdateService(clock, ignored -> { });
        UUID oldInstance = UUID.randomUUID();
        assertTrue(service.register(oldInstance, ignored -> { }));

        service.setTimeScale(60);
        clock.advanceSeconds(2);
        service.advanceInstanceTime(oldInstance, Duration.ofMinutes(1));
        assertTrue(service.remove(oldInstance));

        List<Instant> freshObserved = new ArrayList<>();
        UUID freshInstance = UUID.randomUUID();
        assertTrue(service.register(freshInstance, freshObserved::add));
        clock.advanceSeconds(1);
        service.tick();

        assertEquals(List.of(start.plusSeconds(3)), freshObserved);
        assertEquals(1, service.timeScale());
        assertEquals(Duration.ZERO, service.time(freshInstance).orElseThrow().instanceTimeOffset());
    }

    @Test
    void schedulerTimeScaleRejectsUnsafeValues() {
        CentralUpdateService service = new CentralUpdateService(Clock.systemUTC(), ignored -> { });

        assertThrows(IllegalArgumentException.class, () -> service.setTimeScale(0));
        assertThrows(IllegalArgumentException.class,
                () -> service.setTimeScale(CentralUpdateService.MAX_TIME_SCALE + 1));
    }

    @Test
    void freezeStopsRacingTicksAndRejectsNewRegistrations() {
        CentralUpdateService service = new CentralUpdateService(Clock.systemUTC(), ignored -> { });
        UUID instance = UUID.randomUUID();
        int[] calls = {0};
        assertTrue(service.register(instance, ignored -> calls[0]++));

        service.freeze();

        assertTrue(service.frozen());
        assertEquals(0, service.tick(Instant.EPOCH).attempted());
        assertEquals(0, calls[0]);
        assertFalse(service.register(UUID.randomUUID(), ignored -> { }));
    }

    @Test
    void freezeWaitsForCopiedCallbacksUntilTheTickFinishes() throws Exception {
        CentralUpdateService service = new CentralUpdateService(Clock.systemUTC(), ignored -> { });
        UUID instance = UUID.randomUUID();
        CountDownLatch primaryStarted = new CountDownLatch(1);
        CountDownLatch releasePrimary = new CountDownLatch(1);
        CountDownLatch supplementalStarted = new CountDownLatch(1);
        CountDownLatch releaseSupplemental = new CountDownLatch(1);
        assertTrue(service.register(instance, ignored -> {
            primaryStarted.countDown();
            await(releasePrimary);
        }));
        assertTrue(service.registerSupplemental(instance, ignored -> {
            supplementalStarted.countDown();
            await(releaseSupplemental);
        }));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var tick = executor.submit(() -> service.tick(Instant.EPOCH));
            assertTrue(primaryStarted.await(1, TimeUnit.SECONDS));
            var freeze = executor.submit(service::freeze);

            assertThrows(TimeoutException.class, () -> freeze.get(100, TimeUnit.MILLISECONDS));
            releasePrimary.countDown();
            assertTrue(supplementalStarted.await(1, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> freeze.get(100, TimeUnit.MILLISECONDS));
            releaseSupplemental.countDown();

            tick.get(1, TimeUnit.SECONDS);
            freeze.get(1, TimeUnit.SECONDS);
            assertTrue(service.frozen());
        } finally {
            releasePrimary.countDown();
            releaseSupplemental.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) throw new AssertionError("callback release timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static final class AdvancingClock extends Clock {
        private Instant current;

        private AdvancingClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        private void advanceSeconds(long seconds) {
            current = current.plusSeconds(seconds);
        }
    }
}
