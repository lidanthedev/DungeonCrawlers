package me.lidan.dungeonCrawlers.core.update;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
