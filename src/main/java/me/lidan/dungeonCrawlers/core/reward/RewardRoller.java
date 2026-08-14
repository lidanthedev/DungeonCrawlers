package me.lidan.dungeonCrawlers.core.reward;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RewardDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RewardItem;
import me.lidan.dungeonCrawlers.core.random.WeightedChooser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class RewardRoller {
    public List<RolledReward> roll(RewardDefinition definition, RandomGenerator random) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(random, "random");
        if (!definition.enabled()) return List.of();
        if (definition.unique() && definition.rolls() > definition.items().size()) {
            throw new IllegalArgumentException("unique rolls exceed reward item count");
        }
        List<RewardItem> available = new ArrayList<>(definition.items());
        List<RolledReward> result = new ArrayList<>();
        for (int roll = 0; roll < definition.rolls(); roll++) {
            RewardItem selected = WeightedChooser.choose(available.stream()
                    .map(item -> new WeightedChooser.Weighted<>(item, item.weight())).toList(), random);
            int amount = selected.minimumAmount() == selected.maximumAmount() ? selected.minimumAmount()
                    : Math.toIntExact(random.nextLong(selected.minimumAmount(), (long) selected.maximumAmount() + 1));
            result.add(new RolledReward(selected.itemId(), amount));
            if (definition.unique()) available.remove(selected);
        }
        return List.copyOf(result);
    }

    public record RolledReward(String itemId, int amount) {
        public RolledReward {
            Objects.requireNonNull(itemId);
            if (amount < 1) throw new IllegalArgumentException("amount must be positive");
        }
    }
}
