package me.lidan.dungeonCrawlers.commands;

import me.lidan.dungeonCrawlers.core.door.DoorService;
import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.reward.RewardEntitlementService;
import me.lidan.dungeonCrawlers.core.reward.RewardRoller;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Facing;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.integration.CaveItemsGateway;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonPhaseElevenCommandTest {
    private static final UUID INSTANCE = UUID.fromString("00000000-0000-0000-0000-000000000031");
    private static final UUID ACTIVE = UUID.fromString("00000000-0000-0000-0000-000000000032");
    private static final UUID OFFLINE = UUID.fromString("00000000-0000-0000-0000-000000000033");
    private static final UUID REMOVED = UUID.fromString("00000000-0000-0000-0000-000000000034");

    @BeforeEach
    void setUpBukkit() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDownBukkit() {
        MockBukkit.unmock();
    }

    @Test
    void rewardIconBuildsTheRolledCaveItemInsteadOfAChestPlaceholder() {
        RewardEntitlementService.RewardOffer offer = new RewardEntitlementService.RewardOffer(
                UUID.randomUUID(), "wooden", 0, false,
                List.of(new RewardRoller.RolledReward("UNDEAD_ESSENCE", 3)));
        ItemStack built = Mockito.mock(ItemStack.class);
        List<String> builtIds = new ArrayList<>();
        CaveItemsGateway caveItems = new CaveItemsGateway() {
            @Override
            public boolean isConfigured(String itemId) {
                return "UNDEAD_ESSENCE".equals(itemId);
            }

            @Override
            public Optional<ItemStack> build(String itemId, int amount) {
                builtIds.add(itemId + ":" + amount);
                return Optional.of(built);
            }
        };

        ItemStack icon = DungeonPhaseElevenCommand.rewardIcon(offer, caveItems);

        assertSame(built, icon);
        assertEquals(List.of("UNDEAD_ESSENCE:3"), builtIds);
    }

    @Test
    void previewPaginatesEveryRolledRewardInTheCenteredSlot() {
        RewardEntitlementService.RewardOffer offer = new RewardEntitlementService.RewardOffer(
                UUID.randomUUID(), "wooden", 0, false,
                List.of(new RewardRoller.RolledReward("A", 1), new RewardRoller.RolledReward("B", 2)));
        List<String> builtIds = new ArrayList<>();
        CaveItemsGateway caveItems = new CaveItemsGateway() {
            @Override
            public boolean isConfigured(String itemId) { return true; }

            @Override
            public Optional<ItemStack> build(String itemId, int amount) {
                builtIds.add(itemId + ":" + amount);
                return Optional.of(Mockito.mock(ItemStack.class));
            }
        };

        List<ItemStack> firstPage = DungeonPhaseElevenCommand.previewIcons(offer, caveItems, 0);
        List<ItemStack> secondPage = DungeonPhaseElevenCommand.previewIcons(offer, caveItems, 1);

        assertEquals(1, firstPage.size());
        assertEquals(1, secondPage.size());
        assertEquals(List.of("A:1", "B:2"), builtIds);
        assertEquals(List.of("A"), DungeonPhaseElevenCommand.previewRolls(offer, 0).stream()
                .map(RewardRoller.RolledReward::itemId).toList());
        assertEquals(List.of("B"), DungeonPhaseElevenCommand.previewRolls(offer, 1).stream()
                .map(RewardRoller.RolledReward::itemId).toList());
    }

    @Test
    void emptyPreviewKeepsTheFallbackIcon() {
        RewardEntitlementService.RewardOffer offer = new RewardEntitlementService.RewardOffer(
                UUID.randomUUID(), "wooden", 0, false, List.of());

        assertTrue(DungeonPhaseElevenCommand.previewRolls(offer, 0).isEmpty());
        assertEquals(Material.REDSTONE_BLOCK, DungeonPhaseElevenCommand.previewIcons(
                offer, new CaveItemsGateway() {
                    @Override
                    public boolean isConfigured(String itemId) {
                        return false;
                    }

                    @Override
                    public Optional<ItemStack> build(String itemId, int amount) {
                        return Optional.empty();
                    }
                }, 0).getFirst().getType());
    }

    @Test
    void consoleResetUsesLifecycleEligibilityAndCurrentPresence() {
        RunPreparationService.RunSnapshot run = new RunPreparationService.RunSnapshot(
                INSTANCE, RunPreparationService.RunState.RUNNING,
                List.of(ACTIVE, OFFLINE, REMOVED), List.of("tank"), java.util.Map.of(), true,
                Instant.parse("2026-08-22T00:00:00Z"), Instant.parse("2026-08-22T00:01:00Z"),
                true, Instant.parse("2026-08-22T00:01:00Z"),
                new DoorService.DoorSnapshot(INSTANCE, new Point(0, 0, 0), Facing.NORTH,
                        DoorService.DoorState.OPEN));
        PlayerLifecycleService.InstanceSnapshot lifecycle = new PlayerLifecycleService.InstanceSnapshot(
                INSTANCE, true, false, "running", List.of(
                new PlayerLifecycleService.PlayerSnapshot(ACTIVE, PlayerLifecycleService.PlayerState.ALIVE,
                        true, null, null, 0),
                new PlayerLifecycleService.PlayerSnapshot(OFFLINE, PlayerLifecycleService.PlayerState.GHOST,
                        false, Instant.parse("2026-08-22T00:02:00Z"), null, 1),
                new PlayerLifecycleService.PlayerSnapshot(REMOVED, PlayerLifecycleService.PlayerState.REMOVED,
                        true, null, null, 0)));

        List<RewardEntitlementService.Participant> participants = DungeonPhaseElevenCommand.rewardResetParticipants(
                run, lifecycle, playerId -> playerId.equals(ACTIVE));

        assertEquals(List.of(ACTIVE, OFFLINE), participants.stream()
                .map(RewardEntitlementService.Participant::playerId).toList());
        assertTrue(participants.getFirst().onlineAtCompletion());
        assertFalse(participants.get(1).onlineAtCompletion());
    }

    @Test
    void previewTitleFormatsPriceAndLabelsFreeRewards() {
        RewardEntitlementService.RewardOffer priced = new RewardEntitlementService.RewardOffer(
                UUID.randomUUID(), "wooden", 1_234, false, List.of());
        RewardEntitlementService.RewardOffer free = new RewardEntitlementService.RewardOffer(
                UUID.randomUUID(), "wooden", 0, false, List.of());

        assertEquals("<dark_purple>Reward Preview - <gold>1,234</gold></dark_purple>",
                DungeonPhaseElevenCommand.previewTitle(priced));
        assertEquals("<dark_purple>Reward Preview - <gold>FREE</gold></dark_purple>",
                DungeonPhaseElevenCommand.previewTitle(free));
    }

    @Test
    void overviewOffersSortByMinimumScoreThenRewardId() {
        RewardEntitlementService.RewardOffer high = new RewardEntitlementService.RewardOffer(
                UUID.randomUUID(), "gold", 100, 200, false, List.of());
        RewardEntitlementService.RewardOffer lowB = new RewardEntitlementService.RewardOffer(
                UUID.randomUUID(), "bronze", 0, 0, false, List.of());
        RewardEntitlementService.RewardOffer lowA = new RewardEntitlementService.RewardOffer(
                UUID.randomUUID(), "apple", 0, 0, false, List.of());

        assertEquals(List.of("apple", "bronze", "gold"),
                DungeonPhaseElevenCommand.sortedOffers(List.of(high, lowB, lowA)).stream()
                        .map(RewardEntitlementService.RewardOffer::rewardId)
                        .toList());
    }
}
