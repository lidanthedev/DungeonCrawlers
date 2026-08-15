package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.persistence.model.PlayerRecoverySnapshot;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BukkitPlayerRecoveryTest {
    @Test
    void captureAndExactRestoreLeaveInventoryAndExperienceUntouched() {
        UUID playerId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getLocation()).thenReturn(new Location(world, 1.25, 64, -2.5, 90, 5));
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getHealth()).thenReturn(18.5);
        when(player.getFoodLevel()).thenReturn(17);
        when(player.getSaturation()).thenReturn(4.5f);
        when(player.getExhaustion()).thenReturn(.25f);
        when(player.getFireTicks()).thenReturn(20);
        when(player.getRemainingAir()).thenReturn(280);
        when(player.getMaximumAir()).thenReturn(400);
        PlayerRecoverySnapshot snapshot = BukkitPlayerRecovery.capture(player, instanceId,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        Server server = mock(Server.class);
        when(server.getWorld("world")).thenReturn(world);
        when(player.teleport(any(Location.class))).thenReturn(true);
        var result = BukkitPlayerRecovery.restore(player, snapshot, server,
                new SpawnProvider() {
                    @Override public Optional<Location> spawn() { return Optional.of(new Location(world, 0, 64, 0)); }
                    @Override public String source() { return "test"; }
                }, 20);

        assertTrue(result.successful());
        assertEquals(BukkitPlayerRecovery.RestoreSource.EXACT, result.source());
        verify(player, never()).setExp(anyFloat());
        verify(player, never()).setLevel(anyInt());
        verify(player).setHealth(18.5);
        verify(player).setFoodLevel(17);
        verify(player).setMaximumAir(400);
    }

    @Test
    void unavailableWorldUsesConfiguredFallbackSpawn() {
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        Server server = mock(Server.class);
        when(server.getWorld("missing")).thenReturn(null);
        World world = mock(World.class);
        when(player.teleport(any(Location.class))).thenReturn(true);

        PlayerRecoverySnapshot snapshot = new PlayerRecoverySnapshot(playerId, UUID.randomUUID(), "missing",
                1, 2, 3, 0, 0, "SURVIVAL", 20, 20, 0, 0, 0, 300, 400, Instant.EPOCH);
        var result = BukkitPlayerRecovery.restore(player, snapshot, server,
                new SpawnProvider() {
                    @Override public Optional<Location> spawn() { return Optional.of(new Location(world, 5, 70, 5)); }
                    @Override public String source() { return "test"; }
                }, 20);

        assertTrue(result.successful());
        assertEquals(BukkitPlayerRecovery.RestoreSource.FALLBACK, result.source());
    }

    @Test
    void captureClampsTransientNegativeAirAndFireTicks() {
        Player player = mock(Player.class);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getLocation()).thenReturn(new Location(world, 1, 64, 1));
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getHealth()).thenReturn(20D);
        when(player.getFoodLevel()).thenReturn(20);
        when(player.getSaturation()).thenReturn(0F);
        when(player.getExhaustion()).thenReturn(0F);
        when(player.getFireTicks()).thenReturn(-20);
        when(player.getRemainingAir()).thenReturn(-10);
        when(player.getMaximumAir()).thenReturn(300);

        PlayerRecoverySnapshot snapshot = BukkitPlayerRecovery.capture(player, UUID.randomUUID(), Clock.systemUTC());

        assertEquals(0, snapshot.fireTicks());
        assertEquals(0, snapshot.remainingAir());
        assertEquals(300, snapshot.maximumAir());
    }
}
