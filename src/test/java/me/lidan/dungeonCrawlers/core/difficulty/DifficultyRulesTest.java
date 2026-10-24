package me.lidan.dungeonCrawlers.core.difficulty;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DifficultyRulesTest {
    @Test void chestDiscountUsesExactWholeCoinsWithoutOverflow() {
        assertEquals(0, Difficulty.IMPOSSIBLE.chestPrice(0));
        assertEquals(949, Difficulty.HARD.chestPrice(999));
        assertEquals(759, Difficulty.HARD.chestPrice(999, true));
        assertEquals(2, Difficulty.HARD.chestPrice(3, true), "round only once after both discounts");
        assertEquals(44_000, Difficulty.IMPOSSIBLE.chestPrice(100_000, true));
        assertEquals(5_072_854_620_270_126_693L, Difficulty.IMPOSSIBLE.chestPrice(Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, Difficulty.NORMAL.chestPrice(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> Difficulty.HARD.chestPrice(-1));
    }

    @Test void tierBalanceAndMultiplicativePenalties() {
        assertEquals(10, Difficulty.values().length);
        var impossible = Difficulty.IMPOSSIBLE.defaults();
        assertEquals(50, impossible.healthMultiplier(true));
        assertEquals(45, impossible.incomingMultiplier(true, true));
        assertEquals(.32, impossible.outgoingMultiplier(true, true), 1e-9);
        assertEquals(.4, impossible.outgoingMultiplier(true, false), 1e-9);
        assertEquals(.6, impossible.fragmentChance(0));
        assertEquals(1, impossible.fragmentChance(100));
        assertFalse(Difficulty.HELLISH.defaults().ordinaryRevival());
        assertTrue(Difficulty.DEMONIC.defaults().ordinaryRevival());
        assertEquals(1, Difficulty.HARD.defaults().incomingMultiplier(true, false));
        assertEquals(.001, Difficulty.DEMONIC.defaults().runicChance());
        assertEquals(0, Difficulty.DEATH.defaults().runicBossChance());
        assertEquals(.01, Difficulty.VOID.defaults().runicBossChance());
        assertThrows(IllegalArgumentException.class, () -> new DifficultyRules(Difficulty.NORMAL,
                Double.NaN, 0, 1, 1, 1, 1, true, 0, 0, 0));
    }
}
