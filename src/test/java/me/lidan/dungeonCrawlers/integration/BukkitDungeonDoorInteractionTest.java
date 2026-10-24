package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.commands.DungeonPhaseFiveCommand;
import me.lidan.dungeonCrawlers.core.combat.CombatRoomService;
import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.portal.PortalEncounterService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class BukkitDungeonDoorInteractionTest {
    @Test void bothDoorKindsAcceptCancelledLeftAndRightClicksButRejectGhostsAndOffHand() throws Exception {
        var server = MockBukkit.mock();
        try {
            var plugin = MockBukkit.createMockPlugin();
            var world = server.addSimpleWorld("dungeon_instances");
            var player = server.addPlayer();
            var block = world.getBlockAt(0, 64, 0);
            block.setType(Material.IRON_BLOCK);
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
            var combat = mock(CombatRoomService.class);
            var activated = mock(CombatRoomService.ActivationResult.class);
            when(activated.successful()).thenReturn(true);
            when(activated.openedDoorBlocks()).thenReturn(Set.of());
            when(activated.detail()).thenReturn("room already active");
            when(combat.activateAt(point)).thenReturn(activated);
            var region = new me.lidan.dungeonCrawlers.core.protection.WorldProtectionService.InstanceRegion(
                    world.getName(), instance, new me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds(
                    point, point.add(new Point(4, 4, 4))), Set.of(player.getUniqueId()));
            server.getPluginManager().registerEvents(new BukkitWorldProtectionListener(
                    new me.lidan.dungeonCrawlers.core.protection.WorldProtectionService(), () -> java.util.List.of(region),
                    new me.lidan.dungeonCrawlers.core.protection.TeleportPermitService(), java.time.Clock.systemUTC(),
                    (id, at) -> runs.doorAt(at).isPresent() || combat.isDoorAt(at)), plugin);
            server.getPluginManager().registerEvents(new BukkitDungeonRunListener(command, runs, world.getName()), plugin);
            server.getPluginManager().registerEvents(new BukkitCombatListener(combat, mock(BukkitEntityIdentity.class),
                    world.getName(), () -> false, mock(BukkitBossIdentity.class), mock(PortalEncounterService.class),
                    command::canOpenDungeonDoor), plugin);

            for (boolean preparation : new boolean[]{true, false}) {
                when(runs.doorAt(point)).thenReturn(preparation
                        ? Optional.of(mock(RunPreparationService.DoorBlockLookup.class)) : Optional.empty());
                when(combat.isDoorAt(point)).thenReturn(!preparation);
                for (var status : new PlayerLifecycleService.PlayerState[]{PlayerLifecycleService.PlayerState.ALIVE,
                        PlayerLifecycleService.PlayerState.GHOST}) {
                    when(state.state()).thenReturn(status);
                    for (Action action : new Action[]{Action.LEFT_CLICK_BLOCK, Action.RIGHT_CLICK_BLOCK}) {
                        for (boolean cancelled : new boolean[]{false, true}) {
                            clearInvocations(command, combat);
                            var event = new PlayerInteractEvent(player, action, new ItemStack(Material.STICK),
                                    block, BlockFace.UP, EquipmentSlot.HAND);
                            event.setCancelled(cancelled);
                            server.getPluginManager().callEvent(event);
                            assertTrue(event.isCancelled());
                            boolean allowed = status == PlayerLifecycleService.PlayerState.ALIVE;
                            verify(command, times(preparation && allowed ? 1 : 0)).openDoorAt(player, point);
                            verify(combat, times(!preparation && allowed ? 1 : 0)).activateAt(point);
                        }
                    }
                }
                when(state.state()).thenReturn(PlayerLifecycleService.PlayerState.ALIVE);
                clearInvocations(command, combat);
                server.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                        new ItemStack(Material.STICK), block, BlockFace.UP, EquipmentSlot.OFF_HAND));
                verify(command, never()).openDoorAt(any(), any());
                verify(combat, never()).activateAt(any());
            }
        } finally { MockBukkit.unmock(); }
    }
}
