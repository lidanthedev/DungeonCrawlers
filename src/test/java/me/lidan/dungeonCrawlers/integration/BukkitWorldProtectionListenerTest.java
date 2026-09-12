package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.core.protection.TeleportPermitService;
import me.lidan.dungeonCrawlers.core.protection.WorldProtectionService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import org.bukkit.Location;
import org.bukkit.World;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BukkitWorldProtectionListenerTest {
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
