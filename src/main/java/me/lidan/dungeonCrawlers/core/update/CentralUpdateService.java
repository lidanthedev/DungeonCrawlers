package me.lidan.dungeonCrawlers.core.update;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** One clock-driven update loop shared by all active instances. */
public final class CentralUpdateService {
    private final Clock clock;
    private final Consumer<String> diagnostics;
    private final Map<UUID, Consumer<Instant>> updates = new LinkedHashMap<>();

    public CentralUpdateService(Clock clock, Consumer<String> diagnostics) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    public synchronized boolean register(UUID instanceId, Consumer<Instant> update) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(update, "update");
        if (updates.containsKey(instanceId)) return false;
        updates.put(instanceId, update);
        return true;
    }

    public synchronized boolean remove(UUID instanceId) {
        return updates.remove(Objects.requireNonNull(instanceId, "instanceId")) != null;
    }

    public TickReport tick() {
        return tick(clock.instant());
    }

    public TickReport tick(Instant now) {
        Objects.requireNonNull(now, "now");
        List<Map.Entry<UUID, Consumer<Instant>>> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(updates.entrySet());
        }
        List<UUID> failures = new ArrayList<>();
        for (Map.Entry<UUID, Consumer<Instant>> entry : snapshot) {
            try {
                entry.getValue().accept(now);
            } catch (RuntimeException exception) {
                failures.add(entry.getKey());
                try {
                    diagnostics.accept("instance=" + entry.getKey() + " central update failed: "
                            + message(exception));
                } catch (RuntimeException ignored) {
                    // Diagnostics are best-effort; one consumer must not stop the update loop.
                }
            }
        }
        return new TickReport(now, snapshot.size(), failures);
    }

    public synchronized Set<UUID> registeredInstances() {
        return Set.copyOf(updates.keySet());
    }

    public synchronized int size() {
        return updates.size();
    }

    public synchronized void clear() {
        updates.clear();
    }

    private static String message(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    public record TickReport(Instant now, int attempted, List<UUID> failures) {
        public TickReport {
            Objects.requireNonNull(now, "now");
            failures = List.copyOf(failures);
            if (attempted < 0) throw new IllegalArgumentException("attempted must not be negative");
        }

        public boolean successful() {
            return failures.isEmpty();
        }
    }
}
