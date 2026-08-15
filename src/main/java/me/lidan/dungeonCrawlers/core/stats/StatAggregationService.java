package me.lidan.dungeonCrawlers.core.stats;

import me.lidan.cavecrawlers.stats.StatType;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.BlessingDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.BlessingStacking;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ClassDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.StatModifiers;

import java.util.LinkedHashMap;
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
        Map<String, BlessingDefinition> activeBlessings = new TreeMap<>();
        for (var active : orderedLevels.entrySet()) {
            BlessingDefinition blessing = definitions.get(active.getKey());
            int maximum = blessing != null && blessing.stacking() == BlessingStacking.REPLACE
                    ? 1 : blessing == null ? 0 : blessing.maxLevel();
            if (active.getValue() < 1 || active.getValue() > maximum) {
                throw new IllegalArgumentException("invalid active blessing " + active.getKey());
            }
            activeBlessings.put(active.getKey(), blessing);
        }
        Map<StatType, Double> result = new LinkedHashMap<>();
        for (StatType stat : StatType.values()) {
            double add = selectedClass == null ? 0 : selectedClass.stats().add().getOrDefault(stat, 0.0);
            double factor = selectedClass == null ? 1 : selectedClass.stats().multiply().getOrDefault(stat, 1.0);
            for (var active : orderedLevels.entrySet()) {
                BlessingDefinition blessing = activeBlessings.get(active.getKey());
                StatModifiers perLevel = blessing.perLevel();
                add += perLevel.add().getOrDefault(stat, 0.0) * active.getValue();
                factor *= Math.pow(perLevel.multiply().getOrDefault(stat, 1.0), active.getValue());
            }
            double raw = (incoming.getOrDefault(stat, 0.0) + add) * factor;
            result.put(stat, raw);
        }
        return Map.copyOf(result);
    }
}
