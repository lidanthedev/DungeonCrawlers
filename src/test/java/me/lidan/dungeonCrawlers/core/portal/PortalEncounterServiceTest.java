package me.lidan.dungeonCrawlers.core.portal;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.FloorDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.Generation;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.Limits;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.TemplateRefs;
import me.lidan.dungeonCrawlers.core.door.DoorService;
import me.lidan.dungeonCrawlers.core.encounter.BossEntityGateway;
import me.lidan.dungeonCrawlers.core.encounter.EncounterFactory;
import me.lidan.dungeonCrawlers.core.encounter.EncounterFactoryRegistry;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.state.StateTransitionService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Rotation;
import me.lidan.dungeonCrawlers.core.update.CentralUpdateService;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalEncounterServiceTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void countdownIsOwnedAbortableAndCompletesOnlyForExactBossEntity() {
        UUID instance = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        Clock clock = Clock.fixed(START, ZoneOffset.UTC);
        CentralUpdateService updates = new CentralUpdateService(clock, ignored -> { });
        RunPreparationService runs = runningRun(instance, player, updates, clock);
        FakeEntities entities = new FakeEntities();
        FakeParticipants participants = new FakeParticipants(player);
        PortalEncounterService service = service(updates, runs, clock, entities, participants,
                EncounterFactoryRegistry.withBasic());

        assertTrue(service.register(instance, floor("basic"), plan(instance)).successful());
        var first = service.enterPortal(instance, player);
        assertTrue(first.successful());
        assertEquals(PortalEncounterService.Status.COUNTDOWN, first.snapshot().status());
        assertEquals(List.of("<red><bold>Boss Starting</bold></red>|<yellow>In <white>5</white> seconds</yellow>"),
                participants.titles);
        assertFalse(service.enterPortal(instance, UUID.randomUUID()).successful());
        var aborted = service.abortPortal(instance, player);
        assertTrue(aborted.successful());
        assertEquals("portal countdown aborted by LidanTheGamer", aborted.detail());
        assertEquals("<red><bold>Boss Aborted</bold></red>|<yellow>by <white>LidanTheGamer</white></yellow>",
                participants.titles.getLast());
        assertEquals(PortalEncounterService.Status.IDLE, service.info(instance).orElseThrow().status());

        assertTrue(service.enterPortal(instance, player).successful());
        updates.tick(START.plusSeconds(1));
        assertEquals("<red><bold>Boss Starting</bold></red>|<yellow>In <white>4</white> seconds</yellow>",
                participants.titles.getLast());
        updates.tick(START.plusSeconds(5));
        var started = service.info(instance).orElseThrow();
        assertEquals(PortalEncounterService.Status.BOSS, started.status());
        assertEquals(1, participants.teleports.size());
        assertEquals(1, entities.spawnCount);
        updates.tick(START.plusSeconds(5));
        assertEquals(1, entities.spawnCount);
        assertFalse(service.onBossDeath(instance, UUID.randomUUID()).accepted());
        UUID boss = started.bossEntity();
        assertTrue(service.onBossDeath(instance, boss).accepted());
        assertEquals(PortalEncounterService.Status.COMPLETION_PENDING, service.info(instance).orElseThrow().status());
        assertEquals(RunPreparationService.RunState.COMPLETION_PENDING,
                runs.info(instance).orElseThrow().state());
    }

    @Test
    void customFactoryCanBeRegisteredAndCleanupRemovesActiveEntityAndCallback() {
        UUID instance = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        Clock clock = Clock.fixed(START, ZoneOffset.UTC);
        CentralUpdateService updates = new CentralUpdateService(clock, ignored -> { });
        RunPreparationService runs = runningRun(instance, player, updates, clock);
        FakeEntities entities = new FakeEntities();
        EncounterFactoryRegistry factories = EncounterFactoryRegistry.withBasic();
        factories.register("multi", context -> new TwoStageEncounter(context));
        PortalEncounterService service = service(updates, runs, clock, entities,
                new FakeParticipants(player), factories);

        assertTrue(service.register(instance, floor("multi"), plan(instance)).successful());
        assertTrue(service.startBoss(instance).successful());
        UUID boss = service.info(instance).orElseThrow().bossEntity();
        assertTrue(service.onBossDeath(instance, boss).accepted());
        assertEquals(PortalEncounterService.Status.COMPLETION_PENDING, service.info(instance).orElseThrow().status());
        assertTrue(service.info(instance).orElseThrow().bossSpawn()
                .equals(service.info(instance).orElseThrow().rewardChest()) == false);
        service.cleanup(instance);
        assertTrue(entities.removed.contains(boss));
        assertTrue(service.info(instance).isEmpty());
    }

    @Test
    void factoryCreationFailureFailsClosedWithoutSpawningBoss() {
        UUID instance = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        Clock clock = Clock.fixed(START, ZoneOffset.UTC);
        CentralUpdateService updates = new CentralUpdateService(clock, ignored -> { });
        RunPreparationService runs = runningRun(instance, player, updates, clock);
        FakeEntities entities = new FakeEntities();
        PortalEncounterService service = service(updates, runs, clock, entities,
                new FakeParticipants(player), EncounterFactoryRegistry.withBasic());

        assertTrue(service.register(instance, floor("factory_failure_test"), plan(instance)).successful());
        var result = service.startBoss(instance);
        assertFalse(result.successful());
        assertEquals("IllegalStateException: factory failure test", result.detail());
        assertEquals(PortalEncounterService.Status.FAILED, service.info(instance).orElseThrow().status());
        assertEquals(0, entities.spawnCount);
        assertEquals(RunPreparationService.RunState.FAILED, runs.info(instance).orElseThrow().state());
    }

    private static PortalEncounterService service(CentralUpdateService updates, RunPreparationService runs,
                                                   Clock clock, FakeEntities entities,
                                                   FakeParticipants participants,
                                                   EncounterFactoryRegistry factories) {
        return new PortalEncounterService(updates, runs, factories, entities, participants, clock, ignored -> { });
    }

    private static RunPreparationService runningRun(UUID instance, UUID player,
                                                     CentralUpdateService updates, Clock clock) {
        RunPreparationService runs = new RunPreparationService(new DoorService(), updates,
                new StateTransitionService(), clock, ignored -> { });
        runs.registerGenerated(instance, new me.lidan.dungeonCrawlers.core.party.PartySnapshot(
                player, List.of(player), true), List.of("tank"),
                java.util.Map.of("tank", new me.lidan.dungeonCrawlers.config.registry.ConfigModels.ClassDefinition(
                        "tank", "Tank", Material.STONE,
                        me.lidan.dungeonCrawlers.config.registry.ConfigModels.StatModifiers.empty())),
                new Point(0, 0, 0), me.lidan.dungeonCrawlers.core.template.TemplateModels.Facing.NORTH);
        runs.markSnapshotsReady(instance);
        runs.selectClass(instance, player, "tank");
        assertTrue(runs.openDoor(instance, player).successful());
        return runs;
    }

    private static FloorDefinition floor(String encounter) {
        return new FloorDefinition("floor_1", 1, "Floor I", new TemplateRefs("start", "portal", "boss",
                new me.lidan.dungeonCrawlers.config.registry.ConfigModels.Vector3i(0, 0, 3000)),
                new Generation(1, 0, false, 8, 1), List.of("mob"), List.of("miniboss"),
                "CryptGuardian", encounter, List.of("tank"), List.of(), java.util.Map.of(),
                new Limits(5, 512, 16_777_216, 256, 2, 1_000));
    }

    private static LayoutPlanner.LayoutPlan plan(UUID instance) {
        return new LayoutPlanner.LayoutPlan("phase2-v1", instance, 7, "config", "content", List.of(
                placement(1, "portal", me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomType.PORTAL,
                        Set.of(new Point(10, 0, 0)), List.of(), Optional.empty(), Optional.empty()),
                placement(2, "boss", me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomType.BOSS,
                        Set.of(), List.of(new Point(100, 0, 0)), Optional.of(new Point(101, 0, 0)),
                        Optional.of(new Point(102, 0, 0)))), List.of(), List.of());
    }

    private static LayoutPlanner.Placement placement(int index, String id,
                                                       me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomType type,
                                                       Set<Point> portals, List<Point> spawns,
                                                       Optional<Point> boss, Optional<Point> reward) {
        return new LayoutPlanner.Placement(index, id, type, null, Rotation.NONE,
                new Point(0, 0, 0), new Bounds(new Point(0, 0, 0), new Point(200, 20, 20)),
                Optional.empty(), Optional.empty(), Set.of(), Set.of(), List.of(), List.of(), spawns,
                boss, reward, portals, List.of());
    }

    private static final class FakeEntities implements BossEntityGateway {
        private final UUID boss = UUID.randomUUID();
        private final Set<UUID> valid = new HashSet<>();
        private final Set<UUID> removed = new HashSet<>();
        private int spawnCount;

        @Override
        public SpawnResult spawn(UUID instanceId, String mobId, Point point) {
            spawnCount++;
            valid.add(boss);
            return SpawnResult.success(boss, "spawned");
        }

        @Override
        public boolean remove(UUID entityId) {
            removed.add(entityId);
            valid.remove(entityId);
            return true;
        }

        @Override
        public boolean isValid(UUID entityId) { return valid.contains(entityId); }
    }

    private static final class FakeParticipants implements PortalEncounterService.ParticipantGateway {
        private final List<UUID> players;
        private final List<Point> teleports = new ArrayList<>();
        private final List<String> titles = new ArrayList<>();

        private FakeParticipants(UUID player) { players = List.of(player); }

        @Override
        public List<UUID> activePlayers(UUID instanceId) { return players; }

        @Override
        public boolean teleport(UUID playerId, Point target) {
            teleports.add(target);
            return true;
        }

        @Override
        public void notice(UUID instanceId, String miniMessage) { }

        @Override
        public String displayName(UUID playerId) { return "LidanTheGamer"; }

        @Override
        public void title(UUID instanceId, String miniMessageTitle, String miniMessageSubtitle) {
            titles.add(miniMessageTitle + "|" + miniMessageSubtitle);
        }
    }

    private static final class TwoStageEncounter implements EncounterFactory.Encounter {
        private final EncounterFactory.EncounterContext context;
        private UUID entity;
        private boolean complete;

        private TwoStageEncounter(EncounterFactory.EncounterContext context) { this.context = context; }

        @Override
        public EncounterFactory.StartResult start() {
            var result = context.entities().spawn(context.instanceId(), context.bossMob(), context.bossSpawn());
            if (!result.successful()) return EncounterFactory.StartResult.failure(result.detail());
            entity = result.entityId();
            return EncounterFactory.StartResult.success("stage one");
        }

        @Override
        public EncounterFactory.TickResult tick(Instant now) {
            return complete ? EncounterFactory.TickResult.complete("done")
                    : EncounterFactory.TickResult.running("stage active");
        }

        @Override
        public EncounterFactory.DeathResult onDeath(UUID entityId) {
            if (!Objects.equals(entity, entityId)) return EncounterFactory.DeathResult.ignored("wrong entity");
            complete = true;
            return EncounterFactory.DeathResult.accepted(true, "stage two complete");
        }

        @Override
        public void cleanup() { if (entity != null) context.entities().remove(entity); }

        @Override
        public Optional<UUID> entityId() { return Optional.ofNullable(entity); }

        @Override
        public boolean complete() { return complete; }
    }
}
