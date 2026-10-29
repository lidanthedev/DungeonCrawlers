package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.commands.DungeonPhaseFiveCommand;
import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.portal.PortalEncounterService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.secret.SecretDiscoveryService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class BukkitDungeonChestInteractionTest {
    @Test
    void cancelledChestClicksReachHandlersButGhostsAndOffhandDoNot() throws Exception {
        var server = MockBukkit.mock();
        try {
            var plugin = MockBukkit.createMockPlugin();
            var world = server.addSimpleWorld("dungeon_instances");
            var player = server.addPlayer();
            var block = world.getBlockAt(0, 64, 0);
            var point = new Point(0, 64, 0);
            var instance = UUID.randomUUID();
            var runs = mock(RunPreparationService.class);
            when(runs.instanceFor(player.getUniqueId())).thenReturn(Optional.of(instance));
            var lifecycle = mock(PlayerLifecycleService.class);
            var state = mock(PlayerLifecycleService.PlayerSnapshot.class);
            when(lifecycle.player(instance, player.getUniqueId())).thenReturn(Optional.of(state));
            var command = mock(DungeonPhaseFiveCommand.class);
            for (var entry : java.util.Map.of("runs", runs, "lifecycle", lifecycle).entrySet()) {
                var field = DungeonPhaseFiveCommand.class.getDeclaredField(entry.getKey());
                field.setAccessible(true);
                field.set(command, entry.getValue());
            }
            when(command.canOpenDungeonDoor(player.getUniqueId())).thenCallRealMethod();
            var secrets = mock(SecretDiscoveryService.class);
            when(secrets.discover(instance, player.getUniqueId(), point))
                    .thenReturn(SecretDiscoveryService.DiscoveryResult.alreadyFound(null));
            var encounters = mock(PortalEncounterService.class);
            when(encounters.rewardAt(point)).thenReturn(Optional.of(instance));
            @SuppressWarnings("unchecked")
            BiConsumer<Player, UUID> opener = mock(BiConsumer.class);
            server.getPluginManager().registerEvents(new BukkitDungeonRunListener(
                    command, runs, world.getName(), secrets), plugin);
            server.getPluginManager().registerEvents(new BukkitDungeonLifecycleListener(
                    lifecycle, runs, plugin, java.time.Clock.systemUTC()), plugin);
            server.getPluginManager().registerEvents(new BukkitRewardChestListener(
                    encounters, world.getName(), opener, command::canOpenDungeonDoor), plugin);

            for (Material material : new Material[]{Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST}) {
                block.setType(material);
                for (var status : new PlayerLifecycleService.PlayerState[]{PlayerLifecycleService.PlayerState.ALIVE,
                        PlayerLifecycleService.PlayerState.GHOST, PlayerLifecycleService.PlayerState.REMOVED}) {
                    when(state.state()).thenReturn(status);
                    for (boolean cancelled : new boolean[]{false, true}) {
                        clearInvocations(secrets, opener);
                        var event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                                new ItemStack(Material.STICK), block, BlockFace.UP, EquipmentSlot.HAND);
                        event.setCancelled(cancelled);
                        server.getPluginManager().callEvent(event);
                        boolean allowed = status == PlayerLifecycleService.PlayerState.ALIVE;
                        verify(secrets, times(allowed && material != Material.ENDER_CHEST ? 1 : 0))
                                .discover(instance, player.getUniqueId(), point);
                        verify(opener, times(allowed && material == Material.ENDER_CHEST ? 1 : 0))
                                .accept(player, instance);
                        if (allowed || cancelled || material == Material.ENDER_CHEST
                                || status == PlayerLifecycleService.PlayerState.GHOST) assertTrue(event.isCancelled());
                    }
                }
                when(state.state()).thenReturn(PlayerLifecycleService.PlayerState.ALIVE);
                clearInvocations(secrets, opener);
                server.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                        new ItemStack(Material.STICK), block, BlockFace.UP, EquipmentSlot.OFF_HAND));
                server.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK,
                        new ItemStack(Material.STICK), block, BlockFace.UP, EquipmentSlot.HAND));
                when(runs.instanceFor(player.getUniqueId())).thenReturn(Optional.empty());
                var outsider = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                        new ItemStack(Material.STICK), block, BlockFace.UP, EquipmentSlot.HAND);
                outsider.setCancelled(true);
                server.getPluginManager().callEvent(outsider);
                verifyNoInteractions(secrets, opener);
                when(runs.instanceFor(player.getUniqueId())).thenReturn(Optional.of(instance));
            }
        } finally {
            MockBukkit.unmock();
        }
    }
}
