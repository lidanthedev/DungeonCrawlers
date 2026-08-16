package me.lidan.dungeonCrawlers.core.location;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.EncounterCapability;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomType;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Rotation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationContextServiceTest {
    @Test
    void lookupReportsRoomAndMinibossWithoutChangingState() {
        UUID instance = UUID.randomUUID();
        LayoutPlanner.LayoutPlan plan = new LayoutPlanner.LayoutPlan("phase2-v1", instance, 7,
                "config", "content", List.of(
                placement(instance, 0, RoomType.START, null,
                        new Bounds(new Point(0, 0, 0), new Point(4, 4, 4))),
                placement(instance, 1, RoomType.NORMAL, EncounterCapability.MINIBOSS,
                        new Bounds(new Point(10, 0, 0), new Point(14, 4, 4)))), List.of(), List.of());
        LocationContextService service = new LocationContextService();

        assertTrue(service.register(plan).successful());
        var context = service.locate(instance, new Point(12, 2, 2)).orElseThrow();
        assertEquals(1, context.index());
        assertTrue(context.miniboss());
        assertTrue(service.locate(instance, new Point(12, 2, 2)).isPresent());
        assertFalse(service.locate(instance, new Point(20, 2, 2)).isPresent());
        assertEquals(2, service.rooms(instance).size());
    }

    private static LayoutPlanner.Placement placement(UUID instance, int index, RoomType type,
                                                     EncounterCapability encounter, Bounds bounds) {
        return new LayoutPlanner.Placement(index, "room-" + index, type, encounter, Rotation.NONE,
                bounds.minimum(), bounds, Optional.empty(), Optional.empty(), Set.of(), Set.of(),
                List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), Set.of(), List.of());
    }
}
