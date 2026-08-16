package me.lidan.dungeonCrawlers.core.stats;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.BlessingDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.BlessingStacking;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class BlessingLevels {
    private final TreeMap<String, Integer> levels = new TreeMap<>();

    public synchronized DiscoveryResult discover(BlessingDefinition blessing) {
        return discover(blessing, 1);
    }

    public synchronized DiscoveryResult discover(BlessingDefinition blessing, int discoveries) {
        Objects.requireNonNull(blessing, "blessing");
        if (discoveries < 1) throw new IllegalArgumentException("discoveries must be positive");
        int previous = levels.getOrDefault(blessing.id(), 0);
        int effectiveCap = blessing.stacking() == BlessingStacking.REPLACE ? 1 : blessing.maxLevel();
        int target = (int) Math.min(effectiveCap, (long) previous + discoveries);
        levels.put(blessing.id(), target);
        return new DiscoveryResult(blessing.id(), previous, target, discoveries, target != previous,
                target == effectiveCap);
    }

    public synchronized Map<String, Integer> snapshot() {
        return Map.copyOf(levels);
    }

    public synchronized boolean remove(String blessingId) {
        Objects.requireNonNull(blessingId, "blessingId");
        return levels.remove(blessingId) != null;
    }

    public synchronized void clear() {
        levels.clear();
    }

    public record DiscoveryResult(String blessingId, int previousLevel, int currentLevel,
                                  int levelsAwarded, boolean levelChanged, boolean atCap) {
        public DiscoveryResult {
            Objects.requireNonNull(blessingId, "blessingId");
            if (previousLevel < 0) throw new IllegalArgumentException("previousLevel must be non-negative");
            if (currentLevel < 0) throw new IllegalArgumentException("currentLevel must be non-negative");
            if (levelsAwarded < 1) throw new IllegalArgumentException("levelsAwarded must be positive");
        }

        /** Compatibility constructor for callers that only know the effective level change. */
        public DiscoveryResult(String blessingId, int previousLevel, int currentLevel,
                               boolean levelChanged, boolean atCap) {
            this(blessingId, previousLevel, currentLevel,
                    Math.max(1, currentLevel - previousLevel), levelChanged, atCap);
        }
    }
}
