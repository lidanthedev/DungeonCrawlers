package me.lidan.dungeonCrawlers.core.update;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** One clock-driven update loop shared by all active instances. */
public final class CentralUpdateService {
    public static final long MIN_TIME_SCALE = 1L;
    public static final long MAX_TIME_SCALE = 3_600L;

    private final Clock clock;
    private final Consumer<String> diagnostics;
    private final Map<UUID, List<Consumer<Instant>>> updates = new LinkedHashMap<>();
    private final Map<UUID, Duration> instanceTimeOffsets = new LinkedHashMap<>();
    private boolean frozen;
    private int activeTicks;
    private long timeScale = MIN_TIME_SCALE;
    private Instant scaleRealAnchor;
    private Instant scaleVirtualAnchor;

    public CentralUpdateService(Clock clock, Consumer<String> diagnostics) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    public synchronized boolean register(UUID instanceId, Consumer<Instant> update) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(update, "update");
        if (frozen) return false;
        if (updates.containsKey(instanceId)) return false;
        resetTimeControlsIfIdle();
        instanceTimeOffsets.remove(instanceId);
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
        if (callbacks == null) return false;
        int index = callbacks.indexOf(update);
        return index >= 1 && callbacks.remove(index) != null;
    }

    public synchronized boolean remove(UUID instanceId) {
        UUID id = Objects.requireNonNull(instanceId, "instanceId");
        boolean removed = updates.remove(id) != null;
        instanceTimeOffsets.remove(id);
        resetTimeControlsIfIdle();
        return removed;
    }

    public TickReport tick() {
        Map<UUID, List<Consumer<Instant>>> snapshot;
        Map<UUID, Instant> callbackTimes = new LinkedHashMap<>();
        Instant baseNow;
        synchronized (this) {
            baseNow = schedulerNow(clock.instant());
            if (frozen) return new TickReport(baseNow, 0, List.of());
            snapshot = new LinkedHashMap<>();
            updates.forEach((instanceId, callbacks) -> {
                snapshot.put(instanceId, List.copyOf(callbacks));
                callbackTimes.put(instanceId, baseNow.plus(instanceTimeOffsets
                        .getOrDefault(instanceId, Duration.ZERO)));
            });
            activeTicks++;
        }
        return dispatch(snapshot, callbackTimes, baseNow);
    }

    /** Returns the base scheduler timestamp after applying the optional admin test speed. */
    private synchronized Instant schedulerNow(Instant realNow) {
        return scaledNow(realNow);
    }

    private synchronized Instant scaledNow(Instant realNow) {
        if (scaleRealAnchor == null || scaleVirtualAnchor == null) {
            scaleRealAnchor = realNow;
            scaleVirtualAnchor = realNow;
        }
        return scaleVirtualAnchor.plus(Duration.between(scaleRealAnchor, realNow).multipliedBy(timeScale));
    }

    /** Changes the scheduler's virtual elapsed-time rate for an admin test. */
    public synchronized void setTimeScale(long multiplier) {
        if (multiplier < MIN_TIME_SCALE || multiplier > MAX_TIME_SCALE) {
            throw new IllegalArgumentException("time scale must be in " + MIN_TIME_SCALE + ".." + MAX_TIME_SCALE);
        }
        Instant realNow = clock.instant();
        Instant virtualNow = scaledNow(realNow);
        timeScale = multiplier;
        scaleRealAnchor = realNow;
        scaleVirtualAnchor = virtualNow;
    }

    /** Restores normal one-to-one scheduler time after an admin test. */
    public synchronized void resetTimeScale() {
        Instant realNow = clock.instant();
        Instant virtualNow = scaledNow(realNow);
        timeScale = MIN_TIME_SCALE;
        scaleRealAnchor = realNow;
        scaleVirtualAnchor = virtualNow;
    }

    public synchronized long timeScale() {
        return timeScale;
    }

    /** Advances one instance's scheduler timeline and dispatches only that instance. */
    public TickReport advanceInstanceTime(UUID instanceId, Duration amount) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(amount, "amount");
        if (amount.isNegative()) throw new IllegalArgumentException("time advance must not be negative");
        Map<UUID, List<Consumer<Instant>>> snapshot;
        Instant advanced;
        synchronized (this) {
            Instant baseNow = schedulerNow(clock.instant());
            if (frozen) return new TickReport(baseNow, 0, List.of());
            List<Consumer<Instant>> callbacks = updates.get(instanceId);
            if (callbacks == null) return new TickReport(baseNow, 0, List.of());
            Duration offset = instanceTimeOffsets.getOrDefault(instanceId, Duration.ZERO).plus(amount);
            instanceTimeOffsets.put(instanceId, offset);
            advanced = baseNow.plus(offset);
            snapshot = new LinkedHashMap<>();
            snapshot.put(instanceId, List.copyOf(callbacks));
            activeTicks++;
        }
        return dispatch(snapshot, Map.of(instanceId, advanced), advanced);
    }

    /** Returns the current scheduler time and test offset for one active instance. */
    public synchronized Optional<TimeSnapshot> time(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        if (!updates.containsKey(instanceId)) return Optional.empty();
        Instant realNow = clock.instant();
        Duration offset = instanceTimeOffsets.getOrDefault(instanceId, Duration.ZERO);
        return Optional.of(new TimeSnapshot(instanceId, realNow, schedulerNow(realNow).plus(offset),
                timeScale, offset));
    }

    public TickReport tick(Instant now) {
        Objects.requireNonNull(now, "now");
        Map<UUID, List<Consumer<Instant>>> snapshot;
        synchronized (this) {
            if (frozen) return new TickReport(now, 0, List.of());
            snapshot = new LinkedHashMap<>();
            updates.forEach((instanceId, callbacks) -> snapshot.put(instanceId, List.copyOf(callbacks)));
            activeTicks++;
        }
        Map<UUID, Instant> callbackTimes = new LinkedHashMap<>();
        snapshot.keySet().forEach(instanceId -> callbackTimes.put(instanceId, now));
        return dispatch(snapshot, callbackTimes, now);
    }

    private TickReport dispatch(Map<UUID, List<Consumer<Instant>>> snapshot,
                                Map<UUID, Instant> callbackTimes, Instant reportNow) {
        try {
            List<UUID> failures = new ArrayList<>();
            for (Map.Entry<UUID, List<Consumer<Instant>>> entry : snapshot.entrySet()) {
                boolean failed = false;
                Instant now = callbackTimes.get(entry.getKey());
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
            return new TickReport(reportNow, snapshot.size(), failures);
        } finally {
            synchronized (this) {
                activeTicks--;
                if (activeTicks == 0) notifyAll();
            }
        }
    }

    public synchronized Set<UUID> registeredInstances() {
        return Set.copyOf(updates.keySet());
    }

    public synchronized int size() {
        return updates.size();
    }

    public synchronized int callbackCount(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        List<Consumer<Instant>> callbacks = updates.get(instanceId);
        return callbacks == null ? 0 : callbacks.size();
    }

    public synchronized void clear() {
        updates.clear();
        instanceTimeOffsets.clear();
        resetTimeControlsIfIdle();
    }

    private void resetTimeControlsIfIdle() {
        if (!updates.isEmpty()) return;
        timeScale = MIN_TIME_SCALE;
        instanceTimeOffsets.clear();
        Instant realNow = clock.instant();
        scaleRealAnchor = realNow;
        scaleVirtualAnchor = realNow;
    }

    /** Stops callbacks before plugin-owned services are restored or torn down during disable. */
    public void freeze() {
        boolean interrupted = false;
        synchronized (this) {
            frozen = true;
            while (activeTicks > 0) {
                try {
                    wait();
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    public synchronized boolean frozen() {
        return frozen;
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

    public record TimeSnapshot(UUID instanceId, Instant realNow, Instant schedulerNow,
                               long timeScale, Duration instanceTimeOffset) {
        public TimeSnapshot {
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(realNow, "realNow");
            Objects.requireNonNull(schedulerNow, "schedulerNow");
            Objects.requireNonNull(instanceTimeOffset, "instanceTimeOffset");
            if (timeScale < MIN_TIME_SCALE || timeScale > MAX_TIME_SCALE) {
                throw new IllegalArgumentException("timeScale is outside the supported range");
            }
        }
    }
}
