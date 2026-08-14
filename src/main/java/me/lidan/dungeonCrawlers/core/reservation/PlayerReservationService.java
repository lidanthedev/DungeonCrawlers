package me.lidan.dungeonCrawlers.core.reservation;

import me.lidan.dungeonCrawlers.core.party.PartySnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PlayerReservationService {
    private final Map<UUID, PlayerIndexEntry> players = new HashMap<>();

    public synchronized ReservationResult reserve(UUID instanceId, PartySnapshot snapshot) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(snapshot, "snapshot");
        List<UUID> conflicts = snapshot.onlineMembers().stream().filter(players::containsKey).sorted().toList();
        if (!conflicts.isEmpty()) {
            return new ReservationResult(false, instanceId, List.of(), conflicts,
                    "players already reserved or active: " + conflicts);
        }
        snapshot.onlineMembers().forEach(playerId ->
                players.put(playerId, new PlayerIndexEntry(instanceId, PlayerState.RESERVED)));
        return new ReservationResult(true, instanceId, snapshot.onlineMembers(), List.of(), "reserved");
    }

    public synchronized boolean promote(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        List<UUID> owned = players.entrySet().stream()
                .filter(entry -> entry.getValue().instanceId().equals(instanceId)).map(Map.Entry::getKey).toList();
        if (owned.isEmpty() || owned.stream().anyMatch(id -> players.get(id).state() != PlayerState.RESERVED)) {
            return false;
        }
        owned.forEach(id -> players.put(id, new PlayerIndexEntry(instanceId, PlayerState.ACTIVE)));
        return true;
    }

    public synchronized int release(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        int before = players.size();
        players.entrySet().removeIf(entry -> entry.getValue().instanceId().equals(instanceId));
        return before - players.size();
    }

    public synchronized Optional<PlayerIndexEntry> lookup(UUID playerId) {
        return Optional.ofNullable(players.get(playerId));
    }

    public synchronized int activeReservationCount() {
        return Math.toIntExact(players.values().stream().map(PlayerIndexEntry::instanceId).distinct().count());
    }

    public synchronized Map<UUID, PlayerIndexEntry> snapshot() {
        return Map.copyOf(players);
    }

    public enum PlayerState { RESERVED, ACTIVE }

    public record PlayerIndexEntry(UUID instanceId, PlayerState state) {
        public PlayerIndexEntry {
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(state, "state");
        }
    }

    public record ReservationResult(boolean successful, UUID instanceId, List<UUID> reserved,
                                    List<UUID> conflicts, String detail) {
        public ReservationResult {
            reserved = List.copyOf(reserved);
            conflicts = List.copyOf(conflicts);
        }
    }
}
