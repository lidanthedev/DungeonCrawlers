package me.lidan.dungeonCrawlers.core.secret;

import me.lidan.cavecrawlers.stats.StatType;
import me.lidan.cavecrawlers.utils.Range;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.BlessingDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.BlessingStacking;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ConfigSnapshot;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.FloorDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.Generation;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.Limits;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomType;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.StatModifiers;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.TemplateRefs;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.Vector3i;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.WeightedId;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Rotation;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Secret;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.SecretId;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.SecretKind;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretDiscoveryServiceTest {
    @Test
    void blessingSecretIsPartyWideDeterministicAndIdempotent() {
        UUID instance = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        BlessingDefinition blessing = new BlessingDefinition("power", "Power", Material.STONE,
                BlessingStacking.LEVELS, 2,
                new StatModifiers(Map.of(StatType.STRENGTH, 10.0), Map.of()));
        SecretDiscoveryService service = new SecretDiscoveryService(() -> config(blessing));
        assertTrue(service.register(instance, 1234, floor(), plan(instance), Set.of(player)).successful());

        var first = service.discover(instance, player, new Point(2, 1, 2));
        var second = service.discover(instance, player, new Point(2, 1, 2));

        assertEquals(SecretDiscoveryService.Status.DISCOVERED, first.status());
        assertEquals("power", first.blessingId());
        assertEquals(SecretDiscoveryService.Status.ALREADY_DISCOVERED, second.status());
        assertEquals(Map.of("power", 1), service.blessingLevels(instance));
        assertEquals("Power", service.blessingDisplayName(instance, "power").orElseThrow());
        assertEquals(11.0, service.aggregate(instance, null, Map.of(StatType.STRENGTH, 1.0))
                .get(StatType.STRENGTH));
        assertEquals(player, service.secrets(instance).getFirst().foundBy());
    }

    @Test
    void nonParticipantCannotDiscoverAndRepeatedTemplatesKeepDistinctIds() {
        UUID instance = UUID.randomUUID();
        BlessingDefinition blessing = new BlessingDefinition("power", "Power", Material.STONE,
                BlessingStacking.REPLACE, 5, StatModifiers.empty());
        SecretDiscoveryService service = new SecretDiscoveryService(() -> config(blessing));
        assertTrue(service.register(instance, 1, floor(), plan(instance), Set.of(UUID.randomUUID())).successful());

        var result = service.discover(instance, UUID.randomUUID(), new Point(2, 1, 2));
        assertEquals(SecretDiscoveryService.Status.FAILURE, result.status());
        assertEquals(2, service.secrets(instance).size());
        assertTrue(!service.secrets(instance).get(0).id().equals(service.secrets(instance).get(1).id()));
    }

    @Test
    void adminCanAddMultipleBlessingDiscoveriesForHighLevelDiagnostics() {
        UUID instance = UUID.randomUUID();
        BlessingDefinition blessing = new BlessingDefinition("power", "Power", Material.STONE,
                BlessingStacking.LEVELS, 5, StatModifiers.empty());
        SecretDiscoveryService service = new SecretDiscoveryService(() -> config(blessing));
        assertTrue(service.register(instance, 1, floor(), plan(instance), Set.of()).successful());

        var result = service.addBlessing(instance, "power", 3).orElseThrow();
        assertEquals(3, result.levelsAwarded());
        assertEquals(3, result.currentLevel());
        assertEquals(Map.of("power", 3), service.blessingLevels(instance));
    }

    @Test
    void blessingSecretUsesConfiguredRangeDeterministically() {
        UUID instance = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        BlessingDefinition blessing = new BlessingDefinition("power", "Power", Material.STONE,
                BlessingStacking.LEVELS, 5, new Range(2, 4), StatModifiers.empty());
        SecretDiscoveryService first = new SecretDiscoveryService(() -> config(blessing));
        SecretDiscoveryService second = new SecretDiscoveryService(() -> config(blessing));
        assertTrue(first.register(instance, 1234, floor(), plan(instance), Set.of(player)).successful());
        assertTrue(second.register(instance, 1234, floor(), plan(instance), Set.of(player)).successful());

        var firstResult = first.discover(instance, player, new Point(2, 1, 2));
        var secondResult = second.discover(instance, player, new Point(2, 1, 2));

        assertTrue(firstResult.blessing().currentLevel() >= 2 && firstResult.blessing().currentLevel() <= 4);
        assertEquals(firstResult.blessing().currentLevel(), secondResult.blessing().currentLevel());
    }

    private static ConfigSnapshot config(BlessingDefinition blessing) {
        return new ConfigSnapshot(1, Map.of(), Map.of(), Map.of(), Map.of(blessing.id(), blessing),
                Set.of(), "hash", Instant.EPOCH);
    }

    private static FloorDefinition floor() {
        return new FloorDefinition("floor_1", 1, "Floor I", new TemplateRefs("start", "portal", "boss",
                new Vector3i(0, 0, 3000)), new Generation(1, 0, false, 4, 1), List.of(), List.of(),
                "boss", "basic", List.of(), List.of(new WeightedId("power", 1)), Map.of(),
                new Limits(5, 512, 1_000, 64, 1, 100));
    }

    private static LayoutPlanner.LayoutPlan plan(UUID instance) {
        Secret first = new Secret(new Point(2, 1, 2), SecretKind.BLESSING);
        Secret second = new Secret(new Point(2, 1, 2), SecretKind.STANDARD);
        var placement = new LayoutPlanner.Placement(1, "room", RoomType.NORMAL,
                me.lidan.dungeonCrawlers.config.registry.ConfigModels.EncounterCapability.NORMAL, Rotation.NONE,
                new Point(0, 0, 0), new Bounds(new Point(0, 0, 0), new Point(4, 4, 4)), Optional.empty(),
                Optional.empty(), Set.of(), Set.of(), List.of(), List.of(), List.of(), Optional.empty(),
                Optional.empty(), Set.of(), List.of(new LayoutPlanner.PlacedSecret(
                        new SecretId(instance, 1, first.point()), first.point(), first.kind()),
                        new LayoutPlanner.PlacedSecret(new SecretId(instance, 2, second.point()),
                                new Point(3, 1, 2), second.kind())));
        return new LayoutPlanner.LayoutPlan("phase2-v1", instance, 1, "config", "content",
                List.of(placement), List.of(), List.of());
    }
}
