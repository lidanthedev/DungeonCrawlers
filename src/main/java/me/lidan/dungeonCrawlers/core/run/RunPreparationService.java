package me.lidan.dungeonCrawlers.core.run;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ClassDefinition;
import me.lidan.dungeonCrawlers.core.door.DoorService;
import me.lidan.dungeonCrawlers.core.party.PartySnapshot;
import me.lidan.dungeonCrawlers.core.state.InstanceState;
import me.lidan.dungeonCrawlers.core.state.StateTransitionService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Facing;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.core.update.CentralUpdateService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Coordinates the player-facing PREPARING phase and its one-shot start door. */
public final class RunPreparationService {
    public static final Duration PREPARATION_WARNING = Duration.ofMinutes(1);
    public static final Duration PREPARATION_TIMEOUT = Duration.ofMinutes(5);
    public static final Duration RUN_WARNING = Duration.ofMinutes(1);
    public static final Duration RUN_TIMEOUT = Duration.ofMinutes(60);
    public static final Duration FAILED_READING_PERIOD = Duration.ofSeconds(10);
    public static final Duration COMPLETION_WARNING = Duration.ofMinutes(1);
    public static final Duration COMPLETION_TIMEOUT = Duration.ofMinutes(5);
    public static final Duration COMPLETION_FINAL_COUNTDOWN = Duration.ofSeconds(10);

    private final DoorService doors;
    private final CentralUpdateService updates;
    private final StateTransitionService transitions;
    private final Clock clock;
    private final Consumer<UUID> firstRoomActivator;
    private final boolean failOnFirstRoomActivation;
    private final Consumer<String> diagnostics;
    private final Consumer<UUID> instanceCanceller;
    private Consumer<UUID> failureHandler = ignored -> { };
    private Predicate<UUID> activeGroup = ignored -> true;
    private Consumer<DeadlineNotice> deadlineNotices = ignored -> { };
    private final Map<UUID, MutableRun> runs = new LinkedHashMap<>();
    private boolean frozen;

    public RunPreparationService(DoorService doors, CentralUpdateService updates,
                                 StateTransitionService transitions, Clock clock,
                                 Consumer<UUID> firstRoomActivator) {
        this(doors, updates, transitions, clock, firstRoomActivator, ignored -> { }, ignored -> { });
    }

    public RunPreparationService(DoorService doors, CentralUpdateService updates,
                                 StateTransitionService transitions, Clock clock,
                                 Consumer<UUID> firstRoomActivator,
                                 Consumer<String> diagnostics,
                                 Consumer<UUID> instanceCanceller) {
        this(doors, updates, transitions, clock, firstRoomActivator, diagnostics, instanceCanceller, false);
    }

    public RunPreparationService(DoorService doors, CentralUpdateService updates,
                                 StateTransitionService transitions, Clock clock,
                                 Consumer<UUID> firstRoomActivator,
                                 Consumer<String> diagnostics,
                                 Consumer<UUID> instanceCanceller,
                                 boolean failOnFirstRoomActivation) {
        this.doors = Objects.requireNonNull(doors, "doors");
        this.updates = Objects.requireNonNull(updates, "updates");
        this.transitions = Objects.requireNonNull(transitions, "transitions");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.firstRoomActivator = Objects.requireNonNull(firstRoomActivator, "firstRoomActivator");
        this.failOnFirstRoomActivation = failOnFirstRoomActivation;
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.instanceCanceller = Objects.requireNonNull(instanceCanceller, "instanceCanceller");
    }

    /** Configures the platform callbacks used when a deadline changes the run lifecycle. */
    public synchronized void configureDeadlineHandlers(Consumer<UUID> failureHandler,
                                                        Predicate<UUID> activeGroup,
                                                        Consumer<DeadlineNotice> deadlineNotices) {
        this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler");
        this.activeGroup = Objects.requireNonNull(activeGroup, "activeGroup");
        this.deadlineNotices = Objects.requireNonNull(deadlineNotices, "deadlineNotices");
    }

