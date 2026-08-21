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
        return calculateReport(input, providers).result();
    }

    /**
     * Calculates the final score once. The returned report is the immutable value that must be
     * shared by result rendering and later entitlement processing; neither consumer evaluates
     * bonus providers again.
     */
    public ScoreReport calculateReport(ScoreInput input, List<BonusProvider> providers) {
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

        List<BonusProvider> ordered = orderedProviders(providers);
        Map<String, BonusFact> uniqueFacts = new LinkedHashMap<>();
        ScoreSnapshot snapshot = new ScoreSnapshot(input.successful(), input.deaths(), input.elapsed(),
                input.foundSecrets(), input.totalSecrets(), skill, time, exploration);
        for (BonusProvider provider : ordered) {
            List<BonusFact> facts = provider.evaluate(snapshot);
            if (facts == null) throw new IllegalArgumentException("bonus provider returned null facts: " + provider.id());
            for (BonusFact fact : List.copyOf(facts)) {
                Objects.requireNonNull(fact, "bonus fact");
                uniqueFacts.putIfAbsent(fact.key(), fact);
            }
        }
        int bonus = uniqueFacts.values().stream().mapToInt(BonusFact::points).sum();
        bonus = Math.clamp(bonus, 0, 100);
        int total = skill + time + exploration + bonus;
        List<BonusFact> facts = List.copyOf(uniqueFacts.values());
        DungeonRank rank = DungeonRank.forScore(total);
        ScoreResult result = new ScoreResult(skill, time, exploration, bonus, total, rank, facts);
        return new ScoreReport(new FinalScoreSnapshot(input, skill, time, exploration, bonus,
                total, rank, facts), result);
    }

    public ScoreReport calculateReport(ScoreInput input, BonusProviderRegistry providers) {
        Objects.requireNonNull(providers, "providers");
        return calculateReport(input, providers.providers());
    }

    private static List<BonusProvider> orderedProviders(List<BonusProvider> providers) {
        List<BonusProvider> ordered = new ArrayList<>(providers.size());
        for (BonusProvider provider : providers) {
            Objects.requireNonNull(provider, "bonus provider");
            String id = provider.id();
            if (id == null || id.isBlank()) throw new IllegalArgumentException("bonus provider id must not be blank");
            ordered.add(provider);
        }
        ordered.sort(Comparator.comparingInt(BonusProvider::priority).thenComparing(BonusProvider::id));
        return List.copyOf(ordered);
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
        public ScoreSnapshot {
            Objects.requireNonNull(elapsed, "elapsed");
            if (deaths < 0 || elapsed.isNegative() || foundSecrets < 0 || totalSecrets < 0
                    || foundSecrets > totalSecrets) {
                throw new IllegalArgumentException("invalid score snapshot");
            }
        }
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
            Objects.requireNonNull(rank, "rank");
            Objects.requireNonNull(bonusFacts, "bonusFacts");
            bonusFacts = List.copyOf(bonusFacts);
        }
    }

    /** Immutable score state containing the exact input and all final category values. */
    public record FinalScoreSnapshot(ScoreInput input, int skill, int time, int exploration,
                                     int bonus, int total, DungeonRank rank, List<BonusFact> bonusFacts) {
        public FinalScoreSnapshot {
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(rank, "rank");
            Objects.requireNonNull(bonusFacts, "bonusFacts");
            bonusFacts = List.copyOf(bonusFacts);
        }

        public boolean successful() { return input.successful(); }
        public int deaths() { return input.deaths(); }
        public Duration elapsed() { return input.elapsed(); }
        public int foundSecrets() { return input.foundSecrets(); }
        public int totalSecrets() { return input.totalSecrets(); }
    }

    /** One-shot finalization result shared by rendering and downstream persistence. */
    public record ScoreReport(FinalScoreSnapshot snapshot, ScoreResult result) {
        public ScoreReport {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(result, "result");
        }

        public FinalScoreSnapshot finalSnapshot() { return snapshot; }
    }

    /** Startup-owned immutable ordering boundary for bonus providers. */
    public static final class BonusProviderRegistry {
        private final Map<String, BonusProvider> providers = new LinkedHashMap<>();

        public BonusProviderRegistry() { }

        public BonusProviderRegistry(List<? extends BonusProvider> providers) {
            Objects.requireNonNull(providers, "providers").forEach(this::register);
        }

        public synchronized void register(BonusProvider provider) {
            Objects.requireNonNull(provider, "provider");
            String id = provider.id();
            if (id == null || id.isBlank()) throw new IllegalArgumentException("bonus provider id must not be blank");
            if (providers.putIfAbsent(id, provider) != null) {
                throw new IllegalArgumentException("duplicate bonus provider id: " + id);
            }
        }

        public synchronized List<BonusProvider> providers() {
            return orderedProviders(List.copyOf(providers.values()));
        }
    }
}
