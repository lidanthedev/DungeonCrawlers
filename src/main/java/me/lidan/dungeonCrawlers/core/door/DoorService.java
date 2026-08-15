package me.lidan.dungeonCrawlers.core.door;

import me.lidan.dungeonCrawlers.core.template.TemplateModels.Facing;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Logical 3x3 coal door state; block editing is performed by a later adapter. */
public final class DoorService {
    private final Map<UUID, MutableDoor> doors = new LinkedHashMap<>();

    public synchronized DoorSnapshot register(UUID instanceId, Point center, Facing outward) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(outward, "outward");
        if (doors.containsKey(instanceId)) throw new IllegalStateException("door already registered");
        MutableDoor door = new MutableDoor(instanceId, center, outward);
        doors.put(instanceId, door);
        return door.snapshot();
    }

    public synchronized boolean remove(UUID instanceId) {
        return doors.remove(Objects.requireNonNull(instanceId, "instanceId")) != null;
    }

    public synchronized Optional<DoorSnapshot> info(UUID instanceId) {
        MutableDoor door = doors.get(Objects.requireNonNull(instanceId, "instanceId"));
        return door == null ? Optional.empty() : Optional.of(door.snapshot());
    }

    public synchronized DoorSnapshot setLocked(UUID instanceId) {
        MutableDoor door = require(instanceId);
        if (door.state == DoorState.OPEN) throw new IllegalStateException("open door cannot be locked");
        door.state = DoorState.LOCKED;
        return door.snapshot();
    }

    public synchronized DoorSnapshot setReady(UUID instanceId) {
        MutableDoor door = require(instanceId);
        if (door.state == DoorState.OPEN) throw new IllegalStateException("open door cannot become ready");
        door.state = DoorState.READY;
        return door.snapshot();
    }

    public OpenResult open(UUID instanceId, Runnable onOpen) {
        Objects.requireNonNull(onOpen, "onOpen");
        MutableDoor door;
        DoorSnapshot opened;
        synchronized (this) {
            door = require(instanceId);
            if (door.state == DoorState.LOCKED) return OpenResult.locked(door.snapshot());
            if (door.state == DoorState.OPEN) return OpenResult.alreadyOpen(door.snapshot());
            door.state = DoorState.OPEN;
            opened = door.snapshot();
        }
        try {
            onOpen.run();
        } catch (RuntimeException exception) {
            synchronized (this) {
                if (doors.get(instanceId) == door && door.state == DoorState.OPEN) door.state = DoorState.READY;
            }
            throw exception;
        }
        return OpenResult.opened(opened);
    }

    public synchronized Optional<DoorBlock> blockAt(UUID instanceId, Point point) {
        MutableDoor door = doors.get(Objects.requireNonNull(instanceId, "instanceId"));
        if (door == null || !door.blocks().contains(point)) return Optional.empty();
        return Optional.of(new DoorBlock(instanceId, point, door.state));
    }

    public synchronized Optional<DoorBlock> lookup(Point point) {
        Objects.requireNonNull(point, "point");
        return doors.values().stream().filter(door -> door.blocks().contains(point))
                .findFirst().map(door -> new DoorBlock(door.instanceId, point, door.state));
    }

    private MutableDoor require(UUID instanceId) {
        MutableDoor door = doors.get(Objects.requireNonNull(instanceId, "instanceId"));
        if (door == null) throw new IllegalArgumentException("unknown door " + instanceId);
        return door;
    }

    public enum DoorState { LOCKED, READY, OPEN }

    public record DoorSnapshot(UUID instanceId, Point center, Facing outward, DoorState state) {
        public DoorSnapshot {
            Objects.requireNonNull(instanceId); Objects.requireNonNull(center);
            Objects.requireNonNull(outward); Objects.requireNonNull(state);
        }

        public java.util.Set<Point> blocks() {
            return PointPlane.blocks(center, outward);
        }
    }

    public record DoorBlock(UUID instanceId, Point point, DoorState state) {
        public DoorBlock {
            Objects.requireNonNull(instanceId); Objects.requireNonNull(point); Objects.requireNonNull(state);
        }
    }

    public record OpenResult(boolean opened, boolean alreadyOpen, String detail, DoorSnapshot door) {
        public OpenResult {
            Objects.requireNonNull(detail); Objects.requireNonNull(door);
            if (opened && alreadyOpen) throw new IllegalArgumentException("open result cannot be both states");
        }

        private static OpenResult locked(DoorSnapshot door) { return new OpenResult(false, false, "door is locked", door); }
        private static OpenResult alreadyOpen(DoorSnapshot door) {
            return new OpenResult(false, true, "door already open", door);
        }
        private static OpenResult opened(DoorSnapshot door) { return new OpenResult(true, false, "door opened", door); }
    }

    private static final class MutableDoor {
        private final UUID instanceId;
        private final Point center;
        private final Facing outward;
        private DoorState state = DoorState.LOCKED;

        private MutableDoor(UUID instanceId, Point center, Facing outward) {
            this.instanceId = instanceId; this.center = center; this.outward = outward;
        }

        private DoorSnapshot snapshot() { return new DoorSnapshot(instanceId, center, outward, state); }

        private java.util.Set<Point> blocks() { return PointPlane.blocks(center, outward); }
    }

    private static final class PointPlane {
        private static java.util.Set<Point> blocks(Point center, Facing outward) {
            return me.lidan.dungeonCrawlers.core.template.TemplateModels.plane(center, outward);
        }
    }
}
