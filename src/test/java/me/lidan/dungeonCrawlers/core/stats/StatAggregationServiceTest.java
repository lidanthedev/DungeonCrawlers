package me.lidan.dungeonCrawlers.core.stats;

import me.lidan.cavecrawlers.stats.StatType;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.*;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatAggregationServiceTest {
    @Test
    void stackingRulesAndStableAggregationMatchContract() {
        BlessingDefinition levels = blessing("a_levels", BlessingStacking.LEVELS, 2,
                new StatModifiers(Map.of(StatType.STRENGTH, 10.0), Map.of(StatType.STRENGTH, 2.0)));
        BlessingDefinition replace = blessing("b_replace", BlessingStacking.REPLACE, 5,
                new StatModifiers(Map.of(StatType.STRENGTH, -5.0), Map.of()));
        BlessingLevels active = new BlessingLevels();
        assertTrue(active.discover(levels).levelChanged());
        assertTrue(active.discover(levels).levelChanged());
        assertFalse(active.discover(levels).levelChanged());
        assertTrue(active.discover(replace).levelChanged());
        assertFalse(active.discover(replace).levelChanged());
        ClassDefinition selected = new ClassDefinition("class", "Class", Material.STONE,
                new StatModifiers(Map.of(StatType.STRENGTH, 5.0), Map.of(StatType.STRENGTH, 1.5)));

        double value = new StatAggregationService().aggregate(Map.of(StatType.STRENGTH, 10.0), selected,
                Map.of(levels.id(), levels, replace.id(), replace), active.snapshot()).get(StatType.STRENGTH);

        assertEquals(180, value); // (10 + 5 + 20 - 5) * 1.5 * 2^2
    }

    @Test
    void classPercentagesScaleWithSkillLevelAndExistingBonuses() {
        StatAggregationService service = new StatAggregationService();
        ClassDefinition selected = new ClassDefinition("berserker", "Berserker", Material.IRON_SWORD,
                new StatModifiers(Map.of(StatType.STRENGTH, 50.0), Map.of()),
                Map.of(StatType.STRENGTH, 2.0), 0);
        BlessingDefinition blessing = blessing("strength", BlessingStacking.LEVELS, 1,
                new StatModifiers(Map.of(StatType.STRENGTH, 10.0), Map.of(StatType.STRENGTH, 1.5)));
        Map<StatType, Double> incoming = Map.of(StatType.STRENGTH, 100.0, StatType.DEFENSE, 80.0);
        var result = service.aggregate(incoming, selected, Map.of("strength", blessing), Map.of("strength", 1), 20);

        assertEquals(336.0, result.get(StatType.STRENGTH), 0.00001); // (100 + 50 + 10) * 1.4 * 1.5
        assertEquals(80.0, result.get(StatType.DEFENSE));
        assertEquals(150.0, service.aggregate(incoming, selected, Map.of(), Map.of(), -1).get(StatType.STRENGTH));
        assertEquals(330.0, service.aggregate(incoming, selected, Map.of(), Map.of(), 100).get(StatType.STRENGTH));
        assertEquals(100.0, incoming.get(StatType.STRENGTH));
        assertEquals(100.0, service.aggregate(incoming, null, Map.of(), Map.of(), 20).get(StatType.STRENGTH));
    }

    @Test
    void aggregationDoesNotClampConfiguredStats() {
        StatAggregationService service = new StatAggregationService();
        Map<StatType, Double> result = service.aggregate(Map.of(
                StatType.HEALTH, Double.POSITIVE_INFINITY,
                StatType.SPEED, Double.NaN,
                StatType.CRIT_CHANCE, -100.0), null, Map.of(), Map.of());
        assertEquals(Double.POSITIVE_INFINITY, result.get(StatType.HEALTH));
        assertTrue(Double.isNaN(result.get(StatType.SPEED)));
        assertEquals(-100, result.get(StatType.CRIT_CHANCE));
    }

    @Test
    void replaceBlessingRejectsImpossibleLevelAboveOne() {
        BlessingDefinition replace = blessing("replace", BlessingStacking.REPLACE, 5,
                new StatModifiers(Map.of(StatType.STRENGTH, 1.0), Map.of()));

        assertThrows(IllegalArgumentException.class, () -> new StatAggregationService().aggregate(Map.of(), null,
                Map.of(replace.id(), replace), Map.of(replace.id(), 2)));
    }

    private static BlessingDefinition blessing(String id, BlessingStacking stacking, int max,
                                                StatModifiers modifiers) {
        return new BlessingDefinition(id, id, Material.STONE, stacking, max, modifiers);
    }
}
