package me.lidan.dungeonCrawlers.core.stats;

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
    void nonFiniteAndExtremeResultsFailClosedToFixedCaps() {
        StatAggregationService service = new StatAggregationService();
        Map<StatType, Double> result = service.aggregate(Map.of(
                StatType.HEALTH, Double.POSITIVE_INFINITY,
                StatType.SPEED, Double.NaN,
                StatType.CRIT_CHANCE, -100.0), null, Map.of(), Map.of());
        assertEquals(2048, result.get(StatType.HEALTH));
        assertEquals(0, result.get(StatType.SPEED));
        assertEquals(0, result.get(StatType.CRIT_CHANCE));
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
