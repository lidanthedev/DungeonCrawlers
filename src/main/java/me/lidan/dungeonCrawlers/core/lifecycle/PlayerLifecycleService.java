package me.lidan.dungeonCrawlers.core.lifecycle;

import me.lidan.dungeonCrawlers.config.DungeonTimings;
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
    private static final DungeonTimings DEFAULT_TIMINGS = DungeonTimings.defaults();
    public static final Duration REVIVE_DURATION = DEFAULT_TIMINGS.reviveDuration();
    public static final Duration ADMIN_REVIVE_DURATION = DEFAULT_TIMINGS.adminReviveDuration();

    private final CentralUpdateService updates;
    private final Clock clock;
    private final Consumer<Notice> notices;
    private final DungeonTimings timings;
    private final Map<UUID, MutableInstance> instances = new LinkedHashMap<>();
    private boolean frozen;
    private java.util.function.Predicate<UUID> runicPetActive = ignored -> false;
    private java.util.function.Predicate<Notice> revivalEffect = ignored -> true;

    public synchronized void configureRevival(java.util.function.Predicate<UUID> activePet,
                                               java.util.function.Predicate<Notice> revivalEffect) {
        this.runicPetActive = Objects.requireNonNull(activePet);
        this.revivalEffect = Objects.requireNonNull(revivalEffect);
    }

    public synchronized void configureOrdinaryRevival(UUID instanceId, boolean allowed) {
        MutableInstance state = instance(instanceId);
        if (state == null || state.running) throw new IllegalStateException("revival policy requires a preparing instance");
        state.ordinaryRevival = allowed;
    }

    public PlayerLifecycleService(CentralUpdateService updates, Clock clock, Consumer<Notice> notices) {
        this(updates, clock, notices, DEFAULT_TIMINGS);
    }

    public PlayerLifecycleService(CentralUpdateService updates, Clock clock, Consumer<Notice> notices,
                                  DungeonTimings timings) {
        this.updates = Objects.requireNonNull(updates, "updates");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.notices = Objects.requireNonNull(notices, "notices");
        this.timings = Objects.requireNonNull(timings, "timings");
    }

    public synchronized RegistrationResult register(UUID instanceId, Collection<UUID> participants) {
        UUID id = Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(participants, "participants");
        if (frozen) return RegistrationResult.failure("player lifecycle is frozen for plugin disable");
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
        if (state.completed) return TransitionResult.failure("lifecycle is already completed");
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
        return transitionToGhost(state, player, now, true);
    }

    public synchronized TransitionResult disconnect(UUID instanceId, UUID playerId) {
        return disconnect(instanceId, playerId, true);
    }

    /** Marks a player offline, optionally applying the active-run disconnect-to-ghost transition. */
    public synchronized TransitionResult disconnect(UUID instanceId, UUID playerId, boolean ghostOnDisconnect) {
        MutableInstance state = instance(instanceId);
        MutablePlayer player = state == null ? null : state.players.get(Objects.requireNonNull(playerId, "playerId"));
        if (state == null) return TransitionResult.failure("unknown lifecycle instance");
        if (player == null) return TransitionResult.failure("player is not a participant");
        if (!player.online) return TransitionResult.success(Event.DISCONNECTED, "player already offline", snapshot(state));
        player.online = false;
        if (ghostOnDisconnect && state.running && player.state == PlayerState.ALIVE) {
            TransitionResult ghost = transitionToGhost(state, player, clock.instant(), false);
            if (ghost.event() == Event.WIPED) return ghost;
            return TransitionResult.success(Event.DISCONNECTED,
                    "player disconnected and became a ghost", ghost.snapshot(), player.id);
        }
        if (ghostOnDisconnect && state.running && noOnlineAlive(state)) {
            return wipe(state, "no online active alive player remains", null);
        }
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
        if (state.completed && player.state == PlayerState.GHOST) return reviveAfterCompletion(state, player);
        Instant now = clock.instant();
        if (player.state == PlayerState.GHOST && player.reviveAt != null && !now.isBefore(player.reviveAt)) {
            TransitionResult revived = revive(state, player, now, "revive timer elapsed while offline");
            if (revived.successful()) return revived;
            return TransitionResult.success(Event.RECONNECTED,
                    "player reconnected; revive pending: " + revived.detail(), snapshot(state), player.id);
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

    /** Ends combat and restores ghosts for reward access, retaining their scoring deaths. */
    public synchronized TransitionResult complete(UUID instanceId) {
        MutableInstance state = instance(instanceId);
        if (state == null) return TransitionResult.failure("unknown lifecycle instance");
        if (state.wiped) return TransitionResult.failure("instance is wiped");
        state.completed = true;
        state.running = false;
        for (MutablePlayer player : state.players.values()) {
            if (player.state != PlayerState.GHOST) continue;
            player.reviveAt = null;
            player.reviveKind = ReviveKind.NONE;
            if (player.online) {
                TransitionResult result = reviveAfterCompletion(state, player);
                if (!result.successful()) return result;
            }
        }
        return TransitionResult.success(Event.COMPLETED, "all online ghosts revived for rewards", snapshot(state));
    }

    private TransitionResult reviveAfterCompletion(MutableInstance state, MutablePlayer player) {
        Notice notice = new Notice(state.instanceId, player.id, Event.REVIVED,
                "Dungeon completed; revived for rewards", clock.instant(), null);
        try {
            if (!revivalEffect.test(notice)) return TransitionResult.failure("completion revival could not be applied");
        } catch (RuntimeException failure) {
            return TransitionResult.failure("completion revival failed");
        }
        player.state = PlayerState.ALIVE;
        player.reviveAt = null;
        player.reviveKind = ReviveKind.NONE;
        player.lastTarget = null;
        player.lastCountdownSeconds = -1;
        emit(notice);
        return TransitionResult.success(Event.REVIVED, notice.detail(), snapshot(state), player.id);
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
        player.reviveKind = ReviveKind.ADMIN;
        player.reviveAt = now.plus(timings.adminReviveDuration());
        player.lastCountdownSeconds = -1;
        emitCountdown(state, player, now, Event.GHOST_COUNTDOWN);
        return TransitionResult.success(Event.GHOST_COUNTDOWN,
                "revive scheduled in " + timings.adminReviveDuration().toSeconds() + " seconds",
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

    /** Prevents late lifecycle ticks while online players are restored during disable. */
    public synchronized void freezeForDisable() {
        frozen = true;
    }

    private synchronized void tick(UUID instanceId, Instant now) {
        MutableInstance state = instances.get(instanceId);
        if (frozen || state == null || !state.running || state.wiped) return;
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
        boolean runic = player.reviveKind == ReviveKind.RUNIC;
        if (!player.online) return TransitionResult.failure("player is offline");
        if (player.reviveKind == ReviveKind.NONE) return TransitionResult.failure("ordinary revival is disabled");
        if (runic && player.reviveAt != null && now.isBefore(player.reviveAt)) {
            return TransitionResult.failure("Runic revival countdown has not elapsed");
        }
        UUID target = runic ? null : state.players.values().stream()
                .filter(candidate -> candidate.state == PlayerState.ALIVE && candidate.online)
                .map(candidate -> candidate.id).findFirst().orElse(null);
        if (!runic && target == null) {
            return TransitionResult.failure("no online alive participant is available for revive");
        }
        Notice notice = new Notice(state.instanceId, player.id, Event.REVIVED,
                runic ? "Runic pet revived you" : detail, now, target);
        try {
            if (!revivalEffect.test(notice)) return TransitionResult.failure("revival effect could not be applied");
        } catch (RuntimeException failure) {
            return TransitionResult.failure("revival effect failed");
        }
        if (runic) { player.runicChargeUsed = true; player.forgivenDeaths++; }
        player.reviveKind = ReviveKind.NONE;
        player.state = PlayerState.ALIVE;
        player.reviveAt = null;
        player.lastTarget = target;
        player.lastCountdownSeconds = -1;
        emit(notice);
        return TransitionResult.success(Event.REVIVED, detail, snapshot(state), player.id, target);
    }

    private TransitionResult transitionToGhost(MutableInstance state, MutablePlayer player, Instant now,
                                               boolean notifyPlayer) {
        player.deaths++;
        player.state = PlayerState.GHOST;
        boolean runic = notifyPlayer && !player.runicChargeUsed && runicPetActive.test(player.id);
        player.reviveKind = runic ? ReviveKind.RUNIC : state.ordinaryRevival ? ReviveKind.STANDARD : ReviveKind.NONE;
        Duration delay = runic ? Duration.ofSeconds(5) : timings.reviveDuration();
        player.reviveAt = player.reviveKind == ReviveKind.NONE ? null : now.plus(delay);
        player.lastTarget = null;
        player.lastCountdownSeconds = delay.toSeconds();
        Notice ghost = new Notice(state.instanceId, player.id, Event.GHOSTED,
                player.reviveAt == null ? "Ordinary revival is disabled on this difficulty"
                        : "Reviving in " + delay.toSeconds() + " seconds", player.reviveAt, null);
        if (noOnlineAlive(state)) return wipe(state, "no online active alive player remains", ghost);
        if (notifyPlayer) emit(ghost);
        return TransitionResult.success(Event.GHOSTED, ghost.detail(), snapshot(state), player.id);
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
                player.online && (player.state == PlayerState.ALIVE
                        || player.state == PlayerState.GHOST && player.reviveKind == ReviveKind.RUNIC));
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
        return new PlayerSnapshot(player.id, player.state, player.online, player.reviveAt, player.lastTarget,
                player.deaths, player.forgivenDeaths, player.runicChargeUsed, player.reviveKind);
    }

    private static final class MutableInstance {
        private final UUID instanceId;
        private final Map<UUID, MutablePlayer> players = new LinkedHashMap<>();
        private Consumer<Instant> tick;
        private boolean ordinaryRevival = true;
        private boolean running;
        private boolean completed;
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
        private int deaths;
        private int forgivenDeaths;
        private boolean runicChargeUsed;
        private ReviveKind reviveKind = ReviveKind.NONE;
        private long lastCountdownSeconds = -1;

        private MutablePlayer(UUID id) { this.id = id; }
    }

    public enum PlayerState { ALIVE, GHOST, REMOVED }
    public enum ReviveKind { NONE, STANDARD, RUNIC, ADMIN }

    public enum Event {
        STARTED, GHOSTED, GHOST_COUNTDOWN, DISCONNECTED, RECONNECTED, REVIVED, REMOVED, WIPED, COMPLETED
    }

    public record PlayerSnapshot(UUID playerId, PlayerState state, boolean online,
                                 Instant reviveAt, UUID reviveTarget, int deaths, int forgivenDeaths,
                                 boolean runicChargeUsed, ReviveKind reviveKind) {
        public PlayerSnapshot(UUID playerId, PlayerState state, boolean online, Instant reviveAt, UUID reviveTarget, int deaths) {
            this(playerId, state, online, reviveAt, reviveTarget, deaths, 0, false, ReviveKind.NONE);
        }
        public int scoringDeaths() { return Math.max(0, deaths - forgivenDeaths); }
        public PlayerSnapshot {
            Objects.requireNonNull(playerId); Objects.requireNonNull(state);
            if (deaths < 0 || forgivenDeaths < 0 || forgivenDeaths > deaths) throw new IllegalArgumentException("invalid death counts");
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
