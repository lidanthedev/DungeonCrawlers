package me.lidan.dungeonCrawlers.core.encounter;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FoundryPuzzleTest {
    @Test void verseIsSeededAndErrorsResetWithoutLockingThePuzzle() {
        var puzzle = new FoundryPuzzle(FoundryPuzzle.Kind.RESONANCE, 10010);
        assertEquals(4, puzzle.sequence().stream().distinct().count());
        assertEquals(FoundryPuzzle.Result.RESET, puzzle.press((puzzle.sequence().getFirst() + 1) % 4));
        for (int rune : puzzle.sequence()) puzzle.press(rune);
        assertTrue(puzzle.solved());
        assertEquals(FoundryPuzzle.Result.ALREADY_SOLVED, puzzle.press(0));
        assertFalse(new FoundryPuzzle(FoundryPuzzle.Kind.RESONANCE, 10010).solved(), "instances never share progress");
    }
    @Test void twoDistinctPlayersMustHoldForThreeContinuousSeconds() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var puzzle = new FoundryPuzzle(FoundryPuzzle.Kind.COUNTERWEIGHT, 1);
        assertEquals(FoundryPuzzle.Result.WAITING, puzzle.balance(a, a, 2, true, 1000));
        puzzle.balance(a, b, 2, false, 2000);
        assertFalse(puzzle.solved());
        puzzle.balance(a, null, 2, false, 4000);
        puzzle.balance(a, b, 2, false, 5000);
        puzzle.balance(a, b, 2, false, 7999);
        assertFalse(puzzle.solved());
        assertEquals(FoundryPuzzle.Result.SOLVED, puzzle.balance(a, b, 2, false, 8000));
    }
    @Test void soloLatchStillRequiresOneOccupiedPlateAndCannotBypassMultiplayer() {
        UUID a = UUID.randomUUID();
        var puzzle = new FoundryPuzzle(FoundryPuzzle.Kind.COUNTERWEIGHT, 1);
        puzzle.balance(a, null, 2, true, 0); puzzle.balance(a, null, 2, true, 4000);
        assertFalse(puzzle.solved());
        puzzle.balance(null, null, 1, true, 5000); assertFalse(puzzle.solved());
        puzzle.balance(a, null, 1, false, 6000); assertFalse(puzzle.solved());
        puzzle.balance(a, null, 1, true, 7000);
        assertEquals(FoundryPuzzle.Result.SOLVED, puzzle.balance(a, null, 1, true, 10000));
    }
    @Test void disconnectAndGhostTransitionsCanFallBackToSoloWithoutPermanentLock() {
        var puzzle = new FoundryPuzzle(FoundryPuzzle.Kind.COUNTERWEIGHT, 1);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        puzzle.balance(a, b, 2, false, 0);
        puzzle.balance(a, null, 1, false, 1000);
        puzzle.balance(a, null, 1, true, 2000);
        assertEquals(FoundryPuzzle.Result.SOLVED, puzzle.balance(a, null, 1, true, 5000));
    }
    @Test void lowerArenaHasConnectedRefugesAndRealGaps() {
        assertTrue(FoundryGeometry.lowerPlatform(0, 0));
        assertTrue(FoundryGeometry.lowerPlatform(0, 30));
        assertTrue(FoundryGeometry.lowerPlatform(17, 17));
        assertTrue(FoundryGeometry.lowerPlatform(10, 10));
        assertFalse(FoundryGeometry.lowerPlatform(10, 5));
        assertTrue(FoundryGeometry.lane(10, 0, 0, 1));
        assertFalse(FoundryGeometry.lane(10, 4, 0, 1));
    }
}
