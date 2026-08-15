package me.lidan.dungeonCrawlers.core.combat;

import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Main-thread combat lifecycle. The service owns required mob sets and never
 * advances a room from an unobserved or administratively removed entity.
 */
public final class CombatRoomService {
    private final CombatMobGateway mobs;
    private final CombatChunkGateway chunks;
    private final Consumer<String> diagnostics;
    private final Consumer<RoomNotice> notices;
    private final Map<UUID, MutableInstance> instances = new LinkedHashMap<>();

    public CombatRoomService(CombatMobGateway mobs, CombatChunkGateway chunks, Consumer<String> diagnostics) {
        this(mobs, chunks, diagnostics, ignored -> { });
    }

    public CombatRoomService(CombatMobGateway mobs, CombatChunkGateway chunks, Consumer<String> diagnostics,
                             Consumer<RoomNotice> notices) {
        this.mobs = Objects.requireNonNull(mobs, "mobs");
        this.chunks = Objects.requireNonNull(chunks, "chunks");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.notices = Objects.requireNonNull(notices, "notices");
    }

    public synchronized RegistrationResult register(GenerationService.CombatPlan plan) {
        Objects.requireNonNull(plan, "plan");
        if (instances.containsKey(plan.instanceId())) {
            return RegistrationResult.failure("combat already registered for " + plan.instanceId());
        }
        if (plan.rooms().isEmpty()) return RegistrationResult.failure("generated plan has no combat rooms");
        if (plan.respawnRetries() < 0) return RegistrationResult.failure("mob respawn retries must not be negative");
        if (plan.rooms().stream().anyMatch(room -> !room.normalMarkers().isEmpty() && plan.normalMobs().isEmpty())) {
            return RegistrationResult.failure("floor normal mob list is empty but a normal marker is required");
        }
        if (plan.rooms().stream().anyMatch(room -> !room.minibossMarkers().isEmpty() && plan.minibossMobs().isEmpty())) {
            return RegistrationResult.failure("floor miniboss mob list is empty but a miniboss marker is required");
        }
        List<MutableRoom> rooms = plan.rooms().stream()
                .sorted(java.util.Comparator.comparingInt(GenerationService.CombatRoom::index))
                .map(room -> new MutableRoom(room, plan.normalMobs(), plan.minibossMobs(), plan.respawnRetries()))
                .toList();
        MutableInstance instance = new MutableInstance(plan, rooms);
        rooms.getFirst().state = RoomState.READY;
        instances.put(plan.instanceId(), instance);
        return RegistrationResult.success("combat registered", snapshot(instance));
    }

    public synchronized ActivationResult activateFirst(UUID instanceId) {
        MutableInstance instance = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        if (instance == null) return ActivationResult.failure("unknown combat instance " + instanceId);
        return activate(instance, instance.rooms.getFirst().room.index());
    }

    public synchronized ActivationResult activate(UUID instanceId, int roomIndex) {
        MutableInstance instance = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        if (instance == null) return ActivationResult.failure("unknown combat instance " + instanceId);
        return activate(instance, roomIndex);
    }

    private ActivationResult activate(MutableInstance instance, int roomIndex) {
        MutableRoom room = room(instance, roomIndex).orElse(null);
        if (room == null) return ActivationResult.failure("unknown combat room " + roomIndex);
        if (room.state == RoomState.ACTIVE) return ActivationResult.success("room already active", snapshot(instance));
        if (room.state == RoomState.CLEARED) return ActivationResult.success("room already cleared", snapshot(instance));
        if (room.state == RoomState.FAILED) return ActivationResult.failure(room.detail);
        if (room.state != RoomState.READY) return ActivationResult.failure("room " + roomIndex + " is locked");
        if (!chunks.acquire(instance.plan.instanceId(), room.room.bounds())) {
            room.state = RoomState.FAILED;
            room.detail = "chunk-ticket budget exhausted for room " + roomIndex;
            diagnose(instance, room.detail);
            return ActivationResult.failure(room.detail);
        }
        room.ticketsHeld = true;
        room.state = RoomState.ACTIVE;
        room.detail = "spawning required mobs";
        for (MobRequirement requirement : room.requirements) {
            if (!spawn(instance, room, requirement)) {
                room.state = RoomState.FAILED;
                room.detail = "room " + roomIndex + " spawn exhausted: " + requirement.detail;
                releaseEntities(room);
                chunks.release(instance.plan.instanceId(), room.room.bounds());
                room.ticketsHeld = false;
                diagnose(instance, room.detail);
                return ActivationResult.failure(room.detail);
            }
        }
        room.detail = "room active; required mobs=" + room.requirements.size();
        return ActivationResult.success(room.detail, snapshot(instance));
    }

