package me.lidan.dungeonCrawlers.core.encounter;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingmasterGeometryTest {
    @Test void suitQuartersAgreeWithCardinalPositionsAndWrapAtNegativeAngles() {
        assertEquals(0, RingmasterGeometry.sector(10, 0));
        assertEquals(1, RingmasterGeometry.sector(0, 10));
        assertEquals(2, RingmasterGeometry.sector(-10, 0));
        assertEquals(3, RingmasterGeometry.sector(0, -10));
        assertEquals(0, RingmasterGeometry.sector(10, -.01));
    }
    @Test void sweepIsTwoBlocksWideExcludesHubAndOppositeSide() {
        assertTrue(RingmasterGeometry.inSweep(10, .9, 0));
        assertFalse(RingmasterGeometry.inSweep(10, 1.1, 0));
        assertFalse(RingmasterGeometry.inSweep(-10, 0, 0));
        assertFalse(RingmasterGeometry.inSweep(2, 0, 0));
        assertFalse(RingmasterGeometry.inSweep(43, 0, 0));
        assertTrue(RingmasterGeometry.inSweep(0, 10, Math.PI / 2));
    }
    @Test void carouselWarnsForTwoSecondsThenTurnsFullyInEightSeconds() {
        double initial = .7;
        assertEquals(initial, RingmasterGeometry.carouselAngle(initial, 0));
        assertEquals(initial, RingmasterGeometry.carouselAngle(initial, 1999));
        assertEquals(initial, RingmasterGeometry.carouselAngle(initial, 2000));
        assertEquals(initial + Math.PI / 4, RingmasterGeometry.carouselAngle(initial, 3000), .0001);
        assertEquals(initial + Math.PI, RingmasterGeometry.carouselAngle(initial, 6000), .0001);
        assertEquals(initial + Math.PI * 2, RingmasterGeometry.carouselAngle(initial, 10000), .0001);
        assertEquals(initial + Math.PI * 2, RingmasterGeometry.carouselAngle(initial, 18000), .0001);
    }
    @Test void deckGapWrapsAroundAndDoesNotSwallowTheWholeRing() {
        assertTrue(RingmasterGeometry.inDeckGap(-.1, Math.PI * 2 - .1));
        assertFalse(RingmasterGeometry.inDeckGap(Math.PI, 0));
        assertFalse(RingmasterGeometry.inDeckGap(Math.PI / 2, 0));
    }
}
