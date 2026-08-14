package me.lidan.dungeonCrawlers.core.random;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeightedChooserTest {
    @Test
    void rejectsInvalidWeightsAndEmptyEntries() {
        assertThrows(IllegalArgumentException.class, () -> new WeightedChooser.Weighted<>("x", 0));
        assertThrows(IllegalArgumentException.class,
                () -> WeightedChooser.choose(List.of(), new SplittableRandom(1)));
    }

    @Test
    void selectionIsWeightProportional() {
        var entries = List.of(new WeightedChooser.Weighted<>("light", 1),
                new WeightedChooser.Weighted<>("heavy", 3));
        SplittableRandom random = new SplittableRandom(42);
        int heavy = 0;
        for (int index = 0; index < 10_000; index++) {
            if (WeightedChooser.choose(entries, random).equals("heavy")) heavy++;
        }
        assertTrue(heavy > 7_200 && heavy < 7_800, "heavy selections=" + heavy);
    }

    @Test
    void lastEntryIsResidueFallbackAndSingleEntryIsStable() {
        assertEquals("only", WeightedChooser.choose(
                List.of(new WeightedChooser.Weighted<>("only", 1)), new SplittableRandom(1)));
        assertEquals("last", WeightedChooser.choose(List.of(
                new WeightedChooser.Weighted<>("first", 1),
                new WeightedChooser.Weighted<>("last", 1_000)), new SplittableRandom(1)));
    }
}
