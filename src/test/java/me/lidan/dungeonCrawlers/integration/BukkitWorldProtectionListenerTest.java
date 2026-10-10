package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.core.protection.TeleportPermitService;
import me.lidan.dungeonCrawlers.core.protection.WorldProtectionService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BukkitWorldProtectionListenerTest {
    @Test
    void dungeonDecorationsCannotBeUsedInEitherHandOrByPhysicalActions() {
        UUID participant = UUID.randomUUID();
        var listener = new BukkitWorldProtectionListener(new WorldProtectionService(),
                () -> List.of(region(participant)), new TeleportPermitService(), Clock.systemUTC());
        World world = world("dungeon_instances");
        for (Material material : List.of(Material.DECORATED_POT, Material.FLOWER_POT,
                Material.POTTED_DANDELION, Material.BARREL, Material.LEVER, Material.CHEST)) {
            for (Action action : List.of(Action.RIGHT_CLICK_BLOCK, Action.LEFT_CLICK_BLOCK, Action.PHYSICAL)) {
                for (EquipmentSlot hand : List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND)) {
                    PlayerInteractEvent event = interaction(world, material, action, hand);
                    Event.Result itemUse = event.useItemInHand();
                    listener.onInteract(event);
                    assertEquals(Event.Result.DENY, event.useInteractedBlock(), material + " " + action + " " + hand);
                    assertEquals(itemUse, event.useItemInHand());
                }
            }
        }
    }

    @Test
    void registeredProgressionBlocksReachTheirHandlersButOffhandVanillaUseIsDenied() {
        UUID participant = UUID.randomUUID();
        var region = region(participant);
        Point chest = new Point(0, 64, 0);
        var listener = new BukkitWorldProtectionListener(new WorldProtectionService(), () -> List.of(region),
                new TeleportPermitService(), Clock.systemUTC(),
                (instance, point) -> instance.equals(region.instanceId()) && point.equals(chest));
        PlayerInteractEvent main = interaction(world("dungeon_instances"), Material.TRAPPED_CHEST,
                Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        listener.onInteract(main);
        assertFalse(main.isCancelled());
        PlayerInteractEvent offhand = interaction(world("dungeon_instances"), Material.TRAPPED_CHEST,
                Action.RIGHT_CLICK_BLOCK, EquipmentSlot.OFF_HAND);
        listener.onInteract(offhand);
        assertEquals(Event.Result.DENY, offhand.useInteractedBlock());
        main.setCancelled(true);
        listener.onInteract(main);
        assertEquals(Event.Result.DENY, main.useItemInHand());
        assertEquals(Event.Result.DENY, main.useInteractedBlock());
    }

    @Test
    void normalWorldAndEmptyDungeonWorldInteractionsAreUnaffected() {
        var protectedListener = new BukkitWorldProtectionListener(new WorldProtectionService(),
                () -> List.of(region(UUID.randomUUID())), new TeleportPermitService(), Clock.systemUTC());
        var event = interaction(world("world"), Material.DECORATED_POT, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        protectedListener.onInteract(event);
        assertFalse(event.isCancelled());
        event = interaction(world("dungeon_instances"), Material.DECORATED_POT, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        listener().onInteract(event);
        assertFalse(event.isCancelled());
        var air = new PlayerInteractEvent(mock(Player.class), Action.RIGHT_CLICK_AIR, null, null, BlockFace.UP);
        protectedListener.onInteract(air);
        assertEquals(Event.Result.DEFAULT, air.useItemInHand());
    }

    private static PlayerInteractEvent interaction(World world, Material material, Action action, EquipmentSlot hand) {
        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(block.getType()).thenReturn(material);
        return new PlayerInteractEvent(mock(Player.class), action, null, block, BlockFace.UP, hand);
    }

    @Test
    void crossWorldTeleportIsAllowedEvenWhenItCrossesDungeonBounds() {
        Player player = mock(Player.class);
        World dungeonWorld = world("dungeon_instances");
        World normalWorld = world("world");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        PlayerTeleportEvent event = new PlayerTeleportEvent(player,
                new Location(normalWorld, 0, 64, 0), new Location(dungeonWorld, 0, 64, 0));

        listener().onTeleport(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void crossWorldMoveIsAllowedSoWorldChangeCanHandleDungeonLeave() {
        Player player = mock(Player.class);
        World dungeonWorld = world("dungeon_instances");
        World normalWorld = world("world");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        PlayerMoveEvent event = new PlayerMoveEvent(player,
                new Location(dungeonWorld, 0, 64, 0), new Location(normalWorld, 0, 64, 0));

        listener().onMove(event);

        assertFalse(event.isCancelled());
        assertTrue(event.getTo().getWorld().equals(normalWorld));
    }

    @Test
    void sameWorldTeleportStillCannotLeaveDungeonBounds() {
        UUID participant = UUID.randomUUID();
        Player player = mock(Player.class);
        World dungeonWorld = world("dungeon_instances");
        when(player.getUniqueId()).thenReturn(participant);
        PlayerTeleportEvent event = new PlayerTeleportEvent(player,
                new Location(dungeonWorld, 0, 64, 0), new Location(dungeonWorld, 10, 64, 0));

        BukkitWorldProtectionListener listener = new BukkitWorldProtectionListener(new WorldProtectionService(),
                () -> List.of(region(participant)), new TeleportPermitService(),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        listener.onTeleport(event);

        assertTrue(event.isCancelled());
    }

    private static BukkitWorldProtectionListener listener() {
        return new BukkitWorldProtectionListener(new WorldProtectionService(), List::of,
                new TeleportPermitService(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    private static WorldProtectionService.InstanceRegion region(UUID participant) {
        Point corner = new Point(0, 64, 0);
        return new WorldProtectionService.InstanceRegion("dungeon_instances", UUID.randomUUID(),
                new Bounds(corner, corner.add(new Point(4, 4, 4))), Set.of(participant));
    }

    private static World world(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        return world;
    }
}
