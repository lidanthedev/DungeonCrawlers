package me.lidan.dungeonCrawlers.core.template;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.EncounterCapability;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomType;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Block;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.EmeraldPolicy;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Facing;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Rotation;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Selection;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.SecretKind;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateValidatorTest {
    private static final Bounds BOUNDS = new Bounds(new Point(0, 0, 0), new Point(4, 4, 4));
    private final TemplateValidator validator = new TemplateValidator();

    @Test
    void acceptsEveryCanonicalTemplateTypeAndRecordsMinimumPointRelativeMarkers() {
        var normal = blocks();
        normal.put(new Point(2, 2, 0), jigsaw("dungeoncrawlers:entrance", "north_up"));
        normal.put(new Point(2, 2, 4), jigsaw("dungeoncrawlers:exit", "south_up"));
        normal.put(new Point(1, 1, 2), block("gray_concrete_powder"));
        normal.put(new Point(3, 1, 2), block("yellow_concrete_powder"));
        normal.put(new Point(2, 1, 2), block("chest"));
        var normalResult = validator.validate("normal", RoomType.NORMAL,
                Set.of(EncounterCapability.NORMAL, EncounterCapability.MINIBOSS), selection(normal), EmeraldPolicy.REPLACE);

        var start = blocks();
        start.put(new Point(2, 2, 4), jigsaw("dungeoncrawlers:exit", "south_up"));
        start.put(new Point(2, 1, 2), block("emerald_block"));
        var startResult = validator.validate("start", RoomType.START, Set.of(), selection(start), EmeraldPolicy.REPLACE);

        var portal = blocks();
        portal.put(new Point(2, 2, 0), jigsaw("dungeoncrawlers:entrance", "north_up"));
        portal.put(new Point(2, 1, 2), block("nether_portal"));
        portal.put(new Point(2, 2, 2), block("nether_portal"));
        var portalResult = validator.validate("portal", RoomType.PORTAL, Set.of(), selection(portal), EmeraldPolicy.REPLACE);

        var boss = blocks();
        boss.put(new Point(1, 1, 1), block("emerald_block"));
        boss.put(new Point(2, 1, 2), block("red_concrete_powder"));
        boss.put(new Point(3, 1, 3), block("lime_concrete_powder"));
        boss.put(new Point(1, 1, 3), block("trapped_chest"));
        var bossResult = validator.validate("boss", RoomType.BOSS, Set.of(), selection(boss), EmeraldPolicy.RETAIN);

        assertTrue(normalResult.successful(), normalResult.errors().toString());
        assertTrue(startResult.successful(), startResult.errors().toString());
        assertTrue(portalResult.successful(), portalResult.errors().toString());
        assertTrue(bossResult.successful(), bossResult.errors().toString());
        assertEquals(new Point(2, 2, 0), normalResult.template().orElseThrow().entrance().orElseThrow().point());
        assertEquals(1, normalResult.template().orElseThrow().secrets().size());
        assertEquals(SecretKind.BLESSING, normalResult.template().orElseThrow().secrets().getFirst().kind());
        assertEquals(SecretKind.STANDARD, bossResult.template().orElseThrow().secrets().getFirst().kind());
        assertEquals(2, portalResult.template().orElseThrow().portalBlocks().size());
        assertTrue(bossResult.template().orElseThrow().solidBlocks().contains(new Point(1, 1, 1)));
    }

    @Test
    void reportsAllJigsawNbtOrientationFaceAndDoorPlaneErrors() {
        var blocks = blocks();
        blocks.put(new Point(0, 0, 2), new Block("jigsaw", Map.of("orientation", "up_north"), Map.of(
                "name", "wrong:name", "target", "wrong:target", "pool", "wrong:pool", "final_state", "stone")));
        blocks.put(new Point(1, 0, 2), block("stone"));

        var result = validator.validate("broken", RoomType.START, Set.of(), selection(blocks), EmeraldPolicy.REPLACE);

        assertFalse(result.successful());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("name must be")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("target must be")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("pool must be")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("final_state must be")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("orientation must be")));
        assertTrue(result.errors().size() >= 6, result.errors().toString());
    }

    @Test
    void rejectsOffFaceAndObstructedCenteredDoorPlaneTogether() {
        var blocks = blocks();
        blocks.put(new Point(2, 2, 2), jigsaw("dungeoncrawlers:exit", "east_up"));
        blocks.put(new Point(2, 3, 2), block("stone"));
        blocks.put(new Point(2, 1, 2), block("emerald_block"));

        var result = validator.validate("start", RoomType.START, Set.of(), selection(blocks), EmeraldPolicy.REPLACE);

        assertTrue(result.errors().stream().anyMatch(error -> error.contains("outward selection face")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("door plane block")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("clearance must lie outside")));
    }

    @Test
    void rejectsDisconnectedPortalAndMarkersThatConflictWithCapabilitiesAndType() {
        var portal = blocks();
        portal.put(new Point(2, 2, 0), jigsaw("dungeoncrawlers:entrance", "north_up"));
        portal.put(new Point(1, 1, 2), block("nether_portal"));
        portal.put(new Point(3, 3, 2), block("nether_portal"));
        portal.put(new Point(2, 1, 2), block("gray_concrete_powder"));
        var portalResult = validator.validate("portal", RoomType.PORTAL, Set.of(), selection(portal), EmeraldPolicy.REPLACE);

        var normal = blocks();
        normal.put(new Point(2, 2, 0), jigsaw("dungeoncrawlers:entrance", "north_up"));
        normal.put(new Point(2, 2, 4), jigsaw("dungeoncrawlers:exit", "south_up"));
        normal.put(new Point(1, 1, 2), block("gray_concrete_powder"));
        var normalResult = validator.validate("normal", RoomType.NORMAL,
                Set.of(EncounterCapability.MINIBOSS), selection(normal), EmeraldPolicy.REPLACE);

        assertTrue(portalResult.errors().stream().anyMatch(error -> error.contains("exactly one connected")));
        assertTrue(portalResult.errors().stream().anyMatch(error -> error.contains("must not contain normal mob")));
        assertTrue(normalResult.errors().stream().anyMatch(error -> error.contains("normal mob markers require")));
        assertTrue(normalResult.errors().stream().anyMatch(error -> error.contains("MINIBOSS capability requires")));
    }

    @Test
    void combinedEncounterCapabilityAcceptsEitherMobMarker() {
        Set<EncounterCapability> capabilities = Set.of(EncounterCapability.NORMAL, EncounterCapability.MINIBOSS);

        for (String marker : new String[]{"gray_concrete_powder", "yellow_concrete_powder"}) {
            var blocks = blocks();
            blocks.put(new Point(2, 2, 0), jigsaw("dungeoncrawlers:entrance", "north_up"));
            blocks.put(new Point(2, 2, 4), jigsaw("dungeoncrawlers:exit", "south_up"));
            blocks.put(new Point(2, 1, 2), block(marker));

            var result = validator.validate("normal", RoomType.NORMAL, capabilities,
                    selection(blocks), EmeraldPolicy.REPLACE);

            assertTrue(result.successful(), marker + " " + result.errors());
        }

        var withoutMob = blocks();
        withoutMob.put(new Point(2, 2, 0), jigsaw("dungeoncrawlers:entrance", "north_up"));
        withoutMob.put(new Point(2, 2, 4), jigsaw("dungeoncrawlers:exit", "south_up"));
        var result = validator.validate("normal", RoomType.NORMAL, capabilities,
                selection(withoutMob), EmeraldPolicy.REPLACE);

        assertTrue(result.errors().stream().anyMatch(error -> error.contains("GRAY_CONCRETE_POWDER or YELLOW_CONCRETE_POWDER")));
    }

    @Test
    void rotationsPreservePlaneCardinalityAndFacing() {
        Point asymmetric = new Point(-3, 7, 11);
        for (Rotation rotation : Rotation.values()) {
            for (Facing facing : Facing.values()) {
                Set<Point> transformed = TemplateModels.plane(asymmetric, facing).stream()
                        .map(rotation::apply).collect(java.util.stream.Collectors.toSet());
                assertEquals(9, transformed.size());
                assertEquals(TemplateModels.plane(rotation.apply(asymmetric), rotation.apply(facing)), transformed);
            }
        }
    }

    @Test
    void acceptsEveryHorizontalConnectorOrientationAndRejectsEveryCenteredPlaneEdge() {
        for (Facing facing : Facing.values()) {
            Point center = switch (facing) {
                case NORTH -> new Point(2, 2, 0);
                case EAST -> new Point(4, 2, 2);
                case SOUTH -> new Point(2, 2, 4);
                case WEST -> new Point(0, 2, 2);
            };
            String orientation = facing.name().toLowerCase(java.util.Locale.ROOT) + "_up";
            var valid = blocks();
            valid.put(center, jigsaw("dungeoncrawlers:exit", orientation));
            valid.put(new Point(2, 1, 2), block("emerald_block"));
            assertTrue(validator.validate("start", RoomType.START, Set.of(), selection(valid), EmeraldPolicy.REPLACE)
                    .successful(), facing.toString());

            Point edge = new Point(center.x(), 0, center.z());
            var invalid = blocks();
            invalid.put(edge, jigsaw("dungeoncrawlers:exit", orientation));
            invalid.put(new Point(2, 1, 2), block("emerald_block"));
            var result = validator.validate("start", RoomType.START, Set.of(), selection(invalid), EmeraldPolicy.REPLACE);
            assertTrue(result.errors().stream().anyMatch(error -> error.contains("centered 3x3")),
                    facing + " " + result.errors());
        }
    }

    @Test
    void emeraldPolicyChangesCanonicalContentIdentity() {
        var start = blocks();
        start.put(new Point(2, 2, 4), jigsaw("dungeoncrawlers:exit", "south_up"));
        start.put(new Point(2, 1, 2), block("emerald_block"));

        var replace = validator.validate("start", RoomType.START, Set.of(), selection(start), EmeraldPolicy.REPLACE)
                .template().orElseThrow();
        var retain = validator.validate("start", RoomType.START, Set.of(), selection(start), EmeraldPolicy.RETAIN)
                .template().orElseThrow();

        assertFalse(replace.contentHash().equals(retain.contentHash()));
        assertFalse(replace.solidBlocks().contains(new Point(2, 1, 2)));
        assertTrue(retain.solidBlocks().contains(new Point(2, 1, 2)));
    }

    private static Map<Point, Block> blocks() {
        return new HashMap<>();
    }

    private static Selection selection(Map<Point, Block> blocks) {
        return new Selection(BOUNDS, blocks);
    }

    private static Block block(String type) {
        return new Block(type, Map.of(), Map.of());
    }

    private static Block jigsaw(String name, String orientation) {
        return new Block("jigsaw", Map.of("orientation", orientation), Map.of(
                "name", name,
                "target", "dungeoncrawlers:connector",
                "pool", "minecraft:empty",
                "final_state", "minecraft:air"));
    }
}
