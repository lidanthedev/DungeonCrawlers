package me.lidan.dungeonCrawlers.core.random;

import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class WeightedChooser {
    private WeightedChooser() {
    }

    public static <T> T choose(List<Weighted<T>> entries, RandomGenerator random) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(random, "random");
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("weighted entries must not be empty");
        }
        double total = 0;
        for (Weighted<T> entry : entries) {
            total += entry.weight();
            if (!Double.isFinite(total)) {
                throw new IllegalArgumentException("total weight must be finite");
            }
        }
        double selected = random.nextDouble(total);
        for (Weighted<T> entry : entries) {
            selected -= entry.weight();
            if (selected < 0) {
                return entry.value();
            }
        }
        return entries.getLast().value();
    }

    public record Weighted<T>(T value, double weight) {
        public Weighted {
            Objects.requireNonNull(value, "value");
            if (!Double.isFinite(weight) || weight <= 0) {
                throw new IllegalArgumentException("weight must be finite and positive");
            }
        }
    }
}
