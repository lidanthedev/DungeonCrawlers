package me.lidan.dungeonCrawlers.core.random;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NamedRandomFactoryTest {
    @Test
    void streamsAreStableAndIndependent() {
        NamedRandomFactory factory = new NamedRandomFactory(42);
        long layout = factory.stream("layout").nextLong();
        factory.stream("rewards:player-a").nextLong();
        assertEquals(layout, factory.stream("layout").nextLong());
        assertNotEquals(layout, factory.stream("mobs").nextLong());
    }

    @Test
    void chooserRejectsInvalidWeights() {
        assertThrows(IllegalArgumentException.class, () -> new WeightedChooser.Weighted<>("x", 0));
        assertThrows(IllegalArgumentException.class, () -> WeightedChooser.choose(List.of(), factoryRandom()));
    }

    private static java.util.SplittableRandom factoryRandom() {
        return new java.util.SplittableRandom(1);
    }
}
