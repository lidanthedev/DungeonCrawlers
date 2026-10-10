package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.core.encounter.EncounterFactory.EncounterContext;
import me.lidan.dungeonCrawlers.core.encounter.RingmasterEncounter;
import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.Mockito.*;

class BukkitRingmasterArenaTest {
    @Test
    void carouselKeepsBossAiAndTargetingActiveAndEndsAfterTenSeconds() throws Exception {
        UUID instance = UUID.randomUUID(), bossId = UUID.randomUUID(), playerId = UUID.randomUUID();
        EncounterContext context = mock(EncounterContext.class);
        when(context.instanceId()).thenReturn(instance);
        RunPreparationService runs = mock(RunPreparationService.class);
        RunPreparationService.RunSnapshot run = mock(RunPreparationService.RunSnapshot.class);
        when(run.participants()).thenReturn(List.of(playerId));
        when(runs.info(instance)).thenReturn(Optional.of(run));
        when(runs.instanceFor(playerId)).thenReturn(Optional.of(instance));
        PlayerLifecycleService lifecycle = mock(PlayerLifecycleService.class);
        PlayerLifecycleService.PlayerSnapshot state = mock(PlayerLifecycleService.PlayerSnapshot.class);
        when(state.online()).thenReturn(true);
        when(state.state()).thenReturn(PlayerLifecycleService.PlayerState.ALIVE);
        when(lifecycle.player(instance, playerId)).thenReturn(Optional.of(state));
        Plugin plugin = mock(Plugin.class);
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        when(plugin.getServer()).thenReturn(server);
        org.bukkit.World world = mock(org.bukkit.World.class);
        org.bukkit.Location center = new org.bukkit.Location(world, 0, 64, 0);
        Player player = mock(Player.class);
        when(server.getPlayer(playerId)).thenReturn(player);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenAnswer(ignored -> center.clone().add(10, 0, 0));
        Mob boss = mock(Mob.class);
        when(server.getEntity(bossId)).thenReturn(boss);
        when(boss.getLocation()).thenAnswer(ignored -> center.clone().add(20, 0, 0));
        BukkitRingmasterVisuals visuals = mock(BukkitRingmasterVisuals.class);
        BukkitRingmasterVisuals.Rig rig = mock(BukkitRingmasterVisuals.Rig.class);
        when(visuals.rig(any())).thenReturn(rig);
        when(visuals.horse(any())).thenReturn(rig);
        BukkitRingmasterArena arena = new BukkitRingmasterArena(context, plugin, runs, lifecycle,
                mock(MythicMobGateway.class), mock(BukkitDifficultyService.class));
        setField(arena, "boss", bossId);
        setField(arena, "center", center);
        setField(arena, "visuals", visuals);
        setField(arena, "casts", 1);
        Instant started = Instant.parse("2026-10-10T00:00:00Z");

        arena.cast(RingmasterEncounter.Attack.CAROUSEL, started);
        verify(boss).setAI(true);
        assertTrue(arena.busy());
        arena.tick(started.plusSeconds(1));
        arena.tick(started.plusSeconds(3));
        verify(boss, times(2)).setTarget(player);
        verify(boss, never()).setAI(false);
        verify(boss, never()).setInvulnerable(anyBoolean());
        arena.tick(started.plusMillis(9999));
        assertTrue(arena.busy());
        arena.tick(started.plusSeconds(10));
        assertFalse(arena.busy());
        setField(arena, "casts", 3);
        arena.cast(RingmasterEncounter.Attack.CARDS, started.plusSeconds(14));
        verify(boss).setAI(false);
    }

    private static void setField(BukkitRingmasterArena arena, String name, Object value) throws Exception {
        var field = BukkitRingmasterArena.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(arena, value);
    }

    @Test
    void arrowsOnlyDamageBossForLivingParticipantsInThisInstance() throws Exception {
        UUID instance = UUID.randomUUID(), bossId = UUID.randomUUID(), playerId = UUID.randomUUID();
        EncounterContext context = mock(EncounterContext.class);
        when(context.instanceId()).thenReturn(instance);
        RunPreparationService runs = mock(RunPreparationService.class);
        RunPreparationService.RunSnapshot run = mock(RunPreparationService.RunSnapshot.class);
        when(run.participants()).thenReturn(List.of(playerId));
        when(runs.info(instance)).thenReturn(Optional.of(run));
        PlayerLifecycleService lifecycle = mock(PlayerLifecycleService.class);
        BukkitRingmasterArena arena = new BukkitRingmasterArena(context, mock(Plugin.class), runs, lifecycle,
                mock(MythicMobGateway.class), mock(BukkitDifficultyService.class));
        var bossField = BukkitRingmasterArena.class.getDeclaredField("boss");
        bossField.setAccessible(true);
        bossField.set(arena, bossId);
        Entity boss = mock(Entity.class);
        when(boss.getUniqueId()).thenReturn(bossId);
        Player shooter = mock(Player.class);
        when(shooter.getUniqueId()).thenReturn(playerId);
        when(shooter.isOnline()).thenReturn(true);
        Projectile arrow = mock(Projectile.class);
        when(arrow.getShooter()).thenReturn(shooter);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(arrow);
        when(event.getEntity()).thenReturn(boss);

        when(runs.instanceFor(playerId)).thenReturn(Optional.of(UUID.randomUUID()));
        arena.damage(event);
        verify(event).setCancelled(true);
        clearInvocations(event);

        when(runs.instanceFor(playerId)).thenReturn(Optional.of(instance));
        PlayerLifecycleService.PlayerSnapshot state = mock(PlayerLifecycleService.PlayerSnapshot.class);
        when(state.online()).thenReturn(true);
        when(state.state()).thenReturn(PlayerLifecycleService.PlayerState.GHOST);
        when(lifecycle.player(instance, playerId)).thenReturn(Optional.of(state));
        arena.damage(event);
        verify(event).setCancelled(true);
        clearInvocations(event);

        when(state.state()).thenReturn(PlayerLifecycleService.PlayerState.ALIVE);
        arena.damage(event);
        verify(event, never()).setCancelled(anyBoolean());
    }
}
