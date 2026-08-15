package me.lidan.dungeonCrawlers.core.protection;

import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldProtectionServiceTest {
    @Test
    void protectsGeometryAndUnauthorizedEntry() {
        WorldProtectionService service = new WorldProtectionService();
        UUID instance = UUID.randomUUID();
        UUID participant = UUID.randomUUID();
        List<WorldProtectionService.InstanceRegion> regions = List.of(region(instance, participant));

        assertFalse(service.canModifyGeometry("dungeon_instances", new Point(0, 64, 0), regions).allowed());
        assertTrue(service.canModifyGeometry("world", new Point(0, 64, 0), regions).allowed());
        assertTrue(service.canEnter(participant, "dungeon_instances", new Point(0, 64, 0), regions).allowed());
        assertFalse(service.canEnter(UUID.randomUUID(), "dungeon_instances", new Point(0, 64, 0), regions).allowed());
    }

    @Test
    void deniesPvpCrossInstanceDamageAndUnauthorizedTeleports() {
        WorldProtectionService service = new WorldProtectionService();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        UUID secondParticipant = UUID.randomUUID();
        List<WorldProtectionService.InstanceRegion> regions = List.of(region(first, player),
                region(second, secondParticipant, new Point(10, 64, 10)));

        assertFalse(service.canDamage(player, "dungeon_instances", new Point(0, 64, 0),
                UUID.randomUUID(), "dungeon_instances", new Point(0, 64, 0), regions).allowed());
        assertFalse(service.canDamage(player, "dungeon_instances", new Point(0, 64, 0),
                secondParticipant, "dungeon_instances", new Point(10, 64, 10), regions).allowed());
        assertFalse(service.canTeleport(UUID.randomUUID(), "world", new Point(100, 64, 100),
                "dungeon_instances", new Point(0, 64, 0), false, regions).allowed());
        assertTrue(service.canTeleport(player, "dungeon_instances", new Point(0, 64, 0),
                "world", new Point(0, 64, 0), true, regions).allowed());
        assertFalse(service.canTeleport(UUID.randomUUID(), "world", new Point(0, 64, 0),
                "dungeon_instances", new Point(0, 64, 0), true, regions).allowed());
    }

    private static WorldProtectionService.InstanceRegion region(UUID instance, UUID participant) {
        return region(instance, participant, new Point(0, 64, 0));
    }

    private static WorldProtectionService.InstanceRegion region(UUID instance, UUID participant, Point point) {
        return new WorldProtectionService.InstanceRegion("dungeon_instances", instance,
                new Bounds(point, point.add(new Point(4, 4, 4))), Set.of(participant));
    }
}
