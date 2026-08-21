package me.lidan.dungeonCrawlers.core.lifecycle;

import me.lidan.dungeonCrawlers.core.update.CentralUpdateService;
import org.bukkit.Bukkit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Pure running-player lifecycle for alive, ghost, logout, escape, and wipe state. */
public final class PlayerLifecycleService {
    public static final Duration REVIVE_DURATION = Duration.ofSeconds(60);
    public static final Duration ADMIN_REVIVE_DURATION = Duration.ofSeconds(3);

    private final CentralUpdateService updates;
    private final Clock clock;
    private final Consumer<Notice> notices;
    private final Map<UUID, MutableInstance> instances = new LinkedHashMap<>();

    public PlayerLifecycleService(CentralUpdateService updates, Clock clock, Consumer<Notice> notices) {
        this.updates = Objects.requireNonNull(updates, "updates");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.notices = Objects.requireNonNull(notices, "notices");
    }

    public synchronized RegistrationResult register(UUID instanceId, Collection<UUID> participants) {
        UUID id = Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(participants, "participants");
        List<UUID> ordered = participants.stream().map(value -> Objects.requireNonNull(value, "participant"))
                .distinct().sorted().toList();
        if (ordered.isEmpty()) return RegistrationResult.failure("lifecycle requires at least one participant");
        if (instances.containsKey(id)) return RegistrationResult.failure("lifecycle already registered");
        MutableInstance state = new MutableInstance(id, ordered);
        state.tick = now -> tick(id, now);
        if (!updates.registerSupplemental(id, state.tick)) {
            return RegistrationResult.failure("central update is not registered for instance");
        }
        instances.put(id, state);
        return RegistrationResult.success("lifecycle registered", snapshot(state));
    }

    public synchronized TransitionResult start(UUID instanceId) {
        MutableInstance state = instance(instanceId);
        if (state == null) return TransitionResult.failure("unknown lifecycle instance");
        if (state.running) return TransitionResult.success(Event.STARTED, "lifecycle already running", snapshot(state));
        if (state.wiped) return TransitionResult.failure("lifecycle is already wiped");
        state.running = true;
        return TransitionResult.success(Event.STARTED, "lifecycle running", snapshot(state));
    }

    public synchronized TransitionResult lethal(UUID instanceId, UUID playerId) {
        return lethal(instanceId, playerId, clock.instant());
    }

    public synchronized TransitionResult lethal(UUID instanceId, UUID playerId, Instant now) {
        Objects.requireNonNull(now, "now");
        MutableInstance state = instance(instanceId);
        MutablePlayer player = state == null ? null : state.players.get(Objects.requireNonNull(playerId, "playerId"));
        if (state == null) return TransitionResult.failure("unknown lifecycle instance");
        if (player == null) return TransitionResult.failure("player is not a participant");
        if (!state.running) return TransitionResult.failure("lifecycle is not running");
        if (state.wiped) return TransitionResult.failure("instance is wiped");
        if (player.state != PlayerState.ALIVE) {
            return TransitionResult.failure("player is already " + player.state.name().toLowerCase());
        }
        player.state = PlayerState.GHOST;
        player.reviveAt = now.plus(REVIVE_DURATION);
        player.lastTarget = null;
        player.lastCountdownSeconds = REVIVE_DURATION.toSeconds();
        Notice ghost = new Notice(state.instanceId, player.id, Event.GHOSTED,
                "Reviving in " + REVIVE_DURATION.toSeconds() + " seconds", player.reviveAt, null);
        if (noOnlineAlive(state)) return wipe(state, "no online active alive player remains", ghost);
        emit(ghost);
        return TransitionResult.success(Event.GHOSTED, ghost.detail(), snapshot(state), player.id);
    }

    public synchronized TransitionResult disconnect(UUID instanceId, UUID playerId) {
        MutableInstance state = instance(instanceId);
        MutablePlayer player = state == null ? null : state.players.get(Objects.requireNonNull(playerId, "playerId"));
        if (state == null) return TransitionResult.failure("unknown lifecycle instance");
        if (player == null) return TransitionResult.failure("player is not a participant");
        if (!player.online) return TransitionResult.success(Event.DISCONNECTED, "player already offline", snapshot(state));
        player.online = false;
        if (state.running && noOnlineAlive(state)) return wipe(state, "no online active alive player remains", null);
        return TransitionResult.success(Event.DISCONNECTED, "player disconnected", snapshot(state), player.id);
    }

