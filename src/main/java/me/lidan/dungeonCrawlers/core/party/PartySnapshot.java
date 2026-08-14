package me.lidan.dungeonCrawlers.core.party;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PartySnapshot(UUID leaderId, List<UUID> onlineMembers, boolean solo) {
    public PartySnapshot {
        Objects.requireNonNull(leaderId, "leaderId");
        onlineMembers = List.copyOf(onlineMembers);
        if (onlineMembers.isEmpty() || !onlineMembers.contains(leaderId)) {
            throw new IllegalArgumentException("online party snapshot must contain its leader");
        }
        if (onlineMembers.stream().distinct().count() != onlineMembers.size()) {
            throw new IllegalArgumentException("online party snapshot contains duplicate members");
        }
        if (solo && onlineMembers.size() != 1) {
            throw new IllegalArgumentException("solo snapshot must contain exactly one member");
        }
    }
}
