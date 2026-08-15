package me.lidan.dungeonCrawlers.core.generation;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ConfigSnapshot;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.FloorDefinition;
import me.lidan.dungeonCrawlers.core.generation.SlotAllocator.SlotLease;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner.LayoutPlan;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner.Placement;
import me.lidan.dungeonCrawlers.core.party.PartySnapshot;
import me.lidan.dungeonCrawlers.core.reservation.PlayerReservationService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.integration.GenerationWorldGateway;
import me.lidan.dungeonCrawlers.persistence.DurableRepository;
import me.lidan.dungeonCrawlers.persistence.DurableSubmission;
import me.lidan.dungeonCrawlers.persistence.DurableWrite;
import me.lidan.dungeonCrawlers.persistence.model.GenerationJournal;
import me.lidan.dungeonCrawlers.persistence.model.GenerationJournal.PlannedBounds;
import me.lidan.dungeonCrawlers.persistence.model.GenerationJournalCodec;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class GenerationService {
    public static final String JOURNAL_NAMESPACE = "generation";
    private final PlayerReservationService reservations;
    private final SlotAllocator slots;
    private final DurableRepository repository;
    private final GenerationWorldGateway world;
    private final PreparationProvider preparation;
    private final Executor planningExecutor;
    private final Executor runtimeExecutor;
    private final BooleanSupplier primaryThread;
    private final Consumer<String> diagnostics;
    private final Clock clock;
    private final String worldName;
    private final GenerationJournalCodec journalCodec = new GenerationJournalCodec();
    private final Object admissionLock = new Object();
    private final Map<UUID, MutableInstance> instances = new LinkedHashMap<>();
    private boolean startsEnabled;
    private boolean recoveryRunning;
    private int recoveryDiscovered;
    private int recoveryCleared;
    private final List<String> recoveryBlockers = new ArrayList<>();

    public GenerationService(PlayerReservationService reservations, SlotAllocator slots, DurableRepository repository,
                             GenerationWorldGateway world, PreparationProvider preparation, Executor planningExecutor,
                             Executor runtimeExecutor, BooleanSupplier primaryThread, Consumer<String> diagnostics,
                             Clock clock, String worldName) {
        this.reservations = Objects.requireNonNull(reservations);
        this.slots = Objects.requireNonNull(slots);
        this.repository = Objects.requireNonNull(repository);
        this.world = Objects.requireNonNull(world);
        this.preparation = Objects.requireNonNull(preparation);
        this.planningExecutor = Objects.requireNonNull(planningExecutor);
        this.runtimeExecutor = Objects.requireNonNull(runtimeExecutor);
        this.primaryThread = Objects.requireNonNull(primaryThread);
        this.diagnostics = Objects.requireNonNull(diagnostics);
        this.clock = Objects.requireNonNull(clock);
        this.worldName = Objects.requireNonNull(worldName);
    }

    public StartResult start(StartRequest request) {
        requirePrimaryThread();
        Objects.requireNonNull(request, "request");
        if (request.debugDelayMillis() < 0 || request.debugDelayMillis() > 5_000) {
            return StartResult.failure("debug delay must be in 0..5000 milliseconds");
        }
        UUID instanceId = UUID.randomUUID();
        MutableInstance instance;
        synchronized (admissionLock) {
            if (!startsEnabled) return StartResult.failure("generation starts are blocked until recovery completes");
            if (!slots.hasFreeSlot()) return StartResult.failure("generation capacity is full");
            var reserved = reservations.reserve(instanceId, request.party());
            if (!reserved.successful()) return StartResult.failure(reserved.detail());
            Optional<SlotLease> allocated = slots.allocate(instanceId);
            if (allocated.isEmpty()) {
                reservations.release(instanceId);
                return StartResult.failure("generation capacity changed during admission");
            }
            instance = new MutableInstance(instanceId, request, allocated.orElseThrow());
            instances.put(instanceId, instance);
        }
        diagnostics.accept("instance=" + instanceId + " admitted slot=" + instance.slot.id());
        CompletableFuture.supplyAsync(() -> prepare(instance), planningExecutor)
                .whenCompleteAsync((prepared, failure) -> finishPlanning(instanceId, instance.token, prepared, failure),
                        runtimeExecutor);
        return new StartResult(true, instanceId, instance.slot.id(), "generation admitted");
    }

    public ActionResult cancel(UUID instanceId) {
        requirePrimaryThread();
        MutableInstance instance = instances.get(instanceId);
        if (instance == null) return ActionResult.failure("unknown instance " + instanceId);
        if (instance.state == InstanceStatus.DESTROYED) return ActionResult.success("instance already cleaned");
        if (instance.state == InstanceStatus.CLEAR_FAILED) return beginClear(instance, "cleanup retry");
        if (instance.state == InstanceStatus.CLEARING) return ActionResult.success("cleanup already in flight");
        if (instance.cancelled) return ActionResult.success("cancellation already requested");
        InstanceStatus previous = instance.state;
        instance.cancelled = true;
        instance.token++;
        instance.state = InstanceStatus.CANCELLING;
        instance.detail = "cancellation requested during " + previous;
        if (previous == InstanceStatus.GENERATED) return beginClear(instance, "cancel after generation");
        return ActionResult.success("cancellation requested; in-flight stage will settle before clearing");
    }

    public ActionResult cleanup(UUID instanceId) {
        return cancel(instanceId);
    }

    public Optional<InstanceSnapshot> info(UUID instanceId) {
        requirePrimaryThread();
        MutableInstance instance = instances.get(instanceId);
        return instance == null ? Optional.empty() : Optional.of(instance.snapshot());
    }

    /** Returns the transformed EMERALD_BLOCK spawn and the yaw toward the START room exit. */
    public Optional<PlayerSpawn> playerSpawn(UUID instanceId) {
        requirePrimaryThread();
        MutableInstance instance = instances.get(instanceId);
        if (instance == null || instance.state != InstanceStatus.GENERATED || instance.prepared == null) {
            return Optional.empty();
        }
        var placements = instance.prepared.plan().placements();
        var start = placements.stream().filter(placement -> placement.index() == 0).findFirst();
        if (start.isEmpty()) return Optional.empty();
        Optional<Point> spawn = start.get().playerSpawns().stream().findFirst()
                .or(() -> placements.stream().flatMap(placement -> placement.playerSpawns().stream()).findFirst());
        if (spawn.isEmpty()) return Optional.empty();
        float yaw = start.get().exit().map(exit -> yaw(exit.outward().vector())).orElse(0F);
        return Optional.of(new PlayerSpawn(spawn.orElseThrow(), yaw));
    }

    public List<InstanceSnapshot> instances() {
        requirePrimaryThread();
        return instances.values().stream().map(MutableInstance::snapshot)
                .sorted(Comparator.comparing(InstanceSnapshot::instanceId)).toList();
    }

    public Set<String> activeTemplateIds() {
        requirePrimaryThread();
        Set<String> active = new LinkedHashSet<>();
        instances.values().stream().filter(instance -> instance.state != InstanceStatus.DESTROYED)
                .forEach(instance -> {
                    if (instance.prepared == null) active.addAll(instance.request.snapshot().rooms().keySet());
                    else instance.prepared.plan().placements().forEach(placement -> active.add(placement.templateId()));
                });
        return Set.copyOf(active);
    }

    public List<InstanceRegion> protectionRegions() {
        requirePrimaryThread();
        return instances.values().stream()
                .filter(instance -> instance.state != InstanceStatus.DESTROYED
                        && instance.journal != null && !instance.journal.plannedBounds().isEmpty())
                .map(instance -> new InstanceRegion(worldName, instance.instanceId,
                        enclosing(instance.journal.plannedBounds()),
                        Set.copyOf(instance.request.party().onlineMembers())))
                .toList();
    }

    public List<SlotLease> slots() {
        return slots.snapshot();
    }

    public RecoveryStatus recoveryStatus() {
        requirePrimaryThread();
        return new RecoveryStatus(startsEnabled, recoveryRunning, recoveryDiscovered, recoveryCleared,
                List.copyOf(recoveryBlockers));
    }

    public void freezeForDisable() {
        requirePrimaryThread();
        startsEnabled = false;
        instances.values().stream().filter(instance -> instance.state != InstanceStatus.DESTROYED).forEach(instance -> {
            instance.cancelled = true;
            instance.token++;
            instance.state = InstanceStatus.CANCELLING;
            instance.detail = "plugin disable froze callbacks; startup recovery owns any durable journal";
        });
    }

    public ActionResult recover() {
        requirePrimaryThread();
        if (recoveryRunning) return ActionResult.failure("recovery is already running");
        startsEnabled = false;
        recoveryRunning = true;
        recoveryDiscovered = 0;
        recoveryCleared = 0;
        recoveryBlockers.clear();
        repository.list(JOURNAL_NAMESPACE).whenCompleteAsync((records, failure) -> {
            if (failure != null) {
                recoveryBlockers.add("journal listing failed: " + message(failure));
                finishRecovery(true);
                return;
            }
            recoveryDiscovered = records.size();
            recoverNext(new ArrayList<>(records), 0, false);
        }, runtimeExecutor);
        return ActionResult.success("startup recovery scheduled");
    }

    private PreparedGeneration prepare(MutableInstance instance) {
        try {
            return preparation.prepare(instance.instanceId, instance.request.seed(), instance.request.floor(),
                    instance.request.snapshot(), instance.slot);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("generation preparation failed", exception);
        }
    }

    private void finishPlanning(UUID instanceId, long token, PreparedGeneration prepared, Throwable failure) {
        requirePrimaryThread();
        MutableInstance instance = instances.get(instanceId);
        if (instance == null || instance.token != token || instance.cancelled) {
            if (instance != null) releaseUnmodified(instance, "cancelled before journal");
            return;
        }
        if (failure != null) {
            releaseUnmodified(instance, "planning failed: " + message(failure));
            return;
        }
        instance.prepared = prepared;
        instance.journal = journal(instance, prepared.plan());
        instance.state = InstanceStatus.JOURNALING;
        instance.detail = "awaiting durable planned-bounds ACK";
        DurableWrite write = new DurableWrite(UUID.randomUUID(), instanceId, JOURNAL_NAMESPACE,
                instanceId.toString(), "generation-plan-" + instanceId, 1, journalCodec.encode(instance.journal));
        DurableSubmission submission = repository.submit(write);
        if (!submission.accepted()) {
            releaseUnmodified(instance, "journal rejected: " + submission.detail());
            return;
        }
        instance.journalAccepted = true;
        long journalToken = instance.token;
        submission.runtimeAck().whenCompleteAsync((receipt, journalFailure) ->
                finishJournal(instanceId, journalToken, journalFailure), runtimeExecutor);
    }

    private void finishJournal(UUID instanceId, long token, Throwable failure) {
        requirePrimaryThread();
        MutableInstance instance = instances.get(instanceId);
        if (instance == null || instance.state == InstanceStatus.DESTROYED) return;
        if (failure != null) {
            instance.cancelled = true;
            instance.token++;
            beginClear(instance, "journal ACK failed: " + message(failure));
            return;
        }
        if (instance.token != token || instance.cancelled) {
            beginClear(instance, "cancelled after journal ACK");
            return;
        }
        slots.markPasting(instance.slot.id(), instanceId);
        instance.state = InstanceStatus.PASTING;
        instance.detail = "pasting placement 0";
        paste(instance, 0, token);
    }

    private void paste(MutableInstance instance, int index, long token) {
        if (!current(instance, token)) {
            beginClear(instance, "cancelled during paste");
            return;
        }
        if (index >= instance.prepared.plan().placements().size()) {
            instance.detail = "setting up connections";
            try {
                instance.inFlight = world.setupConnections(worldName, instance.prepared.plan().connections());
            } catch (RuntimeException exception) {
                failAndClear(instance, "connection setup failed: " + message(exception));
                return;
            }
            instance.inFlight.whenCompleteAsync((ignored, failure) -> {
                if (!current(instance, token)) {
                    beginClear(instance, "cancelled during connection setup");
                } else if (failure != null) {
                    failAndClear(instance, "connection setup failed: " + message(failure));
                } else if (!reservations.promote(instance.instanceId)) {
                    failAndClear(instance, "player reservation promotion failed");
                } else {
                    instance.state = InstanceStatus.GENERATED;
                    instance.detail = "generation complete";
                    instance.inFlight = null;
                    diagnostics.accept("instance=" + instance.instanceId + " generated placements="
                            + instance.prepared.plan().placements().size());
                }
            }, runtimeExecutor);
            return;
        }
        Placement placement = instance.prepared.plan().placements().get(index);
        Runnable startPaste = () -> {
            if (!current(instance, token)) {
                beginClear(instance, "cancelled before paste " + index);
                return;
            }
            instance.detail = "pasting placement " + index + " " + placement.templateId();
            try {
                instance.inFlight = world.paste(worldName,
                        instance.prepared.schematic(placement.templateId()), placement.origin(),
                        placement.rotation());
            } catch (RuntimeException exception) {
                failAndClear(instance, "paste " + index + " failed: " + message(exception));
                return;
            }
            instance.inFlight.whenCompleteAsync((ignored, failure) -> {
                instance.inFlight = null;
                if (!current(instance, token)) beginClear(instance, "cancelled during paste " + index);
                else if (failure != null) failAndClear(instance, "paste " + index + " failed: " + message(failure));
                else paste(instance, index + 1, token);
            }, runtimeExecutor);
        };
        if (instance.request.debugDelayMillis() == 0) startPaste.run();
        else CompletableFuture.delayedExecutor(instance.request.debugDelayMillis(), TimeUnit.MILLISECONDS,
                runtimeExecutor).execute(startPaste);
    }

    private void failAndClear(MutableInstance instance, String detail) {
        instance.cancelled = true;
        instance.token++;
        beginClear(instance, detail);
    }

    private ActionResult beginClear(MutableInstance instance, String reason) {
        if (instance.cleanupInFlight) return ActionResult.success("cleanup already in flight");
        if (instance.prepared == null || !instance.journalAccepted) {
            releaseUnmodified(instance, reason);
            return ActionResult.success("released unmodified slot");
        }
        instance.cleanupInFlight = true;
        instance.state = InstanceStatus.CLEARING;
        instance.detail = reason;
        instance.token++;
        slots.markClearing(instance.slot.id(), instance.instanceId);
        CompletableFuture<Void> clear;
        try {
            clear = world.clear(worldName, instance.journal.plannedBounds());
        } catch (RuntimeException exception) {
            clearFailed(instance, "clear failed: " + message(exception));
            return ActionResult.failure("clear failed; slot remains blocked");
        }
        clear.whenCompleteAsync((ignored, clearFailure) -> {
            if (clearFailure != null) {
                clearFailed(instance, "clear failed: " + message(clearFailure));
                return;
            }
            repository.delete(JOURNAL_NAMESPACE, instance.instanceId.toString())
                    .whenCompleteAsync((deleted, deleteFailure) -> {
                        if (deleteFailure != null) {
                            clearFailed(instance, "journal removal failed: " + message(deleteFailure));
                            return;
                        }
                        reservations.release(instance.instanceId);
                        slots.acknowledgeClear(instance.slot.id(), instance.instanceId);
                        instance.cleanupInFlight = false;
                        instance.state = InstanceStatus.DESTROYED;
                        instance.detail = "clear ACK, journal removed, reservation released, slot FREE";
                        diagnostics.accept("instance=" + instance.instanceId + " cleanup complete");
                    }, runtimeExecutor);
        }, runtimeExecutor);
        return ActionResult.success("cleanup started");
    }

    private void clearFailed(MutableInstance instance, String detail) {
        instance.cleanupInFlight = false;
        instance.state = InstanceStatus.CLEAR_FAILED;
        instance.detail = detail;
        diagnostics.accept("P0 instance=" + instance.instanceId + " " + detail + "; slot remains blocked");
    }

    private void releaseUnmodified(MutableInstance instance, String detail) {
        if (instance.state == InstanceStatus.DESTROYED) return;
        reservations.release(instance.instanceId);
        slots.releaseUnmodified(instance.slot.id(), instance.instanceId);
        instance.state = InstanceStatus.DESTROYED;
        instance.detail = detail + "; no paste occurred";
        diagnostics.accept("instance=" + instance.instanceId + " " + instance.detail);
    }

    private void recoverNext(List<me.lidan.dungeonCrawlers.persistence.DurableRecord> records, int index,
                             boolean fatal) {
        if (index >= records.size()) {
            finishRecovery(fatal);
            return;
        }
        var record = records.get(index);
        GenerationJournal journal;
        try {
            journal = journalCodec.decode(record.payload());
            if (!journal.world().equals(worldName)) {
                throw new IllegalArgumentException("journal world " + journal.world() + " differs from " + worldName);
            }
            slots.blockForRecovery(journal.slotId(), journal.instanceId());
        } catch (RuntimeException exception) {
            recoveryBlockers.add(record.recordId() + ": " + message(exception));
            recoverNext(records, index + 1, true);
            return;
        }
        CompletableFuture<Void> clear;
        try {
            clear = world.clear(worldName, journal.plannedBounds());
        } catch (RuntimeException exception) {
            recoveryBlockers.add(journal.instanceId() + ": clear failed: " + message(exception));
            recoverNext(records, index + 1, fatal);
            return;
        }
        clear.whenCompleteAsync((ignored, clearFailure) -> {
            if (clearFailure != null) {
                recoveryBlockers.add(journal.instanceId() + ": clear failed: " + message(clearFailure));
                recoverNext(records, index + 1, fatal);
                return;
            }
            repository.delete(JOURNAL_NAMESPACE, record.recordId()).whenCompleteAsync((deleted, deleteFailure) -> {
                if (deleteFailure != null) {
                    recoveryBlockers.add(journal.instanceId() + ": journal removal failed: " + message(deleteFailure));
                } else {
                    slots.acknowledgeClear(journal.slotId(), journal.instanceId());
                    recoveryCleared++;
                }
                recoverNext(records, index + 1, fatal);
            }, runtimeExecutor);
        }, runtimeExecutor);
    }

    private void finishRecovery(boolean fatal) {
        recoveryRunning = false;
        startsEnabled = !fatal;
        diagnostics.accept("recovery discovered=" + recoveryDiscovered + " cleared=" + recoveryCleared
                + " blocked=" + recoveryBlockers.size() + " startsEnabled=" + startsEnabled);
    }

    private GenerationJournal journal(MutableInstance instance, LayoutPlan plan) {
        List<PlannedBounds> bounds = new ArrayList<>();
        plan.placements().forEach(placement -> bounds.add(planned(placement.bounds())));
        plan.connections().forEach(connection -> {
            int minX = connection.bounds().stream().mapToInt(Point::x).min().orElseThrow();
            int minY = connection.bounds().stream().mapToInt(Point::y).min().orElseThrow();
            int minZ = connection.bounds().stream().mapToInt(Point::z).min().orElseThrow();
            int maxX = connection.bounds().stream().mapToInt(Point::x).max().orElseThrow();
            int maxY = connection.bounds().stream().mapToInt(Point::y).max().orElseThrow();
            int maxZ = connection.bounds().stream().mapToInt(Point::z).max().orElseThrow();
            bounds.add(new PlannedBounds(minX, minY, minZ, maxX, maxY, maxZ));
        });
        return new GenerationJournal(instance.instanceId, instance.request.seed(), instance.slot.id(), worldName,
                bounds, instance.request.party().onlineMembers(), plan.configHash(), plan.contentHash(),
                plan.algorithmVersion(), GenerationJournal.Status.PLANNED, clock.instant());
    }

    private static PlannedBounds planned(Bounds bounds) {
        return new PlannedBounds(bounds.minimum().x(), bounds.minimum().y(), bounds.minimum().z(),
                bounds.maximum().x(), bounds.maximum().y(), bounds.maximum().z());
    }

    private static Bounds enclosing(List<PlannedBounds> bounds) {
        int minX = bounds.stream().mapToInt(PlannedBounds::minX).min().orElseThrow();
        int minY = bounds.stream().mapToInt(PlannedBounds::minY).min().orElseThrow();
        int minZ = bounds.stream().mapToInt(PlannedBounds::minZ).min().orElseThrow();
        int maxX = bounds.stream().mapToInt(PlannedBounds::maxX).max().orElseThrow();
        int maxY = bounds.stream().mapToInt(PlannedBounds::maxY).max().orElseThrow();
        int maxZ = bounds.stream().mapToInt(PlannedBounds::maxZ).max().orElseThrow();
        return new Bounds(new Point(minX, minY, minZ), new Point(maxX, maxY, maxZ));
    }

    private static float yaw(Point direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.x(), direction.z()));
    }

    private boolean current(MutableInstance instance, long token) {
        return instances.get(instance.instanceId) == instance && instance.token == token && !instance.cancelled;
    }

    private void requirePrimaryThread() {
        if (!primaryThread.getAsBoolean()) throw new IllegalStateException("generation mutation must run on Paper thread");
    }

    private static String message(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    public interface PreparationProvider {
        PreparedGeneration prepare(UUID instanceId, long seed, FloorDefinition floor, ConfigSnapshot snapshot,
                                   SlotLease slot) throws Exception;
    }

    public record PreparedGeneration(LayoutPlan plan, Map<String, byte[]> schematics) {
        public PreparedGeneration {
            Objects.requireNonNull(plan); Objects.requireNonNull(schematics);
            Map<String, byte[]> copied = new LinkedHashMap<>();
            schematics.forEach((id, bytes) -> {
                if (id == null || id.isBlank() || bytes == null || bytes.length == 0) {
                    throw new IllegalArgumentException("schematics must contain non-empty template payloads");
                }
                copied.put(id, bytes.clone());
            });
            for (Placement placement : plan.placements()) {
                if (!copied.containsKey(placement.templateId())) {
                    throw new IllegalArgumentException("missing schematic for " + placement.templateId());
                }
            }
            schematics = Map.copyOf(copied);
        }

        @Override
        public Map<String, byte[]> schematics() {
            Map<String, byte[]> copied = new LinkedHashMap<>();
            schematics.forEach((id, bytes) -> copied.put(id, bytes.clone()));
            return Map.copyOf(copied);
        }

        public byte[] schematic(String templateId) {
            byte[] schematic = schematics.get(templateId);
            if (schematic == null) throw new IllegalArgumentException("missing schematic for " + templateId);
            return schematic.clone();
        }
    }

    public record StartRequest(ConfigSnapshot snapshot, FloorDefinition floor, PartySnapshot party, long seed,
                               long debugDelayMillis) {
        public StartRequest {
            Objects.requireNonNull(snapshot); Objects.requireNonNull(floor); Objects.requireNonNull(party);
        }
    }

    public record StartResult(boolean accepted, UUID instanceId, int slotId, String detail) {
        public static StartResult failure(String detail) { return new StartResult(false, null, -1, detail); }
    }

    public record ActionResult(boolean successful, String detail) {
        public static ActionResult success(String detail) { return new ActionResult(true, detail); }
        public static ActionResult failure(String detail) { return new ActionResult(false, detail); }
    }

    public enum InstanceStatus { PLANNING, JOURNALING, PASTING, GENERATED, CANCELLING, CLEARING, CLEAR_FAILED, DESTROYED }

    public record InstanceSnapshot(UUID instanceId, int slotId, InstanceStatus status, List<UUID> participants,
                                   long seed, String detail) { }

    public record PlayerSpawn(Point point, float yaw) {
        public PlayerSpawn {
            Objects.requireNonNull(point);
            if (!Float.isFinite(yaw)) throw new IllegalArgumentException("player spawn yaw must be finite");
        }
    }

    public record RecoveryStatus(boolean startsEnabled, boolean running, int discovered, int cleared,
                                 List<String> blockers) { }

    public record InstanceRegion(String world, UUID instanceId, Bounds bounds, Set<UUID> participants) {
        public InstanceRegion {
            Objects.requireNonNull(world); Objects.requireNonNull(instanceId); Objects.requireNonNull(bounds);
            participants = Set.copyOf(participants);
        }
    }

    private static final class MutableInstance {
        private final UUID instanceId;
        private final StartRequest request;
        private final SlotLease slot;
        private long token = 1;
        private boolean cancelled;
        private boolean journalAccepted;
        private boolean cleanupInFlight;
        private InstanceStatus state = InstanceStatus.PLANNING;
        private String detail = "planning and loading templates asynchronously";
        private PreparedGeneration prepared;
        private GenerationJournal journal;
        private CompletableFuture<Void> inFlight;

        private MutableInstance(UUID instanceId, StartRequest request, SlotLease slot) {
            this.instanceId = instanceId; this.request = request; this.slot = slot;
        }

        private InstanceSnapshot snapshot() {
            return new InstanceSnapshot(instanceId, slot.id(), state, request.party().onlineMembers(),
                    request.seed(), detail);
        }
    }
}
