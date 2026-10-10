package me.lidan.dungeonCrawlers.core.encounter;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CounterweightTrialTest {
    private final CounterweightTrial trial = new CounterweightTrial(1500);
    private final CounterweightTrial.Position west = new CounterweightTrial.Position(-5, 0);
    private final CounterweightTrial.Position east = new CounterweightTrial.Position(5, 0);

    @Test void partyMustHoldOppositePadsContinuouslyAndCannotStackOnePad() {
        for (int time = 0; time <= 2000; time += 100) assertFalse(trial.update(time, List.of(west, west)));
        for (int time = 2100; time < 3600; time += 100) assertFalse(trial.update(time, List.of(west, east)));
        assertTrue(trial.update(3600, List.of(west, east)));
        assertTrue(trial.update(3700, List.of()));
    }
    @Test void steppingAwayResetsProgressAndEitherSoloPadWorks() {
        for (int time = 0; time <= 1000; time += 100) assertFalse(trial.update(time, List.of(west)));
        assertFalse(trial.update(1100, List.of(new CounterweightTrial.Position(0, 0))));
        assertEquals(0, trial.progress(1100));
        for (int time = 1200; time < 2700; time += 100) assertFalse(trial.update(time, List.of(east)));
        assertTrue(trial.update(2700, List.of(east)));
    }
    @Test void lagGapsAndBackwardClockCannotGrantUnobservedCharge() {
        trial.update(0, List.of(west)); trial.update(100, List.of(west));
        assertFalse(trial.update(2000, List.of(west)));
        assertEquals(0, trial.progress(2000));
        trial.update(2100, List.of(west)); trial.update(2000, List.of(west));
        assertEquals(0, trial.progress(2000));
    }
    @Test void departureLeavesASoloRequirementButAnEmptyArenaNeverCompletes() {
        trial.update(0, List.of(west, new CounterweightTrial.Position(0, 0)));
        for (int time = 100; time < 1600; time += 100) assertFalse(trial.update(time, List.of(west)));
        assertTrue(trial.update(1600, List.of(west)));
        var empty = new CounterweightTrial(100);
        assertFalse(empty.update(0, List.of())); assertFalse(empty.update(200, List.of()));
    }
}