    /** Activates the target room only when its previous room has cleared. */
    public synchronized ActivationResult activateFromDoor(UUID instanceId, Point clickedBlock) {
        MutableInstance instance = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        Objects.requireNonNull(clickedBlock, "clickedBlock");
        if (instance == null) return ActivationResult.failure("unknown combat instance " + instanceId);
        GenerationService.RoomLink link = instance.plan.links().stream()
                .filter(candidate -> candidate.triggerBlocks().contains(clickedBlock))
                .findFirst().orElse(null);
        if (link == null) return ActivationResult.failure("no combat door at " + clickedBlock);
        MutableRoom previous = room(instance, link.fromIndex()).orElse(null);
        MutableRoom target = room(instance, link.toIndex()).orElse(null);
        if (previous == null) return ActivationResult.failure("combat door is not unlocked yet");
        if (target == null) return ActivationResult.failure("door target is not a combat room");
        if (previous != null && previous.state != RoomState.CLEARED) {
            return ActivationResult.failure("previous room " + previous.room.index() + " is not cleared");
        }
        ActivationResult result = activate(instance, target.room.index());
        return result.successful() ? result.withOpenedDoorBlocks(link.triggerBlocks()) : result;
    }

    public synchronized boolean isDoorAt(Point clickedBlock) {
        Objects.requireNonNull(clickedBlock, "clickedBlock");
        return instances.values().stream().anyMatch(instance -> instance.plan.links().stream()
                .anyMatch(link -> room(instance, link.fromIndex()).isPresent()
                        && link.triggerBlocks().contains(clickedBlock)));
    }

    public synchronized ActivationResult activateAt(Point clickedBlock) {
        Objects.requireNonNull(clickedBlock, "clickedBlock");
        for (MutableInstance instance : instances.values()) {
            if (instance.plan.links().stream().anyMatch(link -> link.triggerBlocks().contains(clickedBlock))) {
                return activateFromDoor(instance.plan.instanceId(), clickedBlock);
            }
        }
        return ActivationResult.failure("no combat door at " + clickedBlock);
    }

    /** A normal death consumes the requirement and can clear the room. */
    public synchronized EventResult onDeath(UUID instanceId, int roomIndex, UUID entityId) {
        return reconcileEvent(instanceId, roomIndex, entityId, EventKind.DEATH);
    }

    /** A despawn, invalid entity, or world removal is unexpected and respawns within budget. */
    public synchronized EventResult onRemoved(UUID instanceId, int roomIndex, UUID entityId) {
        return reconcileEvent(instanceId, roomIndex, entityId, EventKind.REMOVED);
    }

