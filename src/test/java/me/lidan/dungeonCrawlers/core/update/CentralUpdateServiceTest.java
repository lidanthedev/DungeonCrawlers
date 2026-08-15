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
}
