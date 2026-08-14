package me.lidan.dungeonCrawlers.core.score;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ScoreService {
    private static final Duration FREE_TIME = Duration.ofMinutes(8);

    public ScoreResult calculate(ScoreInput input, List<BonusProvider> providers) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(providers, "providers");
        int skill = input.successful() ? Math.max(0, 100 - 2 * input.deaths()) : 0;
        long overtimeMillis = Math.max(0, input.elapsed().minus(FREE_TIME).toMillis());
        long penaltyMinutes = (overtimeMillis + Duration.ofMinutes(1).toMillis() - 1)
                / Duration.ofMinutes(1).toMillis();
        int time = (int) Math.max(0, 100 - 2 * penaltyMinutes);
        int exploration = input.totalSecrets() == 0 ? 100 : BigDecimal.valueOf(input.foundSecrets())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(input.totalSecrets()), 0, RoundingMode.HALF_UP)
                .intValueExact();

        List<BonusProvider> ordered = new ArrayList<>(providers);
        ordered.sort(Comparator.comparingInt(BonusProvider::priority).thenComparing(BonusProvider::id));
        Map<String, BonusFact> uniqueFacts = new LinkedHashMap<>();
        ScoreSnapshot snapshot = new ScoreSnapshot(input.successful(), input.deaths(), input.elapsed(),
                input.foundSecrets(), input.totalSecrets(), skill, time, exploration);
        for (BonusProvider provider : ordered) {
            for (BonusFact fact : List.copyOf(provider.evaluate(snapshot))) {
                uniqueFacts.putIfAbsent(fact.key(), fact);
            }
        }
        int bonus = uniqueFacts.values().stream().mapToInt(BonusFact::points).sum();
        bonus = Math.clamp(bonus, 0, 100);
        int total = skill + time + exploration + bonus;
        return new ScoreResult(skill, time, exploration, bonus, total, DungeonRank.forScore(total),
                List.copyOf(uniqueFacts.values()));
    }

    public record ScoreInput(boolean successful, int deaths, Duration elapsed, int foundSecrets, int totalSecrets) {
        public ScoreInput {
            Objects.requireNonNull(elapsed, "elapsed");
            if (deaths < 0 || elapsed.isNegative() || foundSecrets < 0 || totalSecrets < 0
                    || foundSecrets > totalSecrets) {
                throw new IllegalArgumentException("invalid score input");
            }
        }
    }

    public record ScoreSnapshot(boolean successful, int deaths, Duration elapsed, int foundSecrets,
                                int totalSecrets, int skill, int time, int exploration) {
    }

    public record BonusFact(String key, int points, String detail) {
        public BonusFact {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(detail, "detail");
            if (key.isBlank()) throw new IllegalArgumentException("bonus key must not be blank");
        }
    }

    public interface BonusProvider {
        String id();
        int priority();
        List<BonusFact> evaluate(ScoreSnapshot snapshot);
    }

    public record ScoreResult(int skill, int time, int exploration, int bonus, int total, DungeonRank rank,
                              List<BonusFact> bonusFacts) {
        public ScoreResult {
            bonusFacts = List.copyOf(bonusFacts);
        }
    }
}
