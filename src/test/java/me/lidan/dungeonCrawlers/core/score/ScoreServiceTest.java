package me.lidan.dungeonCrawlers.core.score;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreServiceTest {
    private final ScoreService service = new ScoreService();

    @Test
    void contractExamplesAndBoundaries() {
        assertEquals(96, score(true, 2, 8, 0, 0).skill());
        assertEquals(0, score(false, 0, 8, 0, 0).skill());
        assertEquals(76, score(true, 0, 20, 0, 0).time());
        assertEquals(92, score(true, 0, 8, 11, 12).exploration());
        assertEquals(100, score(true, 0, 8, 0, 0).exploration());
        assertEquals(DungeonRank.A, DungeonRank.forScore(266));
        assertEquals(DungeonRank.S, DungeonRank.forScore(270));
        assertEquals(DungeonRank.S_PLUS, DungeonRank.forScore(300));
    }

    @Test
    void bonusProvidersAreOrderedDeduplicatedAndClamped() {
        ScoreService.BonusProvider later = provider("z", 10,
                List.of(new ScoreService.BonusFact("same", 80, "later")));
        ScoreService.BonusProvider earlier = provider("a", 0,
                List.of(new ScoreService.BonusFact("same", 30, "first"),
                        new ScoreService.BonusFact("extra", 90, "extra")));

        var result = service.calculate(new ScoreService.ScoreInput(true, 0, Duration.ofMinutes(8), 0, 0),
                List.of(later, earlier));

        assertEquals(100, result.bonus());
        assertEquals(List.of("same", "extra"), result.bonusFacts().stream().map(ScoreService.BonusFact::key).toList());
        assertEquals("first", result.bonusFacts().getFirst().detail());
    }

    private ScoreService.ScoreResult score(boolean success, int deaths, long minutes, int found, int total) {
        return service.calculate(new ScoreService.ScoreInput(success, deaths, Duration.ofMinutes(minutes), found, total),
                List.of());
    }

    private static ScoreService.BonusProvider provider(String id, int priority, List<ScoreService.BonusFact> facts) {
        return new ScoreService.BonusProvider() {
            public String id() { return id; }
            public int priority() { return priority; }
            public List<ScoreService.BonusFact> evaluate(ScoreService.ScoreSnapshot snapshot) { return facts; }
        };
    }
}
