package me.lidan.dungeonCrawlers.integration;

import me.lidan.cavecrawlers.stats.StatType;
import me.lidan.cavecrawlers.stats.Stats;
import me.lidan.cavecrawlers.stats.StatsManager;
import me.lidan.cavecrawlers.CaveCrawlers;
import me.lidan.dungeonCrawlers.commands.DungeonPhaseFiveCommand;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.secret.SecretDiscoveryService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.Optional;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BukkitDungeonRunListenerTest {
    @BeforeAll
    static void initializeNativeStatsWithPluginFixture() throws Exception {
        try (var nativePlugin = mockStatic(org.bukkit.plugin.java.JavaPlugin.class)) {
            nativePlugin.when(() -> org.bukkit.plugin.java.JavaPlugin.getPlugin(CaveCrawlers.class)).thenReturn(null);
            Class.forName(StatsManager.class.getName());
        }
    }

    private final UUID instance = UUID.randomUUID(), playerId = UUID.randomUUID();
    private final Player player = mock(Player.class);
    private final DungeonPhaseFiveCommand phaseFive = mock(DungeonPhaseFiveCommand.class);
    private final RunPreparationService runs = mock(RunPreparationService.class);
    private final SecretDiscoveryService secrets = mock(SecretDiscoveryService.class);
    private final Point point = new Point(0, 64, 0);

    private BukkitDungeonRunListener listener() {
        when(player.getUniqueId()).thenReturn(playerId);
        when(runs.instanceFor(playerId)).thenReturn(Optional.of(instance));
        when(phaseFive.canOpenDungeonDoor(playerId)).thenReturn(true);
        return new BukkitDungeonRunListener(phaseFive, runs, "dungeon_instances", secrets);
    }

    private PlayerInteractEvent interaction(Material material, EquipmentSlot hand) {
        World world = mock(World.class);
        when(world.getName()).thenReturn("dungeon_instances");
        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getType()).thenReturn(material);
        when(block.getY()).thenReturn(64);
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP, hand);
    }

    @Test
    void skillLevelReachesRunStatAggregation() {
        var listener = listener();
        var selected = new me.lidan.dungeonCrawlers.config.registry.ConfigModels.ClassDefinition(
                "berserker", "Berserker", Material.IRON_SWORD,
                new me.lidan.dungeonCrawlers.config.registry.ConfigModels.StatModifiers(Map.of(), Map.of()),
                Map.of(StatType.STRENGTH, 2.0), 0);
        when(phaseFive.selectedClass(playerId)).thenReturn(Optional.of(selected));
        when(secrets.aggregate(eq(instance), eq(selected), anyMap(), eq(20)))
                .thenReturn(Map.of(StatType.STRENGTH, 140.0));
        Stats stats = new Stats();
        stats.set(StatType.STRENGTH, 100);
        try (var levels = mockStatic(DungeonClassScaling.class)) {
            levels.when(() -> DungeonClassScaling.dungeonLevel(player)).thenReturn(20);
            listener.onStatsCalculate(new me.lidan.cavecrawlers.stats.StatsCalculateEvent(player, stats));
        }
        assertEquals(140, stats.get(StatType.STRENGTH).getValue());
        verify(secrets).aggregate(eq(instance), eq(selected), anyMap(), eq(20));
    }

    @Test
    void newSecretRestoresOnlyFinderAndRepeatedDiscoveryDoesNotRefill() {
        var listener = listener();
        var manager = mock(StatsManager.class);
        Stats stats = new Stats();
        stats.set(StatType.INTELLIGENCE, 1234);
        stats.set(StatType.MANA, 5);
        when(manager.getStats(player)).thenReturn(stats);
        when(secrets.discover(instance, playerId, point)).thenReturn(
                SecretDiscoveryService.DiscoveryResult.discovered(null, null, null),
                SecretDiscoveryService.DiscoveryResult.alreadyFound(null));
        try (var nativeStats = mockStatic(StatsManager.class)) {
            nativeStats.when(StatsManager::getInstance).thenReturn(manager);
            var first = interaction(Material.TRAPPED_CHEST, EquipmentSlot.HAND);
            listener.onSecretInteract(first);
            assertTrue(first.isCancelled());
            assertEquals(1234, stats.get(StatType.MANA).getValue());
            nativeStats.verify(() -> StatsManager.healPlayerPercent(player, 100D));
            verify(player).sendMessage(argThat((net.kyori.adventure.text.Component message) ->
                    PlainTextComponentSerializer.plainText().serialize(message)
                            .contains("Health and mana fully restored")));
            stats.set(StatType.MANA, 10);
            listener.onSecretInteract(interaction(Material.TRAPPED_CHEST, EquipmentSlot.HAND));
            assertEquals(10, stats.get(StatType.MANA).getValue());
            nativeStats.verify(() -> StatsManager.healPlayerPercent(player, 100D), times(1));
            verify(manager, times(1)).getStats(player);
        }
    }

    @Test
    void ordinaryChestAndFailureDoNotRefill() {
        var listener = listener();
        when(secrets.discover(instance, playerId, point)).thenReturn(
                SecretDiscoveryService.DiscoveryResult.discovered(null, null, null),
                SecretDiscoveryService.DiscoveryResult.failure("not a secret"));
        try (var nativeStats = mockStatic(StatsManager.class)) {
            listener.onSecretInteract(interaction(Material.CHEST, EquipmentSlot.HAND));
            listener.onSecretInteract(interaction(Material.TRAPPED_CHEST, EquipmentSlot.HAND));
            nativeStats.verifyNoInteractions();
        }
    }

    @Test
    void offhandGhostAndNonParticipantCannotDiscoverOrRefill() {
        var listener = listener();
        try (var nativeStats = mockStatic(StatsManager.class)) {
            listener.onSecretInteract(interaction(Material.TRAPPED_CHEST, EquipmentSlot.OFF_HAND));
            when(phaseFive.canOpenDungeonDoor(playerId)).thenReturn(false);
            listener.onSecretInteract(interaction(Material.TRAPPED_CHEST, EquipmentSlot.HAND));
            when(runs.instanceFor(playerId)).thenReturn(Optional.empty());
            listener.onSecretInteract(interaction(Material.TRAPPED_CHEST, EquipmentSlot.HAND));
            verify(secrets, never()).discover(any(), any(), any());
            nativeStats.verifyNoInteractions();
        }
    }
}
