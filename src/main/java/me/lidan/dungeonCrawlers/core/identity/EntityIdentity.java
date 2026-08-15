package me.lidan.dungeonCrawlers.core.identity;

import java.util.Objects;
import java.util.UUID;

public record EntityIdentity(UUID instanceId, int roomIndex) {
    public EntityIdentity {
        Objects.requireNonNull(instanceId, "instanceId");
        if (roomIndex < 0) throw new IllegalArgumentException("room index must not be negative");
    }
}
