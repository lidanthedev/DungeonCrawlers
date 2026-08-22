package me.lidan.dungeonCrawlers.core.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Splits a rolled reward into payload-sized stacks without changing its total amount. */
public final class RewardStackSplitter {
    public List<RewardRoller.RolledReward> split(RewardRoller.RolledReward reward, int maxStackSize) {
        Objects.requireNonNull(reward, "reward");
        if (maxStackSize < 1) throw new IllegalArgumentException("max stack size must be positive");
        List<RewardRoller.RolledReward> stacks = new ArrayList<>();
        int remaining = reward.amount();
        while (remaining > 0) {
            int amount = Math.min(remaining, maxStackSize);
            stacks.add(new RewardRoller.RolledReward(reward.itemId(), amount));
            remaining -= amount;
        }
        return List.copyOf(stacks);
    }

    public List<RewardRoller.RolledReward> splitAll(List<RewardRoller.RolledReward> rewards, int maxStackSize) {
        Objects.requireNonNull(rewards, "rewards");
        List<RewardRoller.RolledReward> result = new ArrayList<>();
        rewards.forEach(reward -> result.addAll(split(reward, maxStackSize)));
        return List.copyOf(result);
    }
}
