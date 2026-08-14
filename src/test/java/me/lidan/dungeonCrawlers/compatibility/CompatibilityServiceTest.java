package me.lidan.dungeonCrawlers.compatibility;

import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CompatibilityServiceTest {
    @Test
    void emptyListenerInventoryFailsAutomatedChecks() {
        ProbeResult result = CompatibilityService.listenerProbe(new RegisteredListener[0]);
        CompatibilityReport report = new CompatibilityReport(java.time.Instant.EPOCH, java.util.List.of(result));

        assertEquals(ProbeStatus.FAIL, result.status());
        assertFalse(report.passesAutomatedChecks());
    }
}
