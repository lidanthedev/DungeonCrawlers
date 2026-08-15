package me.lidan.dungeonCrawlers.core.protection;

import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** One-shot, destination-scoped permits for plugin-owned teleports. */
public final class TeleportPermitService {
    private final Map<UUID, Permit> permits = new HashMap<>();

    public synchronized void authorize(UUID playerId, Set<Destination> destinations, Instant expiresAt) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(destinations, "destinations");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (destinations.isEmpty()) throw new IllegalArgumentException("teleport permit needs a destination");
        permits.put(playerId, new Permit(Set.copyOf(destinations), expiresAt));
    }

    public synchronized boolean consume(UUID playerId, String world, Point target, Instant now) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(world, "world"); Objects.requireNonNull(target, "target");
        Objects.requireNonNull(now, "now");
        Permit permit = permits.get(playerId);
        if (permit == null) return false;
        if (!now.isBefore(permit.expiresAt())) {
            permits.remove(playerId);
            return false;
        }
        if (!permit.destinations().contains(new Destination(world, target))) return false;
        permits.remove(playerId);
        return true;
    }

    public synchronized void revoke(UUID playerId) { permits.remove(Objects.requireNonNull(playerId, "playerId")); }

    public synchronized int size() { return permits.size(); }

    private record Permit(Set<Destination> destinations, Instant expiresAt) { }

    public record Destination(String world, Point target) {
        public Destination {
            Objects.requireNonNull(world); Objects.requireNonNull(target);
            if (world.isBlank()) throw new IllegalArgumentException("destination world must not be blank");
        }
    }
}
