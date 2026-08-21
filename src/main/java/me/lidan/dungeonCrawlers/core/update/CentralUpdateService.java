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
    private final Map<UUID, List<Consumer<Instant>>> updates = new LinkedHashMap<>();

    public CentralUpdateService(Clock clock, Consumer<String> diagnostics) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    public synchronized boolean register(UUID instanceId, Consumer<Instant> update) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(update, "update");
        if (updates.containsKey(instanceId)) return false;
        updates.put(instanceId, new ArrayList<>(List.of(update)));
        return true;
    }

    /** Adds a callback to an existing instance without replacing its primary update. */
    public synchronized boolean registerSupplemental(UUID instanceId, Consumer<Instant> update) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(update, "update");
        List<Consumer<Instant>> callbacks = updates.get(instanceId);
        if (callbacks == null) return false;
        callbacks.add(update);
        return true;
    }

    /** Removes one supplemental callback while retaining the instance's primary update. */
    public synchronized boolean removeSupplemental(UUID instanceId, Consumer<Instant> update) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(update, "update");
        List<Consumer<Instant>> callbacks = updates.get(instanceId);
        return callbacks != null && callbacks.size() > 1 && callbacks.remove(update);
    }

    public synchronized boolean remove(UUID instanceId) {
        return updates.remove(Objects.requireNonNull(instanceId, "instanceId")) != null;
    }

    public TickReport tick() {
        return tick(clock.instant());
    }

    public TickReport tick(Instant now) {
        Objects.requireNonNull(now, "now");
        Map<UUID, List<Consumer<Instant>>> snapshot;
        synchronized (this) {
            snapshot = new LinkedHashMap<>();
            updates.forEach((instanceId, callbacks) -> snapshot.put(instanceId, List.copyOf(callbacks)));
        }
        List<UUID> failures = new ArrayList<>();
        for (Map.Entry<UUID, List<Consumer<Instant>>> entry : snapshot.entrySet()) {
            boolean failed = false;
            for (Consumer<Instant> callback : entry.getValue()) {
                try {
                    callback.accept(now);
                } catch (RuntimeException exception) {
                    failed = true;
                    try {
                        diagnostics.accept("instance=" + entry.getKey() + " central update failed: "
                                + message(exception));
                    } catch (RuntimeException ignored) {
                        // Diagnostics are best-effort; one consumer must not stop the update loop.
                    }
                }
            }
            if (failed) failures.add(entry.getKey());
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
