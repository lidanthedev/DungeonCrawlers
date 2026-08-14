package me.lidan.dungeonCrawlers.core.party;

import me.lidan.dungeonCrawlers.integration.PartyProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class PartySnapshotPolicy {
    public SnapshotResult snapshot(UUID requester, PartyProvider.PartyLookup lookup, int maxPartySize) {
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(lookup, "lookup");
        if (maxPartySize < 1) return SnapshotResult.failure("max party size must be positive");
        if (lookup.status() == PartyProvider.Status.ERROR) {
            return SnapshotResult.failure("party provider error: " + lookup.detail());
        }
        if (lookup.status() == PartyProvider.Status.SOLO) {
            if (!lookup.onlineMembers().equals(List.of(requester))) {
                return SnapshotResult.failure("solo fallback did not positively identify only the requester");
            }
            return SnapshotResult.success(new PartySnapshot(requester, List.of(requester), true));
        }
        if (!requester.equals(lookup.leader())) {
            return SnapshotResult.failure("only the online party leader may start a dungeon");
        }
        List<UUID> members = new ArrayList<>(lookup.onlineMembers());
        members.sort(Comparator.naturalOrder());
        if (!members.contains(requester)) {
            return SnapshotResult.failure("party leader is not in the online member snapshot");
        }
        if (members.stream().distinct().count() != members.size()) {
            return SnapshotResult.failure("party provider returned duplicate members");
        }
        if (members.size() > maxPartySize) {
            return SnapshotResult.failure("online party exceeds floor capacity " + maxPartySize);
        }
        return SnapshotResult.success(new PartySnapshot(requester, members, false));
    }

    public record SnapshotResult(PartySnapshot snapshot, String error) {
        public static SnapshotResult success(PartySnapshot snapshot) {
            return new SnapshotResult(Objects.requireNonNull(snapshot), null);
        }

        public static SnapshotResult failure(String error) {
            return new SnapshotResult(null, Objects.requireNonNull(error));
        }

        public boolean successful() {
            return snapshot != null;
        }
    }
}
