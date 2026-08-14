package me.lidan.dungeonCrawlers.core.random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class NamedRandomFactoryTest {
    @Test
    void streamsAreStableAndIndependent() {
        NamedRandomFactory factory = new NamedRandomFactory(42);
        long layout = factory.stream("layout").nextLong();
        factory.stream("rewards:player-a").nextLong();
        assertEquals(layout, factory.stream("layout").nextLong());
        assertNotEquals(layout, factory.stream("mobs").nextLong());
    }
}
