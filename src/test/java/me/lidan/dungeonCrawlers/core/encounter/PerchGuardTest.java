package me.lidan.dungeonCrawlers.core.encounter;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PerchGuardTest {
    private final UUID player = UUID.randomUUID();

    @Test void pullsOnlyAfterStayingHighForTheGracePeriodThenRepeatsEverySecond() {
        PerchGuard guard = new PerchGuard();
        assertFalse(guard.pull(player, 6, 0));
        assertFalse(guard.pull(player, 6, 2999));
        assertTrue(guard.pull(player, 6, 3000));
        assertFalse(guard.pull(player, 6, 3999));
        assertTrue(guard.pull(player, 6, 4000));
    }

    @Test void droppingBelowTheBossHeightResetsTheGrace() {
        PerchGuard guard = new PerchGuard();
        assertFalse(guard.pull(player, 6, 0));
        assertFalse(guard.pull(player, 2, 2500));
        assertFalse(guard.pull(player, 6, 2600));
        assertFalse(guard.pull(player, 6, 5500));
        assertTrue(guard.pull(player, 6, 5600));
    }
}
