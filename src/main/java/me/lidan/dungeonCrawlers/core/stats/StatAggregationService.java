package me.lidan.dungeonCrawlers.core.stats;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.BlessingDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ClassDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.StatModifiers;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.StatType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class StatAggregationService {
    public Map<StatType, Double> aggregate(Map<StatType, Double> incoming, ClassDefinition selectedClass,
                                           Map<String, BlessingDefinition> definitions,
                                           Map<String, Integer> activeLevels) {
        Objects.requireNonNull(incoming, "incoming");
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(activeLevels, "activeLevels");
        TreeMap<String, Integer> orderedLevels = new TreeMap<>(activeLevels);
        EnumMap<StatType, Double> result = new EnumMap<>(StatType.class);
        for (StatType stat : StatType.values()) {
            double add = selectedClass == null ? 0 : selectedClass.stats().add().getOrDefault(stat, 0.0);
            double factor = selectedClass == null ? 1 : selectedClass.stats().multiply().getOrDefault(stat, 1.0);
            for (var active : orderedLevels.entrySet()) {
                BlessingDefinition blessing = definitions.get(active.getKey());
                if (blessing == null || active.getValue() < 1 || active.getValue() > blessing.maxLevel()) {
                    throw new IllegalArgumentException("invalid active blessing " + active.getKey());
                }
                StatModifiers perLevel = blessing.perLevel();
                add += perLevel.add().getOrDefault(stat, 0.0) * active.getValue();
                factor *= Math.pow(perLevel.multiply().getOrDefault(stat, 1.0), active.getValue());
            }
            double raw = (incoming.getOrDefault(stat, 0.0) + add) * factor;
            result.put(stat, finiteClamp(raw, stat.minimum(), stat.maximum()));
        }
        return Map.copyOf(result);
    }

    private static double finiteClamp(double value, double minimum, double maximum) {
        if (Double.isNaN(value) || value == Double.NEGATIVE_INFINITY) return minimum;
        if (value == Double.POSITIVE_INFINITY) return maximum;
        return Math.clamp(value, minimum, maximum);
    }
}