    public synchronized PreparationResult registerGenerated(UUID instanceId, PartySnapshot party,
                                                             List<String> allowedClasses,
                                                             Map<String, ClassDefinition> classes,
                                                             Point doorCenter, Facing outward) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(party, "party");
        Objects.requireNonNull(allowedClasses, "allowedClasses");
        Objects.requireNonNull(classes, "classes");
        Objects.requireNonNull(doorCenter, "doorCenter");
        Objects.requireNonNull(outward, "outward");
        if (frozen) return PreparationResult.failure("run preparation is frozen for plugin disable");
        MutableRun existing = runs.get(instanceId);
        if (existing != null) return PreparationResult.failure("preparation already registered");
        if (allowedClasses.isEmpty()) return PreparationResult.failure("floor has no allowed classes");
        List<String> orderedAllowed = List.copyOf(new ArrayList<>(allowedClasses));
        for (String classId : orderedAllowed) {
            if (!classes.containsKey(classId)) return PreparationResult.failure("unknown allowed class " + classId);
        }
        DoorService.DoorSnapshot door = doors.register(instanceId, doorCenter, outward);
        MutableRun run = new MutableRun(instanceId, party, orderedAllowed, classes, door,
                clock.instant().plus(PREPARATION_TIMEOUT));
        if (!updates.register(instanceId, now -> update(instanceId, run, now))) {
            doors.remove(instanceId);
            return PreparationResult.failure("central update already registered for instance");
        }
        runs.put(instanceId, run);
        return PreparationResult.success("preparation registered", run.snapshot());
    }

    public synchronized PreparationResult markSnapshotsReady(UUID instanceId) {
        MutableRun run = runs.get(Objects.requireNonNull(instanceId, "instanceId"));
        if (run == null) return PreparationResult.failure("unknown preparation " + instanceId);
        if (run.state != RunState.PREPARING) return PreparationResult.failure("run is already " + run.state);
        if (run.snapshotsReady) return PreparationResult.success("snapshots already acknowledged", run.snapshot());
        run.snapshotsReady = true;
        return PreparationResult.success("recovery snapshots acknowledged", run.snapshot());
    }

    public synchronized ClassSelectionResult selectClass(UUID instanceId, UUID playerId, String classId) {
        MutableRun run = runs.get(Objects.requireNonNull(instanceId, "instanceId"));
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(classId, "classId");
        if (run == null) return ClassSelectionResult.failure("unknown preparation " + instanceId);
        if (!run.snapshotsReady) return ClassSelectionResult.failure("recovery snapshots are still pending");
        if (run.state != RunState.PREPARING) return ClassSelectionResult.failure("run is already " + run.state);
        if (!run.participants.contains(playerId)) return ClassSelectionResult.failure("player is not in this run");
        if (!run.allowedClasses.contains(classId)) return ClassSelectionResult.failure("class is not allowed: " + classId);
        if (!run.classes.containsKey(classId)) return ClassSelectionResult.failure("unknown class " + classId);
        run.selectedClasses.put(playerId, classId);
        boolean allClassesSelected = run.selectedClasses.keySet().containsAll(run.participants);
        DoorService.DoorSnapshot door = allClassesSelected
                ? doors.setReady(instanceId) : doors.info(instanceId).orElseThrow();
        run.door = door;
        return ClassSelectionResult.success(allClassesSelected
                ? "class selected; start door is ready" : "class selected", run.snapshot(), door);
    }

    public synchronized DoorInteractionResult openDoor(UUID instanceId, UUID playerId) {
        MutableRun run = runs.get(Objects.requireNonNull(instanceId, "instanceId"));
        Objects.requireNonNull(playerId, "playerId");
        if (run == null) return DoorInteractionResult.failure("unknown preparation " + instanceId);
        if (!run.participants.contains(playerId)) return DoorInteractionResult.failure("player is not in this run");
        if (!run.snapshotsReady) return DoorInteractionResult.failure("recovery snapshots are still pending");
        if (run.state != RunState.PREPARING) {
            return DoorInteractionResult.success("run is already " + run.state,
                    doors.info(instanceId).orElseThrow(), run.snapshot());
        }
        boolean allClassesSelected = run.selectedClasses.keySet().containsAll(run.participants);
        if (!allClassesSelected) {
            return DoorInteractionResult.failure("every active member must select a class first");
        }
        DoorService.OpenResult opened;
        try {
            opened = doors.open(instanceId, () -> {
                if (!run.firstRoomActivated) {
                    try {
                        firstRoomActivator.accept(instanceId);
                    } catch (RuntimeException exception) {
                        diagnose("instance=" + instanceId + " first room activation failed: " + message(exception));
                        if (failOnFirstRoomActivation) throw exception;
                    }
                    run.firstRoomActivated = true;
                }
                StateTransitionService.TransitionResult transition = transitions.transition(
                        InstanceState.PREPARING, InstanceState.RUNNING);
                if (!transition.accepted()) throw new IllegalStateException(transition.detail());
                run.state = RunState.RUNNING;
                run.startedAt = clock.instant();
                run.runDeadline = run.startedAt.plus(RUN_TIMEOUT);
            });
        } catch (RuntimeException exception) {
            String detail = exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage();
            if (failOnFirstRoomActivation) {
                cleanup(instanceId);
                try {
                    instanceCanceller.accept(instanceId);
                } catch (RuntimeException cancellationFailure) {
                    diagnose("instance=" + instanceId + " activation rollback failed: " + message(cancellationFailure));
                }
                return DoorInteractionResult.activationFailure("first room activation failed: " + detail);
            }
            return DoorInteractionResult.failure(detail);
        }
        run.door = opened.door();
        return DoorInteractionResult.success(opened.detail(), opened.door(), run.snapshot());
    }

    /** Transitions a started run into the isolated boss encounter. */
    public synchronized PhaseResult enterBoss(UUID instanceId) {
        MutableRun run = runs.get(Objects.requireNonNull(instanceId, "instanceId"));
        if (run == null) return PhaseResult.failure("unknown run " + instanceId);
        if (run.state == RunState.BOSS) return PhaseResult.success("boss encounter already active", run.snapshot());
        if (run.state != RunState.RUNNING) return PhaseResult.failure("run is not running: " + run.state);
        StateTransitionService.TransitionResult transition = transitions.transition(
                InstanceState.RUNNING, InstanceState.BOSS);
        if (!transition.accepted()) return PhaseResult.failure(transition.detail());
        run.state = RunState.BOSS;
        return PhaseResult.success("boss encounter started", run.snapshot());
    }

    /** Freezes progression while the final completion record is prepared. */
    public synchronized PhaseResult enterCompletionPending(UUID instanceId) {
        MutableRun run = runs.get(Objects.requireNonNull(instanceId, "instanceId"));
        if (run == null) return PhaseResult.failure("unknown run " + instanceId);
        if (run.state == RunState.COMPLETION_PENDING) {
            return PhaseResult.success("completion already pending", run.snapshot());
        }
        if (run.state != RunState.BOSS) return PhaseResult.failure("run is not in BOSS: " + run.state);
        StateTransitionService.TransitionResult transition = transitions.transition(
                InstanceState.BOSS, InstanceState.COMPLETION_PENDING);
        if (!transition.accepted()) return PhaseResult.failure(transition.detail());
        run.state = RunState.COMPLETION_PENDING;
        return PhaseResult.success("boss defeated; completion pending", run.snapshot());
    }

    /** Starts the five-minute completed-result period after rewards are durably finalized. */
    public synchronized PhaseResult markCompleted(UUID instanceId) {
        MutableRun run = runs.get(Objects.requireNonNull(instanceId, "instanceId"));
        if (run == null) return PhaseResult.failure("unknown run " + instanceId);
        if (run.state == RunState.COMPLETED) return PhaseResult.success("run already completed", run.snapshot());
        if (run.state != RunState.COMPLETION_PENDING) {
            return PhaseResult.failure("run is not completion pending: " + run.state);
        }
        StateTransitionService.TransitionResult transition = transitions.transition(
                InstanceState.COMPLETION_PENDING, InstanceState.COMPLETED);
        if (!transition.accepted()) return PhaseResult.failure(transition.detail());
        run.state = RunState.COMPLETED;
        run.completedAt = clock.instant();
        run.completionDeadline = run.completedAt.plus(COMPLETION_TIMEOUT);
        run.lastCompletionCountdown = -1;
        return PhaseResult.success("run completed; reward period started", run.snapshot());
    }

    /** Marks an active run failed without performing cleanup. */
    public synchronized PhaseResult fail(UUID instanceId, String detail) {
        MutableRun run = runs.get(Objects.requireNonNull(instanceId, "instanceId"));
        Objects.requireNonNull(detail, "detail");
        if (run == null) return PhaseResult.failure("unknown run " + instanceId);
        if (run.state == RunState.FAILED) return PhaseResult.success("run already failed: " + detail, run.snapshot());
        if (run.state != RunState.RUNNING && run.state != RunState.BOSS) {
            return PhaseResult.failure("run cannot fail from " + run.state);
        }
        transitionToFailed(run, detail, clock.instant());
        return PhaseResult.success(detail, run.snapshot());
    }

    public synchronized Optional<DoorBlockLookup> doorAt(Point point) {
        return doors.lookup(Objects.requireNonNull(point, "point"))
                .map(block -> new DoorBlockLookup(block.instanceId(), block.state()));
    }

    public synchronized Optional<UUID> instanceFor(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return runs.values().stream().filter(run -> run.participants.contains(playerId))
                .map(run -> run.instanceId).findFirst();
    }

    public synchronized Optional<ClassDefinition> selectedClass(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        for (MutableRun run : runs.values()) {
            String classId = run.selectedClasses.get(playerId);
            if (classId != null) return Optional.of(run.classes.get(classId));
        }
        return Optional.empty();
    }

    public synchronized Optional<RunSnapshot> info(UUID instanceId) {
        MutableRun run = runs.get(Objects.requireNonNull(instanceId, "instanceId"));
        return run == null ? Optional.empty() : Optional.of(run.snapshot());
    }

    /** Removes a participant after an escape; removed players cannot be selected or teleported later. */
    public synchronized PreparationResult removeParticipant(UUID instanceId, UUID playerId) {
        MutableRun run = runs.get(Objects.requireNonNull(instanceId, "instanceId"));
        Objects.requireNonNull(playerId, "playerId");
        if (run == null) return PreparationResult.failure("unknown preparation " + instanceId);
        if (!run.participants.remove(playerId)) return PreparationResult.failure("player is not in this run");
        run.selectedClasses.remove(playerId);
        return PreparationResult.success("participant removed", run.snapshot());
    }

    public synchronized List<RunSnapshot> snapshots() {
        return runs.values().stream().map(MutableRun::snapshot).toList();
    }

    /** Stops deadline callbacks before the plugin restores online player snapshots. */
    public synchronized void freezeForDisable() {
        frozen = true;
    }

    public synchronized boolean frozen() {
        return frozen;
    }

    public synchronized void cleanupAll() {
        new ArrayList<>(runs.keySet()).forEach(this::cleanup);
    }

    public synchronized PreparationResult cleanup(UUID instanceId) {
        MutableRun run = runs.remove(Objects.requireNonNull(instanceId, "instanceId"));
        if (run == null) return PreparationResult.success("preparation already cleaned", null);
        updates.remove(instanceId);
        doors.remove(instanceId);
        return PreparationResult.success("preparation cleaned", run.snapshot());
    }

    private synchronized void update(UUID instanceId, MutableRun run, Instant now) {
        if (frozen || runs.get(instanceId) != run) return;
        run.lastUpdated = now;
        switch (run.state) {
            case PREPARING -> updatePreparing(instanceId, run, now);
            case RUNNING, BOSS -> updateRunning(instanceId, run, now);
            case FAILED -> updateFailed(instanceId, run, now);
            case COMPLETED -> updateCompleted(instanceId, run, now);
            default -> { }
        }
    }

    private void updatePreparing(UUID instanceId, MutableRun run, Instant now) {
        if (!run.preparationWarningSent && inWarningWindow(now, run.preparationDeadline, PREPARATION_WARNING)) {
            run.preparationWarningSent = true;
            notifyDeadline(new DeadlineNotice(instanceId, DeadlineEvent.PREPARATION_WARNING,
                    secondsRemaining(now, run.preparationDeadline), "preparation deadline is approaching"));
        }
        if (!now.isBefore(run.preparationDeadline)) {
            notifyDeadline(new DeadlineNotice(instanceId, DeadlineEvent.PREPARATION_EXPIRED, 0,
                    "preparation timed out"));
            closeAfterDeadline(instanceId, run, "preparation timed out");
        }
    }

    private void updateRunning(UUID instanceId, MutableRun run, Instant now) {
        if (run.runDeadline == null) return;
        if (!run.runWarningSent && inWarningWindow(now, run.runDeadline, RUN_WARNING)) {
            run.runWarningSent = true;
            notifyDeadline(new DeadlineNotice(instanceId, DeadlineEvent.RUN_WARNING,
                    secondsRemaining(now, run.runDeadline), "run deadline is approaching"));
        }
        if (!now.isBefore(run.runDeadline)) {
            transitionToFailed(run, "run time limit reached", now);
        }
    }

    private void updateFailed(UUID instanceId, MutableRun run, Instant now) {
        if (run.failedDeadline == null || now.isBefore(run.failedDeadline)) return;
        notifyDeadline(new DeadlineNotice(instanceId, DeadlineEvent.FAILED_CLOSING, 0,
                "failed run reading period ended"));
        closeAfterDeadline(instanceId, run, "failed run reading period ended");
    }

    private void updateCompleted(UUID instanceId, MutableRun run, Instant now) {
        boolean active;
        try {
            active = activeGroup.test(instanceId);
        } catch (RuntimeException exception) {
            diagnose("instance=" + instanceId + " active completion group check failed: " + message(exception));
            active = true;
        }
        if (!active) {
            notifyDeadline(new DeadlineNotice(instanceId, DeadlineEvent.COMPLETION_CLOSED, 0,
                    "no active participants remain"));
            closeAfterDeadline(instanceId, run, "completed reward group is empty");
            return;
        }
        if (!run.completionWarningSent && inWarningWindow(now, run.completionDeadline, COMPLETION_WARNING)) {
            run.completionWarningSent = true;
            notifyDeadline(new DeadlineNotice(instanceId, DeadlineEvent.COMPLETION_WARNING,
                    secondsRemaining(now, run.completionDeadline), "reward period is ending soon"));
        }
        if (!now.isBefore(run.completionDeadline)) {
            notifyDeadline(new DeadlineNotice(instanceId, DeadlineEvent.COMPLETION_CLOSED, 0,
                    "completed reward period ended"));
            closeAfterDeadline(instanceId, run, "completed reward period ended");
            return;
        }
        long remaining = secondsRemaining(now, run.completionDeadline);
        if (remaining > 0 && remaining <= COMPLETION_FINAL_COUNTDOWN.toSeconds()
                && remaining != run.lastCompletionCountdown) {
            run.lastCompletionCountdown = remaining;
            notifyDeadline(new DeadlineNotice(instanceId, DeadlineEvent.COMPLETION_COUNTDOWN, remaining,
                    "reward period closes soon"));
        }
    }

    private void transitionToFailed(MutableRun run, String detail, Instant now) {
        InstanceState current = run.state == RunState.RUNNING ? InstanceState.RUNNING : InstanceState.BOSS;
        StateTransitionService.TransitionResult transition = transitions.transition(current, InstanceState.FAILED);
        if (!transition.accepted()) throw new IllegalStateException(transition.detail());
        run.state = RunState.FAILED;
        run.failedDeadline = now.plus(FAILED_READING_PERIOD);
        notifyDeadline(new DeadlineNotice(run.instanceId, DeadlineEvent.RUN_FAILED, 0, detail));
        try {
            failureHandler.accept(run.instanceId);
        } catch (RuntimeException exception) {
            diagnose("instance=" + run.instanceId + " failure handling failed: " + message(exception));
        }
    }

    private void closeAfterDeadline(UUID instanceId, MutableRun run, String detail) {
        diagnose("instance=" + instanceId + " " + detail);
        try {
            instanceCanceller.accept(instanceId);
        } catch (RuntimeException exception) {
            diagnose("instance=" + instanceId + " deadline cleanup failed: " + message(exception));
        }
        if (runs.get(instanceId) == run) cleanup(instanceId);
    }

    private void notifyDeadline(DeadlineNotice notice) {
        try {
            deadlineNotices.accept(notice);
        } catch (RuntimeException exception) {
            diagnose("instance=" + notice.instanceId() + " deadline notice failed: " + message(exception));
        }
    }

    private static boolean inWarningWindow(Instant now, Instant deadline, Duration warning) {
        return !now.isBefore(deadline.minus(warning)) && now.isBefore(deadline);
    }

    private static long secondsRemaining(Instant now, Instant deadline) {
        Duration remaining = Duration.between(now, deadline);
        if (remaining.isZero() || remaining.isNegative()) return 0;
        return remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1);
    }

    private void diagnose(String message) {
        try {
            diagnostics.accept(message);
        } catch (RuntimeException ignored) {
            // Diagnostics are best-effort and must not interrupt state cleanup.
        }
    }

    private static String message(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    public enum RunState { PREPARING, RUNNING, BOSS, COMPLETION_PENDING, COMPLETED, FAILED }

    public enum DeadlineEvent {
        PREPARATION_WARNING, PREPARATION_EXPIRED, RUN_WARNING, RUN_FAILED,
        FAILED_CLOSING, COMPLETION_WARNING, COMPLETION_COUNTDOWN, COMPLETION_CLOSED
    }

    public record DeadlineNotice(UUID instanceId, DeadlineEvent event, long secondsRemaining, String detail) {
        public DeadlineNotice {
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(event, "event");
            Objects.requireNonNull(detail, "detail");
            if (secondsRemaining < 0) throw new IllegalArgumentException("secondsRemaining must not be negative");
        }
    }

    public record DoorBlockLookup(UUID instanceId, DoorService.DoorState state) {
        public DoorBlockLookup { Objects.requireNonNull(instanceId); Objects.requireNonNull(state); }
    }

    public record RunSnapshot(UUID instanceId, RunState state, List<UUID> participants,
                              List<String> allowedClasses, Map<UUID, String> selectedClasses,
                              boolean snapshotsReady, Instant preparationDeadline, Instant startedAt,
                              Instant runDeadline, Instant failedDeadline, Instant completedAt,
                              Instant completionDeadline, boolean firstRoomActivated, Instant lastUpdated,
                              DoorService.DoorSnapshot door) {
        public RunSnapshot {
            Objects.requireNonNull(instanceId); Objects.requireNonNull(state);
            participants = List.copyOf(participants); allowedClasses = List.copyOf(allowedClasses);
            selectedClasses = Map.copyOf(selectedClasses); Objects.requireNonNull(preparationDeadline);
            Objects.requireNonNull(lastUpdated); Objects.requireNonNull(door);
        }
    }

    public record PreparationResult(boolean successful, String detail, RunSnapshot snapshot) {
        public PreparationResult { Objects.requireNonNull(detail); }
        public static PreparationResult success(String detail, RunSnapshot snapshot) {
            return new PreparationResult(true, detail, snapshot);
        }
        public static PreparationResult failure(String detail) { return new PreparationResult(false, detail, null); }
    }

    public record PhaseResult(boolean successful, String detail, RunSnapshot snapshot) {
        public PhaseResult { Objects.requireNonNull(detail); }
        public static PhaseResult success(String detail, RunSnapshot snapshot) {
            return new PhaseResult(true, detail, snapshot);
        }
        public static PhaseResult failure(String detail) { return new PhaseResult(false, detail, null); }
    }

    public record ClassSelectionResult(boolean successful, String detail, RunSnapshot snapshot,
                                      DoorService.DoorSnapshot door) {
        public ClassSelectionResult { Objects.requireNonNull(detail); }
        public static ClassSelectionResult success(String detail, RunSnapshot snapshot,
                                                   DoorService.DoorSnapshot door) {
            return new ClassSelectionResult(true, detail, snapshot, door);
        }
        public static ClassSelectionResult failure(String detail) {
            return new ClassSelectionResult(false, detail, null, null);
        }
    }

    public record DoorInteractionResult(boolean successful, String detail,
                                        DoorService.DoorSnapshot door, RunSnapshot snapshot,
                                        boolean rollbackRequired) {
        public DoorInteractionResult { Objects.requireNonNull(detail); }
        public static DoorInteractionResult success(String detail, DoorService.DoorSnapshot door,
                                                    RunSnapshot snapshot) {
            return new DoorInteractionResult(true, detail, door, snapshot, false);
        }
        public static DoorInteractionResult failure(String detail) {
            return new DoorInteractionResult(false, detail, null, null, false);
        }
        public static DoorInteractionResult activationFailure(String detail) {
            return new DoorInteractionResult(false, detail, null, null, true);
        }
    }

    private static final class MutableRun {
        private final UUID instanceId;
        private final List<UUID> participants;
        private final List<String> allowedClasses;
        private final Map<String, ClassDefinition> classes;
        private final Instant preparationDeadline;
        private final Map<UUID, String> selectedClasses = new LinkedHashMap<>();
        private RunState state = RunState.PREPARING;
        private boolean snapshotsReady;
        private Instant startedAt;
        private Instant runDeadline;
        private Instant failedDeadline;
        private Instant completedAt;
        private Instant completionDeadline;
        private boolean preparationWarningSent;
        private boolean runWarningSent;
        private boolean completionWarningSent;
        private long lastCompletionCountdown = -1;
        private boolean firstRoomActivated;
        private Instant lastUpdated;
        private DoorService.DoorSnapshot door;

        private MutableRun(UUID instanceId, PartySnapshot party, List<String> allowedClasses,
                           Map<String, ClassDefinition> classes, DoorService.DoorSnapshot door,
                           Instant preparationDeadline) {
            this.instanceId = instanceId;
            this.participants = new ArrayList<>(party.onlineMembers());
            this.allowedClasses = List.copyOf(allowedClasses);
            this.classes = Map.copyOf(classes);
            this.door = door;
            this.preparationDeadline = preparationDeadline;
            this.lastUpdated = preparationDeadline.minus(PREPARATION_TIMEOUT);
        }

        private RunSnapshot snapshot() {
            return new RunSnapshot(instanceId, state, participants, allowedClasses,
                    selectedClasses, snapshotsReady, preparationDeadline, startedAt, runDeadline,
                    failedDeadline, completedAt, completionDeadline, firstRoomActivated, lastUpdated, door);
        }
    }
}
