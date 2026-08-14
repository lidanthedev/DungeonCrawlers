package me.lidan.dungeonCrawlers.core.reward;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RewardDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RewardItem;
import me.lidan.dungeonCrawlers.core.random.NamedRandomFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardRollerTest {
    @Test
    void rollsAreImmutableDeterministicBoundedAndUnique() {
        RewardDefinition definition = new RewardDefinition(true, 100, 0, 2, true, List.of(
                new RewardItem("a", 1, 2, 4), new RewardItem("b", 1, 5, 7)));
        RewardRoller roller = new RewardRoller();

        List<RewardRoller.RolledReward> first = roller.roll(definition,
                new NamedRandomFactory(42).stream("rewards:player"));
        List<RewardRoller.RolledReward> second = roller.roll(definition,
                new NamedRandomFactory(42).stream("rewards:player"));

        assertEquals(first, second);
        assertNotEquals(first.get(0).itemId(), first.get(1).itemId());
        first.forEach(item -> {
            RewardItem source = definition.items().stream().filter(candidate -> candidate.itemId().equals(item.itemId()))
                    .findFirst().orElseThrow();
            assertTrue(item.amount() >= source.minimumAmount());
            assertTrue(item.amount() <= source.maximumAmount());
        });
        assertThrows(UnsupportedOperationException.class, () -> first.add(first.getFirst()));
    }
}