    public synchronized TransitionResult reconnect(UUID instanceId, UUID playerId) {
        MutableInstance state = instance(instanceId);
        MutablePlayer player = state == null ? null : state.players.get(Objects.requireNonNull(playerId, "playerId"));
        if (state == null) return TransitionResult.failure("unknown lifecycle instance");
        if (player == null) return TransitionResult.failure("player is not a participant");
        if (state.wiped) return TransitionResult.failure("instance is wiped");
        if (player.state == PlayerState.REMOVED) return TransitionResult.failure("player was removed from instance");
        player.online = true;
        Instant now = clock.instant();
        if (player.state == PlayerState.GHOST && player.reviveAt != null && !now.isBefore(player.reviveAt)) {
            return revive(state, player, now, "revive timer elapsed while offline");
        }
        if (player.state == PlayerState.GHOST && player.reviveAt != null) {
            player.lastCountdownSeconds = -1;
            emitCountdown(state, player, now, Event.RECONNECTED);
        }
        return TransitionResult.success(Event.RECONNECTED, "player reconnected", snapshot(state), player.id);
    }

    public synchronized TransitionResult escape(UUID instanceId, UUID playerId) {
        MutableInstance state = instance(instanceId);
        MutablePlayer player = state == null ? null : state.players.get(Objects.requireNonNull(playerId, "playerId"));
        if (state == null) return TransitionResult.failure("unknown lifecycle instance");
        if (player == null) return TransitionResult.failure("player is not a participant");
        if (player.state == PlayerState.REMOVED) return TransitionResult.failure("player is already removed");
        player.state = PlayerState.REMOVED;
        player.reviveAt = null;
        player.lastTarget = null;
        Notice notice = new Notice(state.instanceId, player.id, Event.REMOVED,
                "player escaped and was removed from the run", null, null);
        emit(notice);
        if (state.running && noOnlineAlive(state)) return wipe(state, "no online active alive player remains", notice);
        return TransitionResult.success(Event.REMOVED, notice.detail(), snapshot(state), player.id);
    }

    public synchronized TransitionResult revive(UUID instanceId, UUID playerId) {
        MutableInstance state = instance(instanceId);
        MutablePlayer player = state == null ? null : state.players.get(Objects.requireNonNull(playerId, "playerId"));
        if (state == null) return TransitionResult.failure("unknown lifecycle instance");
        if (player == null) return TransitionResult.failure("player is not a participant");
        return revive(state, player, clock.instant(), "player revived");
    }

    /** Schedules an administrative revive without bypassing the normal ghost countdown. */
    public synchronized TransitionResult scheduleAdminRevive(UUID instanceId, UUID playerId) {
        MutableInstance state = instance(instanceId);
        MutablePlayer player = state == null ? null : state.players.get(Objects.requireNonNull(playerId, "playerId"));
        if (state == null) return TransitionResult.failure("unknown lifecycle instance");
        if (player == null) return TransitionResult.failure("player is not a participant");
        if (state.wiped || !state.running) return TransitionResult.failure("instance is not running");
        if (player.state != PlayerState.GHOST) return TransitionResult.failure("player is not a ghost");

        Instant now = clock.instant();
        player.reviveAt = now.plus(ADMIN_REVIVE_DURATION);
        player.lastCountdownSeconds = -1;
        emitCountdown(state, player, now, Event.GHOST_COUNTDOWN);
        return TransitionResult.success(Event.GHOST_COUNTDOWN,
                "revive scheduled in " + ADMIN_REVIVE_DURATION.toSeconds() + " seconds",
                snapshot(state), player.id);
    }

    public synchronized TransitionResult remove(UUID instanceId, UUID playerId) {
        return escape(instanceId, playerId);
    }

