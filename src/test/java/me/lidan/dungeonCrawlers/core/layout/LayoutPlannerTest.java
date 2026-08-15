package me.lidan.dungeonCrawlers.core.layout;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.EncounterCapability;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.FloorDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.Generation;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.Limits;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomType;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.TemplateRefs;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.Vector3i;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner.CatalogEntry;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner.PlanRequest;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Connector;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.ConnectorKind;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Facing;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Rotation;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Secret;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.SecretKind;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Template;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutPlannerTest {
    private static final UUID INSTANCE = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final Bounds SLOT = new Bounds(new Point(-500, -100, -500), new Point(500, 200, 5_000));
    private final LayoutPlanner planner = new LayoutPlanner();

    @Test
    void plansExactCompositionPhysicalPortalIsolatedBossAndTwoPlaneConnections() {
        PlanRequest request = request(77, floor(4, 2, true, new Vector3i(0, 0, 300)), catalog(), SLOT);

        var result = planner.plan(request);

        assertTrue(result.successful(), result.errors().toString());
        var plan = result.plan().orElseThrow();
        assertEquals(8, plan.placements().size());
        assertEquals(3, plan.placements().stream()
                .filter(placement -> placement.encounter() == EncounterCapability.MINIBOSS).count());
        assertEquals(2, plan.placements().stream()
                .filter(placement -> placement.encounter() == EncounterCapability.NORMAL).count());
        assertEquals(RoomType.PORTAL, plan.placements().get(6).type());
        assertEquals(RoomType.BOSS, plan.placements().get(7).type());
        assertEquals(new Point(0, 0, 300), plan.placements().get(7).origin());
        assertEquals(6, plan.connections().size());
        plan.connections().forEach(connection -> {
            assertTrue(connection.valid());
            assertEquals(9, connection.doorBounds().size());
            assertEquals(9, connection.entranceBounds().size());
            assertEquals(18, connection.bounds().size());
        });
    }

    @Test
    void sameInputsReproduceAndNamedSeedChangesTrace() {
        PlanRequest first = request(1234, floor(6, 3, true, new Vector3i(0, 0, 300)), catalog(), SLOT);
        PlanRequest same = request(1234, first.floor(), catalog(), SLOT);
        PlanRequest different = request(1235, first.floor(), catalog(), SLOT);

        var firstPlan = planner.plan(first).plan().orElseThrow();
        var samePlan = planner.plan(same).plan().orElseThrow();
        var differentPlan = planner.plan(different).plan().orElseThrow();

        assertEquals(firstPlan, samePlan);
        assertNotEquals(firstPlan.trace().get(1), differentPlan.trace().get(1));
    }

    @Test
    void avoidsImmediateTemplateRepeatsAndRepeatedTemplateSecretsHaveDistinctRuntimeIdentity() {
        var plan = planner.plan(request(9, floor(8, 0, false, new Vector3i(0, 0, 300)), catalog(), SLOT))
                .plan().orElseThrow();
        List<LayoutPlanner.Placement> combat = plan.placements().stream()
                .filter(placement -> placement.type() == RoomType.NORMAL).toList();

        for (int index = 1; index < combat.size(); index++) {
            assertNotEquals(combat.get(index - 1).templateId(), combat.get(index).templateId());
        }
        assertEquals(combat.size(), combat.stream().map(placement -> placement.secrets().getFirst()).distinct().count());
    }

    @Test
    void workedConnectionExampleProducesExactOrigin() {
        Map<String, CatalogEntry> catalog = catalog();
        Template start = template("start", RoomType.START, Set.of(),
                new Bounds(new Point(0, 0, 0), new Point(4, 4, 4)), null,
                new Connector(ConnectorKind.EXIT, new Point(4, 2, 2), Facing.EAST));
        Template normal = template("normal_a", RoomType.NORMAL, Set.of(EncounterCapability.NORMAL),
                new Bounds(new Point(0, 0, 0), new Point(4, 4, 10)),
                new Connector(ConnectorKind.ENTRANCE, new Point(0, 2, 5), Facing.WEST),
                new Connector(ConnectorKind.EXIT, new Point(4, 2, 5), Facing.EAST));
        catalog.put("start", entry(start, 1));
        catalog.put("normal_a", entry(normal, 1));
        catalog.remove("normal_b");
        Point startOrigin = new Point(106, 68, 198);
        Bounds slot = new Bounds(new Point(0, 0, 0), new Point(1_000, 200, 5_000));

        var plan = planner.plan(new PlanRequest(INSTANCE, 1,
                floor(1, 0, false, new Vector3i(0, 0, 300)), catalog, startOrigin, slot, "config"))
                .plan().orElseThrow();

        assertEquals(new Point(111, 68, 195), plan.placements().get(1).origin());
    }

    @Test
    void markerBlocksExcludeChestPayloadButRemoveRewardMarker() {
        Point rewardMarker = new Point(4, 1, 4);
        Point chest = new Point(5, 1, 4);
        var placement = new LayoutPlanner.Placement(0, "room", RoomType.BOSS, null, Rotation.NONE,
                new Point(0, 0, 0), new Bounds(new Point(0, 0, 0), new Point(8, 8, 8)), Optional.empty(),
                Optional.empty(), Set.of(), Set.of(), List.of(), List.of(), List.of(), Optional.empty(),
                Optional.of(rewardMarker), Set.of(), List.of(new LayoutPlanner.PlacedSecret(
                        new me.lidan.dungeonCrawlers.core.template.TemplateModels.SecretId(INSTANCE, 0, chest),
                        chest, SecretKind.STANDARD)));

        assertTrue(placement.markerBlocks().contains(rewardMarker));
        assertFalse(placement.markerBlocks().contains(chest));
    }

    @Test
    void collisionPaddingDoesNotRejectFramesOfDirectlyConnectedRooms() {
        Map<String, CatalogEntry> catalog = catalog();
        replaceSolids(catalog, "start", Set.of(new Point(0, 2, 4)));
        replaceSolids(catalog, "normal_a", Set.of(new Point(0, 2, 0), new Point(0, 2, 4)));
        replaceSolids(catalog, "normal_b", Set.of(new Point(0, 2, 0), new Point(0, 2, 4)));
        replaceSolids(catalog, "portal", Set.of(new Point(0, 2, 0)));

        var result = planner.plan(request(12, floor(1, 0, false, new Vector3i(0, 0, 300)), catalog, SLOT));

        assertTrue(result.successful(), result.errors().toString());
    }

    @Test
    void oversizeBossFailsBeforeAnyPlanIsReturned() {
        Bounds tooSmall = new Bounds(new Point(-10, -10, -10), new Point(50, 50, 50));
        var result = planner.plan(request(1, floor(1, 0, false, new Vector3i(0, 0, 300)), catalog(), tooSmall));

        assertFalse(result.successful());
        assertTrue(result.errors().getFirst().contains("BOSS"), result.errors().toString());
        assertTrue(result.errors().getFirst().contains("leave slot"), result.errors().toString());
    }

    @Test
    void isolatedBossCollisionFailsBeforePlanIsReturned() {
        var result = planner.plan(request(1, floor(1, 0, false, new Vector3i(0, 0, 0)), catalog(), SLOT));

        assertFalse(result.successful());
        assertTrue(result.errors().getFirst().contains("BOSS"), result.errors().toString());
        assertTrue(result.errors().getFirst().contains("collision"), result.errors().toString());
    }

    @Test
    void transformsEveryRuntimeMarkerAcrossAllRotations() {
        Point marker = new Point(1, 1, 2);
        Bounds bounds = new Bounds(new Point(0, 0, 0), new Point(4, 4, 4));
        for (me.lidan.dungeonCrawlers.core.template.TemplateModels.Rotation expected
                : me.lidan.dungeonCrawlers.core.template.TemplateModels.Rotation.values()) {
            Facing targetEntranceFacing = Facing.NORTH;
            Facing sourceExitFacing = expected.apply(targetEntranceFacing).opposite();
            Template source = template("source", RoomType.START, Set.of(), bounds, null,
                    new Connector(ConnectorKind.EXIT, new Point(2, 2, 2), sourceExitFacing));
            Template target = new Template("target", RoomType.NORMAL, Set.of(EncounterCapability.NORMAL), bounds,
                    Optional.of(new Connector(ConnectorKind.ENTRANCE, new Point(2, 2, 2), targetEntranceFacing)),
                    Optional.of(new Connector(ConnectorKind.EXIT, new Point(2, 2, 4), Facing.SOUTH)),
                    List.of(marker), List.of(marker), List.of(marker), Optional.of(marker), Optional.of(marker),
                    List.of(new Secret(marker, SecretKind.BLESSING)), Set.of(marker), Set.of(marker), "hash-target");

            var result = planner.connectTest(source,
                    me.lidan.dungeonCrawlers.core.template.TemplateModels.Rotation.NONE,
                    new Point(-20, 5, 30), target);
            assertTrue(result.successful(), expected + " " + result.detail());
            var placement = result.placement().orElseThrow();
            Point transformed = expected.apply(marker).add(placement.origin());
            assertEquals(expected, placement.rotation());
            assertEquals(List.of(transformed), placement.normalMobs());
            assertEquals(List.of(transformed), placement.minibossMobs());
            assertEquals(List.of(transformed), placement.playerSpawns());
            assertEquals(Optional.of(transformed), placement.bossSpawn());
            assertEquals(Optional.of(transformed), placement.rewardChest());
            assertEquals(Set.of(transformed), placement.portalBlocks());
            assertEquals(transformed, placement.secrets().getFirst().worldPoint());
        }
    }

    @Test
    void fuzzesOneThousandSeedsWithoutChangingCountsOrGeometry() {
        FloorDefinition floor = floor(7, 3, true, new Vector3i(0, 0, 300));
        for (long seed = 0; seed < 1_000; seed++) {
            var result = planner.plan(request(seed, floor, catalog(), SLOT));
            assertTrue(result.successful(), "seed=" + seed + " " + result.errors());
            var plan = result.plan().orElseThrow();
            assertEquals(11, plan.placements().size(), "seed=" + seed);
            assertTrue(plan.connections().stream().allMatch(connection -> connection.bounds().size() == 18),
                    "seed=" + seed);
        }
    }

    private static PlanRequest request(long seed, FloorDefinition floor, Map<String, CatalogEntry> catalog,
                                       Bounds slot) {
        return new PlanRequest(INSTANCE, seed, floor, catalog, new Point(0, 0, 0), slot, "config-hash");
    }

    private static FloorDefinition floor(int rooms, int minibosses, boolean finalMiniboss, Vector3i bossOffset) {
        return new FloorDefinition("floor_1", 1, "Floor I", new TemplateRefs("start", "portal", "boss", bossOffset),
                new Generation(rooms, minibosses, finalMiniboss, 64, 1), List.of("mob"), List.of("miniboss"),
                "bossMob", "basic", List.of("class"), List.of(), Map.of(),
                new Limits(5, 512, 16_777_216, 256, 2, 1_000));
    }

    private static Map<String, CatalogEntry> catalog() {
        Map<String, CatalogEntry> result = new LinkedHashMap<>();
        Bounds bounds = new Bounds(new Point(0, 0, 0), new Point(4, 4, 4));
        Template start = template("start", RoomType.START, Set.of(), bounds, null,
                new Connector(ConnectorKind.EXIT, new Point(2, 2, 4), Facing.SOUTH));
        Template normalA = template("normal_a", RoomType.NORMAL, Set.of(EncounterCapability.NORMAL), bounds,
                new Connector(ConnectorKind.ENTRANCE, new Point(2, 2, 0), Facing.NORTH),
                new Connector(ConnectorKind.EXIT, new Point(2, 2, 4), Facing.SOUTH));
        Template normalB = template("normal_b", RoomType.NORMAL, Set.of(EncounterCapability.NORMAL), bounds,
                new Connector(ConnectorKind.ENTRANCE, new Point(2, 2, 0), Facing.NORTH),
                new Connector(ConnectorKind.EXIT, new Point(2, 2, 4), Facing.SOUTH));
        Template miniboss = template("miniboss", RoomType.NORMAL, Set.of(EncounterCapability.MINIBOSS), bounds,
                new Connector(ConnectorKind.ENTRANCE, new Point(2, 2, 0), Facing.NORTH),
                new Connector(ConnectorKind.EXIT, new Point(2, 2, 4), Facing.SOUTH));
        Template portal = template("portal", RoomType.PORTAL, Set.of(), bounds,
                new Connector(ConnectorKind.ENTRANCE, new Point(2, 2, 0), Facing.NORTH), null);
        Template boss = template("boss", RoomType.BOSS, Set.of(), bounds, null, null);
        result.put("start", entry(start, 1));
        result.put("normal_a", entry(normalA, 3));
        result.put("normal_b", entry(normalB, 1));
        result.put("miniboss", entry(miniboss, 1));
        result.put("portal", entry(portal, 1));
        result.put("boss", entry(boss, 1));
        return result;
    }

    private static CatalogEntry entry(Template template, double weight) {
        return new CatalogEntry(new RoomDefinition(template.id(), template.type(), template.capabilities(),
                1, null, weight), template);
    }

    private static void replaceSolids(Map<String, CatalogEntry> catalog, String id, Set<Point> solids) {
        CatalogEntry current = catalog.get(id);
        Template template = current.template();
        catalog.put(id, new CatalogEntry(current.definition(), new Template(template.id(), template.type(),
                template.capabilities(), template.bounds(), template.entrance(), template.exit(),
                template.normalMobs(), template.minibossMobs(), template.playerSpawns(), template.bossSpawn(),
                template.rewardChest(), template.secrets(), template.portalBlocks(), solids, template.contentHash())));
    }

    private static Template template(String id, RoomType type, Set<EncounterCapability> capabilities, Bounds bounds,
                                     Connector entrance, Connector exit) {
        List<Secret> secrets = type == RoomType.NORMAL
                ? List.of(new Secret(new Point(1, 1, 1), SecretKind.STANDARD)) : List.of();
        return new Template(id, type, capabilities, bounds, Optional.ofNullable(entrance), Optional.ofNullable(exit),
                List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), secrets, Set.of(), Set.of(),
                "hash-" + id);
    }
}
