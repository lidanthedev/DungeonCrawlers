package me.lidan.dungeonCrawlers.core.difficulty;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.math.RoundingMode;

public enum Difficulty {
    NORMAL(1, 0, 1, 1, 1, 1, 0, 0, 0),
    HARD(1, .05, 1, 1, 1.25, 1.05, 0, 0, 0),
    INSANE(1.5, .10, 1, 1, 1.5, 1.10, 0, 0, 0),
    EXTREME(1.5, .15, 1, 1, 1.75, 1.15, 0, 0, 0),
    DEMONIC(2, .20, 1, 1, 2, 1.20, .001, .10, 0),
    HELLISH(2.5, .25, 1.20, 1.25, 2.5, 1.25, .002, .20, 0),
    DEATH(2.5, .30, 1.50, 1.50, 3, 1.30, .003, .30, 0),
    VOID(3, .40, 1.80, 2, 3.5, 1.35, .004, .40, .01),
    HARDCORE(4, .50, 2.40, 2.50, 4, 1.40, .005, .50, .02),
    IMPOSSIBLE(5, .60, 3, 3, 5, 1.50, .006, .60, .03);

    private final DifficultyRules defaults;

    Difficulty(double health, double duplicateReduction, double incoming, double isolation,
               double xp, double magicFind, double runic, double fragments, double boss) {
        defaults = new DifficultyRules(this, health, duplicateReduction, incoming, isolation, xp, magicFind,
                ordinal() < 5, runic, fragments, boss);
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String displayName() { return name().charAt(0) + id().substring(1); }
    public DifficultyRules defaults() { return defaults; }
    public Difficulty next() { return ordinal() + 1 < values().length ? values()[ordinal() + 1] : this; }

    public int chestDiscountPercent() { return ordinal() * 5; }

    public long chestPrice(long basePrice) {
        return chestPrice(basePrice, false);
    }

    public long chestPrice(long basePrice, boolean runicBoss) {
        if (basePrice < 0) throw new IllegalArgumentException("chest price must not be negative");
        return BigDecimal.valueOf(basePrice).multiply(BigDecimal.valueOf(100 - chestDiscountPercent()))
                .multiply(BigDecimal.valueOf(runicBoss ? 80 : 100))
                .divide(BigDecimal.valueOf(10000), 0, RoundingMode.DOWN).longValueExact();
    }

    public static Map<String, DifficultyRules> defaultRules() {
        return Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(Difficulty::id, Difficulty::defaults));
    }
}