    public synchronized TransitionResult wipe(UUID instanceId, String reason) {
        MutableInstance state = instance(instanceId);
        if (state == null) return TransitionResult.failure("unknown lifecycle instance");
        return wipe(state, Objects.requireNonNull(reason, "reason"), null);
    }

    public synchronized Optional<InstanceSnapshot> info(UUID instanceId) {
        MutableInstance state = instance(instanceId);
        return state == null ? Optional.empty() : Optional.of(snapshot(state));
    }

    public synchronized Optional<PlayerSnapshot> player(UUID instanceId, UUID playerId) {
        MutableInstance state = instance(instanceId);
        if (state == null) return Optional.empty();
        MutablePlayer player = state.players.get(Objects.requireNonNull(playerId, "playerId"));
        return player == null ? Optional.empty() : Optional.of(snapshot(player));
    }

    public synchronized List<InstanceSnapshot> instances() {
        return instances.values().stream().map(this::snapshot)
                .sorted(Comparator.comparing(InstanceSnapshot::instanceId)).toList();
    }

    public synchronized boolean cleanup(UUID instanceId) {
        MutableInstance state = instances.remove(Objects.requireNonNull(instanceId, "instanceId"));
        if (state == null) return false;
        updates.removeSupplemental(instanceId, state.tick);
        return true;
    }

    public synchronized void cleanupAll() {
        new ArrayList<>(instances.keySet()).forEach(this::cleanup);
    }

    private void tick(UUID instanceId, Instant now) {
        MutableInstance state = instances.get(instanceId);
        if (state == null || !state.running || state.wiped) return;
        for (MutablePlayer player : state.players.values()) {
            if (!player.online || player.state != PlayerState.GHOST || player.reviveAt == null) continue;
            if (!now.isBefore(player.reviveAt)) {
                revive(state, player, now, "revive timer elapsed");
            } else {
                emitCountdown(state, player, now, Event.GHOST_COUNTDOWN);
            }
        }
    }

    private TransitionResult revive(MutableInstance state, MutablePlayer player, Instant now, String detail) {
        if (player.state != PlayerState.GHOST) {
            return TransitionResult.failure("player is not a ghost");
        }
        if (state.wiped || !state.running) return TransitionResult.failure("instance is not running");
        UUID target = state.players.values().stream()
                .filter(candidate -> candidate.state == PlayerState.ALIVE && candidate.online)
                .map(candidate -> candidate.id).findFirst().orElse(null);
        if (target == null) {
            return TransitionResult.failure("no online alive participant is available for revive");
        }
        player.state = PlayerState.ALIVE;
        player.reviveAt = null;
        player.lastTarget = target;
        player.lastCountdownSeconds = -1;
        Notice notice = new Notice(state.instanceId, player.id, Event.REVIVED, detail, now, target);
        emit(notice);
        return TransitionResult.success(Event.REVIVED, detail, snapshot(state), player.id, target);
    }

    private void emitCountdown(MutableInstance state, MutablePlayer player, Instant now, Event event) {
        long remaining = secondsRemaining(now, player.reviveAt);
        if (remaining <= 0 || remaining == player.lastCountdownSeconds) return;
        player.lastCountdownSeconds = remaining;
        emit(new Notice(state.instanceId, player.id, event,
                "Reviving in " + remaining + (remaining == 1 ? " second" : " seconds"),
                player.reviveAt, null));
    }

    private static long secondsRemaining(Instant now, Instant deadline) {
        Duration remaining = Duration.between(now, deadline);
        if (remaining.isZero() || remaining.isNegative()) return 0;
        long seconds = remaining.getSeconds();
        return remaining.getNano() == 0 ? seconds : seconds + 1;
    }

    private TransitionResult wipe(MutableInstance state, String reason, Notice prior) {
        if (!state.wiped) {
            state.wiped = true;
            state.detail = reason;
            Notice notice = new Notice(state.instanceId, null, Event.WIPED, reason, null, null);
            emit(notice);
        }
        return TransitionResult.success(Event.WIPED, reason, snapshot(state),
                prior == null ? null : prior.playerId());
    }

