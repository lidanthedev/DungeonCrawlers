package me.lidan.dungeonCrawlers.compatibility;

import java.time.Instant;
import java.util.List;

public record CompatibilityReport(Instant createdAt, List<ProbeResult> results) {
    public CompatibilityReport {
        results = List.copyOf(results);
    }

    public boolean passesAutomatedChecks() {
        return results.stream().noneMatch(result -> result.status() == ProbeStatus.FAIL);
    }

    public boolean passesHumanGate() {
        return results.stream().noneMatch(ProbeResult::blocksGate);
    }
}

