package me.lidan.dungeonCrawlers.core.chunk;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** All-or-nothing bounded chunk-ticket accounting per dungeon instance. */
public final class ChunkTicketBudget {
    private final int maximumPerInstance;
    private final int maximumTotal;
    private final Map<UUID, Set<ChunkKey>> tickets = new LinkedHashMap<>();

    public ChunkTicketBudget(int maximumPerInstance, int maximumTotal) {
        if (maximumPerInstance < 1 || maximumTotal < maximumPerInstance) {
            throw new IllegalArgumentException("invalid chunk-ticket limits");
        }
        this.maximumPerInstance = maximumPerInstance;
        this.maximumTotal = maximumTotal;
    }

    public synchronized boolean acquire(UUID instanceId, Collection<ChunkKey> requested) {
        Objects.requireNonNull(instanceId, "instanceId");
        Set<ChunkKey> requestedSet = normalized(requested);
        Set<ChunkKey> existing = tickets.computeIfAbsent(instanceId, ignored -> new LinkedHashSet<>());
        Set<ChunkKey> additions = new LinkedHashSet<>(requestedSet);
        additions.removeAll(existing);
        if (existing.size() + additions.size() > maximumPerInstance
                || totalCount() + additions.size() > maximumTotal) {
            if (existing.isEmpty()) tickets.remove(instanceId);
            return false;
        }
        existing.addAll(additions);
        return true;
    }

    public synchronized int release(UUID instanceId, Collection<ChunkKey> requested) {
        Objects.requireNonNull(instanceId, "instanceId");
        Set<ChunkKey> existing = tickets.get(instanceId);
        if (existing == null) return 0;
        int before = existing.size();
        existing.removeAll(normalized(requested));
        if (existing.isEmpty()) tickets.remove(instanceId);
        return before - existing.size();
    }

    public synchronized int releaseAll(UUID instanceId) {
        Set<ChunkKey> removed = tickets.remove(Objects.requireNonNull(instanceId, "instanceId"));
        return removed == null ? 0 : removed.size();
    }

    public synchronized Set<ChunkKey> tickets(UUID instanceId) {
        return Set.copyOf(tickets.getOrDefault(Objects.requireNonNull(instanceId, "instanceId"), Set.of()));
    }

    public synchronized int totalCount() {
        return tickets.values().stream().mapToInt(Set::size).sum();
    }

    public int maximumPerInstance() { return maximumPerInstance; }

    public int maximumTotal() { return maximumTotal; }

    private static Set<ChunkKey> normalized(Collection<ChunkKey> requested) {
        Objects.requireNonNull(requested, "requested");
        LinkedHashSet<ChunkKey> result = new LinkedHashSet<>();
        requested.forEach(value -> result.add(Objects.requireNonNull(value, "chunk key")));
        return result;
    }

    public record ChunkKey(int x, int z) { }
}
