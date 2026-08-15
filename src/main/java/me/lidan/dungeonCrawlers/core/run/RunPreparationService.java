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

/** Coordinates the player-facing PREPARING phase and its one-shot start door. */
public final class RunPreparationService {
    public static final Duration PREPARATION_TIMEOUT = Duration.ofMinutes(5);

    private final DoorService doors;
    private final CentralUpdateService updates;
    private final StateTransitionService transitions;
    private final Clock clock;
    private final Consumer<UUID> firstRoomActivator;
    private final boolean failOnFirstRoomActivation;
    private final Consumer<String> diagnostics;
    private final Consumer<UUID> instanceCanceller;
    private final Map<UUID, MutableRun> runs = new LinkedHashMap<>();

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
            });
        } catch (RuntimeException exception) {
            return DoorInteractionResult.failure(exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage());
        }
        run.door = opened.door();
        return DoorInteractionResult.success(opened.detail(), opened.door(), run.snapshot());
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

    public synchronized List<RunSnapshot> snapshots() {
        return runs.values().stream().map(MutableRun::snapshot).toList();
    }

    public synchronized PreparationResult cleanup(UUID instanceId) {
        MutableRun run = runs.remove(Objects.requireNonNull(instanceId, "instanceId"));
        if (run == null) return PreparationResult.success("preparation already cleaned", null);
        updates.remove(instanceId);
        doors.remove(instanceId);
        return PreparationResult.success("preparation cleaned", run.snapshot());
    }

    private synchronized void update(UUID instanceId, MutableRun run, Instant now) {
        if (runs.get(instanceId) != run) return;
        if (run.state != RunState.PREPARING || now.isBefore(run.preparationDeadline)) {
            run.lastUpdated = now;
            return;
        }
        runs.remove(instanceId);
        updates.remove(instanceId);
        doors.remove(instanceId);
        diagnose("instance=" + instanceId + " preparation timed out");
        try {
            instanceCanceller.accept(instanceId);
        } catch (RuntimeException exception) {
            diagnose("instance=" + instanceId + " timeout cancellation failed: " + message(exception));
        }
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

    public enum RunState { PREPARING, RUNNING }

    public record DoorBlockLookup(UUID instanceId, DoorService.DoorState state) {
        public DoorBlockLookup { Objects.requireNonNull(instanceId); Objects.requireNonNull(state); }
    }

    public record RunSnapshot(UUID instanceId, RunState state, List<UUID> participants,
                              List<String> allowedClasses, Map<UUID, String> selectedClasses,
                              boolean snapshotsReady, Instant preparationDeadline, Instant startedAt,
                              boolean firstRoomActivated, Instant lastUpdated, DoorService.DoorSnapshot door) {
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
                                        DoorService.DoorSnapshot door, RunSnapshot snapshot) {
        public DoorInteractionResult { Objects.requireNonNull(detail); }
        public static DoorInteractionResult success(String detail, DoorService.DoorSnapshot door,
                                                    RunSnapshot snapshot) {
            return new DoorInteractionResult(true, detail, door, snapshot);
        }
        public static DoorInteractionResult failure(String detail) {
            return new DoorInteractionResult(false, detail, null, null);
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
        private boolean firstRoomActivated;
        private Instant lastUpdated;
        private DoorService.DoorSnapshot door;

        private MutableRun(UUID instanceId, PartySnapshot party, List<String> allowedClasses,
                           Map<String, ClassDefinition> classes, DoorService.DoorSnapshot door,
                           Instant preparationDeadline) {
            this.instanceId = instanceId;
            this.participants = List.copyOf(party.onlineMembers());
            this.allowedClasses = List.copyOf(allowedClasses);
            this.classes = Map.copyOf(classes);
            this.door = door;
            this.preparationDeadline = preparationDeadline;
            this.lastUpdated = preparationDeadline.minus(PREPARATION_TIMEOUT);
        }

        private RunSnapshot snapshot() {
            return new RunSnapshot(instanceId, state, participants, allowedClasses,
                    selectedClasses, snapshotsReady, preparationDeadline, startedAt, firstRoomActivated,
                    lastUpdated, door);
        }
    }
}
