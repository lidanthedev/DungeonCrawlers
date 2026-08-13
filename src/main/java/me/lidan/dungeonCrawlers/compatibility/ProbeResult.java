package me.lidan.dungeonCrawlers.compatibility;

import java.util.Objects;

public record ProbeResult(String id, ProbeStatus status, String detail) {
    public ProbeResult {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(detail, "detail");
    }

    public boolean blocksGate() {
        return status != ProbeStatus.PASS;
    }
}