    private EventResult reconcileEvent(UUID instanceId, int roomIndex, UUID entityId, EventKind kind) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(entityId, "entityId");
        MutableInstance instance = instances.get(instanceId);
        if (instance == null) return EventResult.ignored("unknown combat instance");
        MutableRoom room = room(instance, roomIndex).orElse(null);
        if (room == null) return EventResult.ignored("unknown combat room");
        MobRequirement requirement = room.byEntity.remove(entityId);
        if (requirement == null) return EventResult.ignored("entity is not a required mob");
        requirement.entityId = null;
        if (requirement.adminSuppressed) return EventResult.ignored("admin removal suppressed progression");
        if (kind == EventKind.DEATH) {
            requirement.state = MobState.DEAD;
            room.detail = "required mob defeated";
            maybeClear(instance, room);
            return EventResult.accepted(room.state == RoomState.CLEARED ? "room cleared" : "required mob defeated",
                    snapshot(instance));
        }
        if (room.state != RoomState.ACTIVE) return EventResult.ignored("room is not active");
        requirement.state = MobState.MISSING;
        if (!recover(instance, room, requirement)) {
            room.state = RoomState.FAILED;
            room.detail = "room " + room.room.index() + " respawn exhausted: " + requirement.detail;
            releaseEntities(room);
            releaseTickets(instance, room);
            diagnose(instance, room.detail);
            return EventResult.accepted(room.detail, snapshot(instance));
        }
        room.detail = "required mob respawned";
        return EventResult.accepted(room.detail, snapshot(instance));
    }

    private boolean spawn(MutableInstance instance, MutableRoom room, MobRequirement requirement) {
        while (requirement.attempts <= room.retries) {
            requirement.attempts++;
            CombatMobGateway.SpawnResult result = mobs.spawn(instance.plan.instanceId(), room.room.index(),
                    requirement.mobId, requirement.point);
            if (result.successful() && result.entityId() != null) {
                requirement.entityId = result.entityId();
                requirement.state = MobState.ALIVE;
                requirement.attempts = 0;
                requirement.detail = result.detail();
                room.byEntity.put(result.entityId(), requirement);
                return true;
            }
            requirement.detail = result.detail();
        }
        requirement.state = MobState.FAILED;
        return false;
    }

    private void maybeClear(MutableInstance instance, MutableRoom room) {
        if (room.state != RoomState.ACTIVE || room.requirements.stream().anyMatch(requirement ->
                requirement.state != MobState.DEAD)) return;
        room.state = RoomState.CLEARED;
        room.detail = "room cleared";
        releaseTickets(instance, room);
        int position = instance.rooms.indexOf(room);
        int unlockedRoom = -1;
        if (position >= 0 && position + 1 < instance.rooms.size()) {
            MutableRoom next = instance.rooms.get(position + 1);
            if (next.state == RoomState.LOCKED) {
                next.state = RoomState.READY;
                next.detail = "ready after room " + room.room.index();
                unlockedRoom = next.room.index();
            }
        }
        notifyRoom(instance, room.room.index(), unlockedRoom);
    }

    public synchronized ClearResult clear(UUID instanceId, int roomIndex) {
        MutableInstance instance = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        if (instance == null) return ClearResult.failure("unknown combat instance " + instanceId);
        MutableRoom room = room(instance, roomIndex).orElse(null);
        if (room == null) return ClearResult.failure("unknown combat room " + roomIndex);
        room.adminSuppression = true;
        room.requirements.forEach(requirement -> {
            if (requirement.entityId != null) mobs.remove(requirement.entityId);
            requirement.entityId = null;
            requirement.adminSuppressed = true;
            requirement.state = MobState.DEAD;
        });
        room.byEntity.clear();
        room.state = RoomState.CLEARED;
        room.detail = "admin cleared";
        releaseTickets(instance, room);
        int position = instance.rooms.indexOf(room);
        int unlockedRoom = -1;
        if (position >= 0 && position + 1 < instance.rooms.size() && instance.rooms.get(position + 1).state == RoomState.LOCKED) {
            instance.rooms.get(position + 1).state = RoomState.READY;
            instance.rooms.get(position + 1).detail = "ready after room " + room.room.index();
            unlockedRoom = instance.rooms.get(position + 1).room.index();
        }
        notifyRoom(instance, room.room.index(), unlockedRoom);
        return ClearResult.success("room cleared", snapshot(instance));
    }

    public synchronized AdminResult remove(UUID instanceId, int roomIndex, UUID entityId) {
        MutableInstance instance = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        if (instance == null) return AdminResult.failure("unknown combat instance " + instanceId);
        MutableRoom room = room(instance, roomIndex).orElse(null);
        if (room == null) return AdminResult.failure("unknown combat room " + roomIndex);
        MobRequirement requirement = room.byEntity.get(Objects.requireNonNull(entityId, "entityId"));
        if (requirement == null) return AdminResult.failure("entity is not a required mob");
        requirement.adminSuppressed = true;
        requirement.detail = "admin removal suppressed progression";
        boolean removed = mobs.remove(entityId);
        return AdminResult.success(removed ? "mob removed; progression suppressed" : "mob already absent; progression suppressed",
                snapshot(instance));
    }

    public synchronized AdminResult kill(UUID instanceId, int roomIndex, UUID entityId) {
        MutableInstance instance = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        if (instance == null) return AdminResult.failure("unknown combat instance " + instanceId);
        MutableRoom room = room(instance, roomIndex).orElse(null);
        if (room == null) return AdminResult.failure("unknown combat room " + roomIndex);
        MobRequirement requirement = room.byEntity.remove(Objects.requireNonNull(entityId, "entityId"));
        if (requirement == null) return AdminResult.failure("entity is not a required mob");
        mobs.remove(entityId);
        requirement.entityId = null;
        requirement.state = MobState.DEAD;
        requirement.detail = "admin kill";
        maybeClear(instance, room);
        return AdminResult.success(room.state == RoomState.CLEARED ? "mob killed; room cleared" : "mob killed",
                snapshot(instance));
    }

    public synchronized AdminResult spawn(UUID instanceId, int roomIndex, String mobId) {
        MutableInstance instance = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        if (instance == null) return AdminResult.failure("unknown combat instance " + instanceId);
        MutableRoom room = room(instance, roomIndex).orElse(null);
        if (room == null) return AdminResult.failure("unknown combat room " + roomIndex);
        if (room.state != RoomState.ACTIVE) return AdminResult.failure("room " + roomIndex + " is not active");
        Objects.requireNonNull(mobId, "mobId");
        Point point = room.room.normalMarkers().stream().findFirst()
                .or(() -> room.room.minibossMarkers().stream().findFirst()).orElse(null);
        if (point == null) return AdminResult.failure("room has no mob marker");
        CombatMobGateway.SpawnResult result = mobs.spawn(instanceId, roomIndex, mobId, point);
        return result.successful() ? AdminResult.success("admin mob spawned " + result.entityId(), snapshot(instance))
                : AdminResult.failure("admin mob spawn failed: " + result.detail());
    }

    public synchronized ReconcileResult reconcile(UUID instanceId) {
        MutableInstance instance = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        if (instance == null) return ReconcileResult.failure("unknown combat instance " + instanceId);
        int repaired = 0;
        for (MutableRoom room : instance.rooms) {
            if (room.state != RoomState.ACTIVE) continue;
            for (MobRequirement requirement : room.requirements) {
                if (requirement.entityId == null || requirement.adminSuppressed || mobs.isValid(requirement.entityId)) continue;
                UUID missing = requirement.entityId;
                room.byEntity.remove(missing);
                requirement.entityId = null;
                requirement.state = MobState.MISSING;
                if (recover(instance, room, requirement)) repaired++;
                else {
                    room.state = RoomState.FAILED;
                    room.detail = "room " + room.room.index() + " respawn exhausted: " + requirement.detail;
                    releaseEntities(room);
                    releaseTickets(instance, room);
                    diagnose(instance, room.detail);
                    return ReconcileResult.success("reconciliation exhausted on " + missing, repaired,
                            snapshot(instance));
                }
            }
        }
        return ReconcileResult.success("reconciled missing required mobs=" + repaired, repaired, snapshot(instance));
    }

    public synchronized List<RoomSnapshot> rooms(UUID instanceId) {
        MutableInstance instance = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        return instance == null ? List.of() : instance.rooms.stream().map(this::snapshot).toList();
    }

    public synchronized Optional<InstanceSnapshot> info(UUID instanceId) {
        MutableInstance instance = instances.get(Objects.requireNonNull(instanceId, "instanceId"));
        return instance == null ? Optional.empty() : Optional.of(snapshot(instance));
    }

    public synchronized List<InstanceSnapshot> instances() {
        return instances.values().stream().map(this::snapshot)
                .sorted(Comparator.comparing(InstanceSnapshot::instanceId)).toList();
    }

    public synchronized void cleanup(UUID instanceId) {
        MutableInstance instance = instances.remove(Objects.requireNonNull(instanceId, "instanceId"));
        if (instance == null) return;
        instance.rooms.forEach(room -> {
            room.requirements.forEach(requirement -> {
                if (requirement.entityId != null) mobs.remove(requirement.entityId);
            });
            room.byEntity.clear();
            releaseTickets(instance, room);
        });
        chunks.releaseAll(instance.plan.instanceId());
    }

    private void releaseTickets(MutableInstance instance, MutableRoom room) {
        if (!room.ticketsHeld) return;
        chunks.release(instance.plan.instanceId(), room.room.bounds());
        room.ticketsHeld = false;
    }

    private void releaseEntities(MutableRoom room) {
        room.requirements.forEach(requirement -> {
            if (requirement.entityId != null) mobs.remove(requirement.entityId);
            requirement.entityId = null;
        });
        room.byEntity.clear();
    }

    private Optional<MutableRoom> room(MutableInstance instance, int index) {
        return instance.rooms.stream().filter(room -> room.room.index() == index).findFirst();
    }

    private RoomSnapshot snapshot(MutableRoom room) {
        List<MobSnapshot> mobs = room.requirements.stream().map(requirement -> new MobSnapshot(
                requirement.mobId, requirement.point, requirement.entityId, requirement.state,
                requirement.attempts, requirement.adminSuppressed, requirement.detail)).toList();
        return new RoomSnapshot(room.room.index(), room.room.templateId(), room.state, room.detail, mobs);
    }

    private InstanceSnapshot snapshot(MutableInstance instance) {
        return new InstanceSnapshot(instance.plan.instanceId(), instance.rooms.stream().map(this::snapshot).toList());
    }

    private void diagnose(MutableInstance instance, String detail) {
        try {
            diagnostics.accept("instance=" + instance.plan.instanceId() + " " + detail);
        } catch (RuntimeException ignored) {
            // Diagnostics must not interrupt combat reconciliation.
        }
    }

    private boolean recover(MutableInstance instance, MutableRoom room, MobRequirement requirement) {
        if (requirement.respawnsRemaining <= 0) {
            requirement.state = MobState.FAILED;
            requirement.detail = "respawn retry budget exhausted";
            return false;
        }
        requirement.respawnsRemaining--;
        return spawn(instance, room, requirement);
    }

    private void notifyRoom(MutableInstance instance, int clearedRoom, int unlockedRoom) {
        try {
            notices.accept(new RoomNotice(instance.plan.instanceId(), clearedRoom, unlockedRoom));
        } catch (RuntimeException ignored) {
            // Player-facing notices must not interrupt room progression.
        }
    }

    private static List<MobRequirement> requirements(GenerationService.CombatRoom room,
                                                       List<String> normalMobs, List<String> minibossMobs,
                                                       int respawnRetries) {
        List<MobRequirement> result = new ArrayList<>();
        int index = 0;
        for (Point point : room.normalMarkers()) {
            result.add(new MobRequirement(normalMobs.get(index++ % normalMobs.size()), point, respawnRetries));
        }
        index = 0;
        for (Point point : room.minibossMarkers()) {
            result.add(new MobRequirement(minibossMobs.get(index++ % minibossMobs.size()), point, respawnRetries));
        }
        return result;
    }

    private static final class MutableInstance {
        private final GenerationService.CombatPlan plan;
        private final List<MutableRoom> rooms;

        private MutableInstance(GenerationService.CombatPlan plan, List<MutableRoom> rooms) {
            this.plan = plan;
            this.rooms = new ArrayList<>(rooms);
        }
    }

    private static final class MutableRoom {
        private final GenerationService.CombatRoom room;
        private final int retries;
        private final List<MobRequirement> requirements;
        private final Map<UUID, MobRequirement> byEntity = new LinkedHashMap<>();
        private RoomState state = RoomState.LOCKED;
        private boolean ticketsHeld;
        private boolean adminSuppression;
        private String detail = "locked";

        private MutableRoom(GenerationService.CombatRoom room, List<String> normalMobs,
                            List<String> minibossMobs, int retries) {
            this.room = room;
            this.retries = retries;
            this.requirements = requirements(room, normalMobs, minibossMobs, retries);
        }
    }

    private static final class MobRequirement {
        private final String mobId;
        private final Point point;
        private UUID entityId;
        private MobState state = MobState.MISSING;
        private int attempts;
        private int respawnsRemaining;
        private boolean adminSuppressed;
        private String detail = "not spawned";

        private MobRequirement(String mobId, Point point, int retries) {
            this.mobId = Objects.requireNonNull(mobId);
            this.point = Objects.requireNonNull(point);
            this.respawnsRemaining = retries;
        }
    }

    private enum EventKind { DEATH, REMOVED }

    public enum RoomState { LOCKED, READY, ACTIVE, CLEARED, FAILED }

    public enum MobState { MISSING, ALIVE, DEAD, FAILED }

    public record RegistrationResult(boolean successful, String detail, InstanceSnapshot snapshot) {
        public static RegistrationResult success(String detail, InstanceSnapshot snapshot) {
            return new RegistrationResult(true, detail, snapshot);
        }
        public static RegistrationResult failure(String detail) { return new RegistrationResult(false, detail, null); }
    }

    public record ActivationResult(boolean successful, String detail, InstanceSnapshot snapshot,
                                   Set<Point> openedDoorBlocks) {
        public ActivationResult {
            Objects.requireNonNull(detail);
            openedDoorBlocks = Set.copyOf(openedDoorBlocks);
        }

        public static ActivationResult success(String detail, InstanceSnapshot snapshot) {
            return new ActivationResult(true, detail, snapshot, Set.of());
        }
        public static ActivationResult failure(String detail) {
            return new ActivationResult(false, detail, null, Set.of());
        }

        private ActivationResult withOpenedDoorBlocks(Set<Point> blocks) {
            return new ActivationResult(successful, detail, snapshot, blocks);
        }
    }

    public record EventResult(boolean accepted, String detail, InstanceSnapshot snapshot) {
        public static EventResult accepted(String detail, InstanceSnapshot snapshot) {
            return new EventResult(true, detail, snapshot);
        }
        public static EventResult ignored(String detail) { return new EventResult(false, detail, null); }
    }

    public record RoomNotice(UUID instanceId, int clearedRoom, int unlockedRoom) {
        public RoomNotice {
            Objects.requireNonNull(instanceId, "instanceId");
            if (clearedRoom < 0 || unlockedRoom < -1) {
                throw new IllegalArgumentException("room indexes must be valid");
            }
        }
    }

    public record ClearResult(boolean successful, String detail, InstanceSnapshot snapshot) {
        public static ClearResult success(String detail, InstanceSnapshot snapshot) {
            return new ClearResult(true, detail, snapshot);
        }
        public static ClearResult failure(String detail) { return new ClearResult(false, detail, null); }
    }

    public record AdminResult(boolean successful, String detail, InstanceSnapshot snapshot) {
        public static AdminResult success(String detail, InstanceSnapshot snapshot) {
            return new AdminResult(true, detail, snapshot);
        }
        public static AdminResult failure(String detail) { return new AdminResult(false, detail, null); }
    }

    public record ReconcileResult(boolean successful, String detail, int repaired, InstanceSnapshot snapshot) {
        public static ReconcileResult success(String detail, int repaired, InstanceSnapshot snapshot) {
            return new ReconcileResult(true, detail, repaired, snapshot);
        }
        public static ReconcileResult failure(String detail) { return new ReconcileResult(false, detail, 0, null); }
    }

    public record InstanceSnapshot(UUID instanceId, List<RoomSnapshot> rooms) {
        public InstanceSnapshot { Objects.requireNonNull(instanceId); rooms = List.copyOf(rooms); }
    }

    public record RoomSnapshot(int index, String templateId, RoomState state, String detail,
                               List<MobSnapshot> requiredMobs) {
        public RoomSnapshot {
            Objects.requireNonNull(templateId); Objects.requireNonNull(state); Objects.requireNonNull(detail);
            requiredMobs = List.copyOf(requiredMobs);
        }
    }

    public record MobSnapshot(String mobId, Point point, UUID entityId, MobState state, int attempts,
                              boolean adminSuppressed, String detail) {
        public MobSnapshot {
            Objects.requireNonNull(mobId); Objects.requireNonNull(point); Objects.requireNonNull(state);
            Objects.requireNonNull(detail);
        }
    }
}
