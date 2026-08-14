package me.lidan.dungeonCrawlers.compatibility;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatibilityReportTest {
    @Test
    void manualChecksBlockHumanGateButNotAutomatedChecks() {
        CompatibilityReport report = new CompatibilityReport(Instant.EPOCH, List.of(
                new ProbeResult("automatic", ProbeStatus.PASS, "ok"),
                new ProbeResult("manual", ProbeStatus.MANUAL_REQUIRED, "staging")
        ));

        assertTrue(report.passesAutomatedChecks());
        assertFalse(report.passesHumanGate());
    }

    @Test
    void failureBlocksBothGates() {
        CompatibilityReport report = new CompatibilityReport(Instant.EPOCH, List.of(
                new ProbeResult("required", ProbeStatus.FAIL, "missing")
        ));

        assertFalse(report.passesAutomatedChecks());
        assertFalse(report.passesHumanGate());
    }

    @Test
    void verifiedFallbackAllowsHumanGate() {
        CompatibilityReport report = new CompatibilityReport(Instant.EPOCH, List.of(
                new ProbeResult("optional", ProbeStatus.FALLBACK_PASS, "verified fallback")
        ));

        assertTrue(report.passesHumanGate());
    }

    @Test
    void absentIntegrationWithoutFallbackBlocksHumanGate() {
        CompatibilityReport report = new CompatibilityReport(Instant.EPOCH, List.of(
                new ProbeResult("optional", ProbeStatus.ABSENT, "no fallback")
        ));

        assertFalse(report.passesHumanGate());
    }

    @Test
    void reportDefensivelyCopiesResults() {
        var mutable = new java.util.ArrayList<ProbeResult>();
        mutable.add(new ProbeResult("one", ProbeStatus.PASS, "ok"));
        CompatibilityReport report = new CompatibilityReport(Instant.EPOCH, mutable);
        mutable.clear();

        assertTrue(report.passesHumanGate());
        assertTrue(report.results().size() == 1);
    }
}
