package me.lidan.dungeonCrawlers.core.combat;

import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static me.lidan.dungeonCrawlers.config.registry.ConfigModels.EncounterCapability.NORMAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatRoomServiceTest {
    @Test
    void clearsRoomAndUnlocksNextOnlyAfterAllDeaths() {
        UUID instance = UUID.randomUUID();
        FakeMobs mobs = new FakeMobs();
        FakeChunks chunks = new FakeChunks();
        CombatRoomService service = new CombatRoomService(mobs, chunks, ignored -> { });
        assertTrue(service.register(plan(instance, 1)).successful());
        assertFalse(service.isDoorAt(new Point(0, 0, 0)));

        assertTrue(service.activateFirst(instance).successful());
        UUID first = mobs.entities.getFirst();
        UUID second = mobs.entities.getLast();
        assertEquals(CombatRoomService.RoomState.ACTIVE, service.info(instance).orElseThrow().rooms().getFirst().state());
        assertEquals(CombatRoomService.RoomState.LOCKED, service.info(instance).orElseThrow().rooms().get(1).state());

        assertTrue(service.onDeath(instance, 1, first).accepted());
        assertEquals(CombatRoomService.RoomState.ACTIVE, service.info(instance).orElseThrow().rooms().getFirst().state());
        assertTrue(service.onDeath(instance, 1, second).accepted());
        assertEquals(CombatRoomService.RoomState.CLEARED, service.info(instance).orElseThrow().rooms().getFirst().state());
        assertEquals(CombatRoomService.RoomState.READY, service.info(instance).orElseThrow().rooms().get(1).state());
        var activation = service.activateAt(new Point(2, 0, 0));
        assertTrue(activation.successful());
        assertEquals(Set.of(new Point(2, 0, 0)), activation.openedDoorBlocks());
    }

    @Test
    void unexpectedRemovalRetriesAndExhaustionFailsRoom() {
        UUID instance = UUID.randomUUID();
        FakeMobs mobs = new FakeMobs();
        FakeChunks chunks = new FakeChunks();
        CombatRoomService service = new CombatRoomService(mobs, chunks, ignored -> { });
        assertTrue(service.register(plan(instance, 0)).successful());
        assertTrue(service.activateFirst(instance).successful());
        UUID entity = mobs.entities.getFirst();
        mobs.failNext = true;
        assertTrue(service.onRemoved(instance, 1, entity).accepted());
        assertEquals(CombatRoomService.MobState.FAILED,
                service.info(instance).orElseThrow().rooms().getFirst().requiredMobs().getFirst().state());
        assertEquals(CombatRoomService.RoomState.FAILED, service.info(instance).orElseThrow().rooms().getFirst().state());
    }

    @Test
    void successfulUnexpectedRemovalsConsumeBoundedRespawnBudget() {
        UUID instance = UUID.randomUUID();
        FakeMobs mobs = new FakeMobs();
        CombatRoomService service = new CombatRoomService(mobs, new FakeChunks(), ignored -> { });
        assertTrue(service.register(plan(instance, 2)).successful());
        assertTrue(service.activateFirst(instance).successful());

        UUID first = service.info(instance).orElseThrow().rooms().getFirst().requiredMobs().getFirst().entityId();
        assertTrue(service.onRemoved(instance, 1, first).accepted());
        UUID second = service.info(instance).orElseThrow().rooms().getFirst().requiredMobs().getFirst().entityId();
        assertTrue(service.onRemoved(instance, 1, second).accepted());
        UUID third = service.info(instance).orElseThrow().rooms().getFirst().requiredMobs().getFirst().entityId();
        assertTrue(service.onRemoved(instance, 1, third).accepted());
        assertEquals(CombatRoomService.RoomState.FAILED,
                service.info(instance).orElseThrow().rooms().getFirst().state());
    }

    @Test
    void adminRemovalDoesNotAdvanceRoom() {
        UUID instance = UUID.randomUUID();
        FakeMobs mobs = new FakeMobs();
        CombatRoomService service = new CombatRoomService(mobs, new FakeChunks(), ignored -> { });
        assertTrue(service.register(plan(instance, 1)).successful());
        assertTrue(service.activateFirst(instance).successful());
        UUID entity = mobs.entities.getFirst();
        assertTrue(service.remove(instance, 1, entity).successful());
        assertFalse(service.onRemoved(instance, 1, entity).accepted());
        assertEquals(CombatRoomService.RoomState.ACTIVE, service.info(instance).orElseThrow().rooms().getFirst().state());
    }

    @Test
    void instancesAreIsolated() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        FakeMobs mobs = new FakeMobs();
        CombatRoomService service = new CombatRoomService(mobs, new FakeChunks(), ignored -> { });
        assertTrue(service.register(plan(first, 1)).successful());
        assertTrue(service.register(plan(second, 1)).successful());
        assertTrue(service.activateFirst(first).successful());
        assertTrue(service.activateFirst(second).successful());
        UUID entity = mobs.entities.getFirst();
        assertFalse(service.onDeath(second, 1, entity).accepted());
        assertEquals(CombatRoomService.RoomState.ACTIVE, service.info(first).orElseThrow().rooms().getFirst().state());
    }

    private static GenerationService.CombatPlan plan(UUID instance, int retries) {
        Bounds bounds = new Bounds(new Point(0, 0, 0), new Point(4, 4, 4));
        var room1 = new GenerationService.CombatRoom(1, "one", NORMAL, bounds,
                List.of(new Point(1, 1, 1), new Point(2, 1, 1)), List.of());
        var room2 = new GenerationService.CombatRoom(2, "two", NORMAL,
                new Bounds(new Point(5, 0, 0), new Point(9, 4, 4)), List.of(new Point(6, 1, 1)), List.of());
        return new GenerationService.CombatPlan(instance, 1, List.of(room1, room2),
                List.of(new GenerationService.RoomLink(0, 1, Set.of(new Point(0, 0, 0))),
                        new GenerationService.RoomLink(1, 2, Set.of(new Point(2, 0, 0)))),
                List.of("zombie"), List.of("mini"), retries);
    }

    private static final class FakeMobs implements CombatMobGateway {
        private final List<UUID> entities = new ArrayList<>();
        private final Deque<UUID> valid = new ArrayDeque<>();
        private boolean failNext;

        @Override
        public SpawnResult spawn(UUID instanceId, int roomIndex, String mobId, Point point) {
            if (failNext) {
                failNext = false;
                return SpawnResult.failure("forced spawn failure");
            }
            UUID entity = UUID.randomUUID();
            entities.add(entity);
            valid.add(entity);
            return new SpawnResult(true, entity, "spawned");
        }

        @Override
        public boolean remove(UUID entityId) { return valid.remove(entityId); }

        @Override
        public boolean isValid(UUID entityId) { return valid.contains(entityId); }
    }

    private static final class FakeChunks implements CombatChunkGateway {
        @Override
        public boolean acquire(UUID instanceId, Bounds bounds) { return true; }

        @Override
        public int release(UUID instanceId, Bounds bounds) { return 1; }

        @Override
        public int releaseAll(UUID instanceId) { return 0; }
    }
}
