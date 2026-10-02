package me.lidan.dungeonCrawlers.core.difficulty;

import java.util.Objects;

public record DifficultyRules(Difficulty tier, double healthMultiplier, double duplicateClassReduction,
                              double incomingMultiplier, double isolationMultiplier, double xpMultiplier,
                              double magicFindMultiplier, boolean ordinaryRevival, double runicChance,
                              double fragmentChance, double runicBossChance) {
    public DifficultyRules {
        Objects.requireNonNull(tier);
        for (double value : new double[]{healthMultiplier, incomingMultiplier, isolationMultiplier,
                xpMultiplier, magicFindMultiplier}) {
            if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("invalid difficulty multiplier");
        }
        for (double chance : new double[]{duplicateClassReduction, runicChance, fragmentChance, runicBossChance}) {
            if (!Double.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("invalid difficulty probability");
        }
    }

    public boolean groupMechanics() { return tier.ordinal() >= Difficulty.HELLISH.ordinal(); }
    public double outgoingMultiplier(boolean duplicateClass, boolean protectedEnemy) {
        return (duplicateClass ? 1 - duplicateClassReduction : 1) * (groupMechanics() && protectedEnemy ? .8 : 1);
    }
    public double incomingMultiplier(boolean alone, boolean runic) {
        return incomingMultiplier * (groupMechanics() && alone ? isolationMultiplier : 1) * (runic ? 5 : 1);
    }
    public double healthMultiplier(boolean runic) { return healthMultiplier * (runic ? 10 : 1); }
    public double fragmentChance(double magicFind) { return Math.min(1, fragmentChance * (1 + Math.max(0, magicFind) / 100)); }
}
