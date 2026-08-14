package me.lidan.dungeonCrawlers.core.generation;

import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SlotAllocator {
    private static final int MAX_WORLD_COORDINATE = 29_999_984;
    private final Map<Integer, MutableSlot> slots = new LinkedHashMap<>();

    public SlotAllocator(Settings settings) {
        Objects.requireNonNull(settings, "settings");
        int columns = (int) Math.ceil(Math.sqrt(settings.capacity()));
        for (int index = 0; index < settings.capacity(); index++) {
            int cellX = (index % columns) * settings.spacing();
            int cellZ = (index / columns) * settings.spacing();
            Bounds usable = new Bounds(
                    new Point(cellX + settings.margin(), settings.minimumY(), cellZ + settings.margin()),
                    new Point(cellX + settings.spacing() - settings.margin() - 1, settings.maximumY(),
                            cellZ + settings.spacing() - settings.margin() - 1));
            Point origin = new Point(cellX + settings.spacing() / 2, settings.baseY(),
                    cellZ + settings.spacing() / 2);
            slots.put(index, new MutableSlot(index, origin, usable));
        }
    }

    public synchronized boolean hasFreeSlot() {
        return slots.values().stream().anyMatch(slot -> slot.state == SlotState.FREE);
    }

    public synchronized Optional<SlotLease> allocate(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        MutableSlot slot = slots.values().stream().filter(value -> value.state == SlotState.FREE).findFirst().orElse(null);
        if (slot == null) return Optional.empty();
        slot.state = SlotState.ALLOCATED;
        slot.instanceId = instanceId;
        return Optional.of(slot.snapshot());
    }

    public synchronized SlotLease markPasting(int slotId, UUID instanceId) {
        MutableSlot slot = owned(slotId, instanceId);
        requireState(slot, SlotState.ALLOCATED);
        slot.state = SlotState.PASTING;
        return slot.snapshot();
    }

    public synchronized SlotLease markClearing(int slotId, UUID instanceId) {
        MutableSlot slot = owned(slotId, instanceId);
        if (slot.state != SlotState.ALLOCATED && slot.state != SlotState.PASTING
                && slot.state != SlotState.CLEARING) {
            throw new IllegalStateException("slot " + slotId + " cannot clear from " + slot.state);
        }
        slot.state = SlotState.CLEARING;
        return slot.snapshot();
    }

    public synchronized SlotLease blockForRecovery(int slotId, UUID instanceId) {
        MutableSlot slot = slot(slotId);
        if (slot.state == SlotState.CLEARING && instanceId.equals(slot.instanceId)) return slot.snapshot();
        requireState(slot, SlotState.FREE);
        slot.state = SlotState.CLEARING;
        slot.instanceId = Objects.requireNonNull(instanceId, "instanceId");
        return slot.snapshot();
    }

    public synchronized void releaseUnmodified(int slotId, UUID instanceId) {
        MutableSlot slot = owned(slotId, instanceId);
        requireState(slot, SlotState.ALLOCATED);
        free(slot);
    }

    public synchronized void acknowledgeClear(int slotId, UUID instanceId) {
        MutableSlot slot = owned(slotId, instanceId);
        requireState(slot, SlotState.CLEARING);
        free(slot);
    }

    public synchronized Optional<SlotLease> lookup(int slotId) {
        MutableSlot slot = slots.get(slotId);
        return slot == null ? Optional.empty() : Optional.of(slot.snapshot());
    }

    public synchronized List<SlotLease> snapshot() {
        return slots.values().stream().map(MutableSlot::snapshot)
                .sorted(Comparator.comparingInt(SlotLease::id)).toList();
    }

    private MutableSlot owned(int slotId, UUID instanceId) {
        MutableSlot slot = slot(slotId);
        if (!Objects.equals(instanceId, slot.instanceId)) {
            throw new IllegalStateException("slot " + slotId + " is not owned by " + instanceId);
        }
        return slot;
    }

    private MutableSlot slot(int slotId) {
        MutableSlot slot = slots.get(slotId);
        if (slot == null) throw new IllegalArgumentException("unknown slot " + slotId);
        return slot;
    }

    private static void requireState(MutableSlot slot, SlotState expected) {
        if (slot.state != expected) {
            throw new IllegalStateException("slot " + slot.id + " must be " + expected + "; was " + slot.state);
        }
    }

    private static void free(MutableSlot slot) {
        slot.state = SlotState.FREE;
        slot.instanceId = null;
    }

    public enum SlotState { ALLOCATED, PASTING, CLEARING, FREE }

    public record Settings(int capacity, int spacing, int margin, int baseY, int minimumY, int maximumY) {
        public Settings {
            if (capacity < 1 || capacity > 10_000) throw new IllegalArgumentException("capacity must be in 1..10000");
            if (spacing < 1 || margin < 0 || (long) margin * 2 >= spacing) {
                throw new IllegalArgumentException("slot spacing must exceed twice its margin");
            }
            int columns = (int) Math.ceil(Math.sqrt(capacity));
            long maximumCoordinate = (long) columns * spacing - margin - 1;
            if (maximumCoordinate > MAX_WORLD_COORDINATE) {
                throw new IllegalArgumentException("slot grid exceeds the Minecraft world coordinate limit");
            }
            if (minimumY > baseY || baseY > maximumY) {
                throw new IllegalArgumentException("base Y must be within the vertical bounds");
            }
        }
    }

    public record SlotLease(int id, UUID instanceId, SlotState state, Point origin, Bounds usableBounds) {
        public SlotLease {
            Objects.requireNonNull(state); Objects.requireNonNull(origin); Objects.requireNonNull(usableBounds);
            if (state == SlotState.FREE && instanceId != null) throw new IllegalArgumentException("free slot has an owner");
            if (state != SlotState.FREE && instanceId == null) throw new IllegalArgumentException("leased slot needs an owner");
        }
    }

    private static final class MutableSlot {
        private final int id;
        private final Point origin;
        private final Bounds usableBounds;
        private SlotState state = SlotState.FREE;
        private UUID instanceId;

        private MutableSlot(int id, Point origin, Bounds usableBounds) {
            this.id = id; this.origin = origin; this.usableBounds = usableBounds;
        }

        private SlotLease snapshot() {
            return new SlotLease(id, instanceId, state, origin, usableBounds);
        }
    }
}
