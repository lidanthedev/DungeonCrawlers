package me.lidan.dungeonCrawlers.core.stats;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.BlessingDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.BlessingStacking;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class BlessingLevels {
    private final TreeMap<String, Integer> levels = new TreeMap<>();

    public synchronized DiscoveryResult discover(BlessingDefinition blessing) {
        Objects.requireNonNull(blessing, "blessing");
        int previous = levels.getOrDefault(blessing.id(), 0);
        int effectiveCap = blessing.stacking() == BlessingStacking.REPLACE ? 1 : blessing.maxLevel();
        int target = Math.min(effectiveCap, previous + 1);
        levels.put(blessing.id(), target);
        return new DiscoveryResult(blessing.id(), previous, target, target != previous,
                target == effectiveCap);
    }

    public synchronized Map<String, Integer> snapshot() {
        return Map.copyOf(levels);
    }

    public record DiscoveryResult(String blessingId, int previousLevel, int currentLevel,
                                  boolean levelChanged, boolean atCap) { }
}