    private boolean noOnlineAlive(MutableInstance state) {
        return state.players.values().stream().noneMatch(player ->
                player.state == PlayerState.ALIVE && player.online);
    }

    private MutableInstance instance(UUID instanceId) {
        return instances.get(Objects.requireNonNull(instanceId, "instanceId"));
    }

    private void emit(Notice notice) {
        try {
            notices.accept(notice);
        } catch (RuntimeException ignored) {
            // Player-facing lifecycle notifications must not corrupt state transitions.
        }
    }

    private InstanceSnapshot snapshot(MutableInstance state) {
        return new InstanceSnapshot(state.instanceId, state.running, state.wiped, state.detail,
                state.players.values().stream().map(PlayerLifecycleService::snapshot).toList());
    }

    private static PlayerSnapshot snapshot(MutablePlayer player) {
        return new PlayerSnapshot(player.id, player.state, player.online, player.reviveAt, player.lastTarget);
    }

    private static final class MutableInstance {
        private final UUID instanceId;
        private final Map<UUID, MutablePlayer> players = new LinkedHashMap<>();
        private Consumer<Instant> tick;
        private boolean running;
        private boolean wiped;
        private String detail = "lifecycle registered";

        private MutableInstance(UUID instanceId, List<UUID> participants) {
            this.instanceId = instanceId;
            participants.forEach(id -> players.put(id, new MutablePlayer(id)));
        }
    }

    private static final class MutablePlayer {
        private final UUID id;
        private PlayerState state = PlayerState.ALIVE;
        private boolean online = true;
        private Instant reviveAt;
        private UUID lastTarget;
        private long lastCountdownSeconds = -1;

        private MutablePlayer(UUID id) { this.id = id; }
    }

    public enum PlayerState { ALIVE, GHOST, REMOVED }

    public enum Event {
        STARTED, GHOSTED, GHOST_COUNTDOWN, DISCONNECTED, RECONNECTED, REVIVED, REMOVED, WIPED
    }

    public record PlayerSnapshot(UUID playerId, PlayerState state, boolean online,
                                 Instant reviveAt, UUID reviveTarget) {
        public PlayerSnapshot {
            Objects.requireNonNull(playerId); Objects.requireNonNull(state);
        }
    }

    public record InstanceSnapshot(UUID instanceId, boolean running, boolean wiped, String detail,
                                   List<PlayerSnapshot> players) {
        public InstanceSnapshot {
            Objects.requireNonNull(instanceId); Objects.requireNonNull(detail); players = List.copyOf(players);
        }
    }

    public record Notice(UUID instanceId, UUID playerId, Event event, String detail,
                         Instant reviveAt, UUID reviveTarget) {
        public Notice {
            Objects.requireNonNull(instanceId); Objects.requireNonNull(event); Objects.requireNonNull(detail);
        }
    }

    public record RegistrationResult(boolean successful, String detail, InstanceSnapshot snapshot) {
        public RegistrationResult { Objects.requireNonNull(detail); }
        public static RegistrationResult success(String detail, InstanceSnapshot snapshot) {
            return new RegistrationResult(true, detail, snapshot);
        }
        public static RegistrationResult failure(String detail) { return new RegistrationResult(false, detail, null); }
    }

    public record TransitionResult(boolean successful, Event event, String detail,
                                   InstanceSnapshot snapshot, UUID playerId, UUID reviveTarget) {
        public TransitionResult { Objects.requireNonNull(detail); }
        public static TransitionResult success(Event event, String detail, InstanceSnapshot snapshot) {
            return new TransitionResult(true, event, detail, snapshot, null, null);
        }
        public static TransitionResult success(Event event, String detail, InstanceSnapshot snapshot, UUID playerId) {
            return new TransitionResult(true, event, detail, snapshot, playerId, null);
        }
        public static TransitionResult success(Event event, String detail, InstanceSnapshot snapshot,
                                               UUID playerId, UUID reviveTarget) {
            return new TransitionResult(true, event, detail, snapshot, playerId, reviveTarget);
        }
        public static TransitionResult failure(String detail) {
            return new TransitionResult(false, null, detail, null, null, null);
        }
    }
}
