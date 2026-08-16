package me.lidan.dungeonCrawlers.core.location;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.EncounterCapability;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomType;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner.LayoutPlan;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner.Placement;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only runtime location context. Looking up a location never changes room
 * state or activates combat; progression remains owned by the door/combat
 * services.
 */
public final class LocationContextService {
    private final Map<UUID, List<RoomContext>> instances = new LinkedHashMap<>();

    public synchronized RegistrationResult register(LayoutPlan plan) {
        Objects.requireNonNull(plan, "plan");
        if (instances.containsKey(plan.instanceId())) {
            return RegistrationResult.failure("location context already registered");
        }
        List<RoomContext> rooms = plan.placements().stream()
                .sorted(Comparator.comparingInt(Placement::index))
                .map(placement -> new RoomContext(plan.instanceId(), placement.index(), placement.templateId(),
                        placement.type(), placement.encounter(), placement.bounds()))
                .toList();
        if (rooms.isEmpty()) return RegistrationResult.failure("layout has no placements");
        instances.put(plan.instanceId(), List.copyOf(rooms));
        return RegistrationResult.success("location context registered", rooms);
    }

    public synchronized Optional<RoomContext> locate(UUID instanceId, Point point) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(point, "point");
        return instances.getOrDefault(instanceId, List.of()).stream()
                .filter(room -> room.bounds().contains(point))
                .findFirst();
    }

    public synchronized List<RoomContext> rooms(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        return instances.getOrDefault(instanceId, List.of());
    }

    public synchronized Optional<UUID> instanceAt(Point point) {
        Objects.requireNonNull(point, "point");
        return instances.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(room -> room.bounds().contains(point)))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public synchronized boolean unregister(UUID instanceId) {
        return instances.remove(Objects.requireNonNull(instanceId, "instanceId")) != null;
    }

    public synchronized void unregisterAll() {
        instances.clear();
    }

    public record RoomContext(UUID instanceId, int index, String templateId, RoomType type,
                              EncounterCapability encounter, Bounds bounds) {
        public RoomContext {
            Objects.requireNonNull(instanceId, "instanceId");
            if (index < 0) throw new IllegalArgumentException("room index must not be negative");
            Objects.requireNonNull(templateId, "templateId");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(bounds, "bounds");
        }

        public boolean miniboss() {
            return encounter == EncounterCapability.MINIBOSS;
        }
    }

    public record RegistrationResult(boolean successful, String detail, List<RoomContext> rooms) {
        public RegistrationResult {
            Objects.requireNonNull(detail, "detail");
            rooms = List.copyOf(rooms);
        }

        public static RegistrationResult success(String detail, List<RoomContext> rooms) {
            return new RegistrationResult(true, detail, rooms);
        }

        public static RegistrationResult failure(String detail) {
            return new RegistrationResult(false, detail, List.of());
        }
    }
}
