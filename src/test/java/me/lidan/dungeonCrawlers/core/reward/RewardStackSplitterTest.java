package me.lidan.dungeonCrawlers.core.reward;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RewardStackSplitterTest {
    @Test
    void splitsLargeRolledRewardWithoutChangingItsTotal() {
        assertEquals(List.of(new RewardRoller.RolledReward("a", 64),
                        new RewardRoller.RolledReward("a", 36)),
                new RewardStackSplitter().split(new RewardRoller.RolledReward("a", 100), 64));
    }
}
