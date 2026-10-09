package me.lidan.dungeonCrawlers.integration;

import me.lidan.cavecrawlers.damage.DamageCalculationEvent;
import me.lidan.cavecrawlers.skills.SkillsManager;
import me.lidan.cavecrawlers.stats.StatType;
import me.lidan.cavecrawlers.stats.Stats;
import me.lidan.cavecrawlers.stats.StatsCalculateEvent;
import me.lidan.dungeonCrawlers.integration.addon.RunicPetAdapter;
import me.lidan.dungeonCrawlers.core.difficulty.Difficulty;
import me.lidan.dungeonCrawlers.core.claim.RewardClaimService;
import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BukkitDifficultyServiceTest {
    private BukkitDifficultyService service;
    private PlayerMock player, teammate;
    private PlayerLifecycleService lifecycle;
    private RunPreparationService runs;
    private GenerationService.LayoutContext context;
    private RewardClaimService claims;
    private final UUID instance = UUID.randomUUID();
    private final Mob enemy = mock(Mob.class);

    @BeforeEach void setup() throws Exception {
        var server = MockBukkit.mock();
        var world = server.addSimpleWorld("dungeon");
        player = server.addPlayer(); teammate = server.addPlayer();
        player.teleport(new Location(world, 0, 64, 0));
        teammate.teleport(new Location(world, 12, 64, 0));
        var generation = mock(GenerationService.class);
        context = mock(GenerationService.LayoutContext.class);
        when(context.progressionEnabled()).thenReturn(true);
        when(context.difficulty()).thenReturn(Difficulty.HELLISH.defaults());
        when(generation.layoutContext(instance)).thenReturn(Optional.of(context));
        runs = mock(RunPreparationService.class);
        when(runs.instanceFor(player.getUniqueId())).thenReturn(Optional.of(instance));
        var run = mock(RunPreparationService.RunSnapshot.class);
        when(run.startedAt()).thenReturn(java.time.Instant.EPOCH);
        when(run.participants()).thenReturn(List.of(player.getUniqueId(), teammate.getUniqueId()));
        when(run.selectedClasses()).thenReturn(Map.of(player.getUniqueId(), "mage", teammate.getUniqueId(), "mage"));
        when(runs.info(instance)).thenReturn(Optional.of(run));
        lifecycle = mock(PlayerLifecycleService.class);
        var alivePlayer = state(player);
        var aliveTeammate = state(teammate);
        when(lifecycle.player(instance, player.getUniqueId())).thenReturn(Optional.of(alivePlayer));
        var party = mock(PlayerLifecycleService.InstanceSnapshot.class);
        when(party.players()).thenReturn(List.of(alivePlayer, aliveTeammate));
        when(lifecycle.info(instance)).thenReturn(Optional.of(party));
        claims = mock(RewardClaimService.class);
        service = new BukkitDifficultyService(MockBukkit.createMockPlugin(), generation, runs, lifecycle, claims);
        when(enemy.getUniqueId()).thenReturn(UUID.randomUUID());
        when(enemy.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(enemy.getNearbyEntities(8, 8, 8)).thenReturn(List.of());
        track(enemy, false, false, instance);
    }
    @AfterEach void close() { if (service != null) service.close(); MockBukkit.unmock(); }

    @Test void nearbyProtectionExcludesBossesOtherInstancesAndTheEightBlockBoundary() throws Exception {
        var other = mock(Mob.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        when(other.isValid()).thenReturn(true);
        when(other.getLocation()).thenReturn(player.getLocation().add(8, 0, 0));
        when(enemy.getNearbyEntities(8, 8, 8)).thenReturn(List.of(other));
        track(other, false, false, instance);
        assertEquals(60, outgoingDamage(), 1E-9); // Duplicate class .75, single group protection .8.
        when(other.getLocation()).thenReturn(player.getLocation().add(8.01, 0, 0));
        assertEquals(75, outgoingDamage());
        when(other.getLocation()).thenReturn(player.getLocation().add(8, 0, 0));
        track(other, false, true, instance);
        assertEquals(75, outgoingDamage());
        track(other, false, false, UUID.randomUUID());
        assertEquals(75, outgoingDamage());
        track(other, false, false, instance);
        track(enemy, false, true, instance);
        assertEquals(75, outgoingDamage());
    }
    @Test void attributedAttacksUseAliveOnlineTeammatesAtTwelveBlocksAndEnvironmentIsUnchanged() throws Exception {
        var source = mock(DamageSource.class);
        when(source.getCausingEntity()).thenReturn(enemy);
        var event = damage(source);
        service.incoming(event);
        verify(event).setDamage(120);
        teammate.teleport(teammate.getLocation().add(.01, 0, 0));
        event = damage(source);
        service.incoming(event);
        verify(event).setDamage(150);
        track(enemy, true, false, instance);
        event = damage(source);
        service.incoming(event);
        verify(event).setDamage(750);
        event = damage(mock(DamageSource.class));
        service.incoming(event);
        verify(event, never()).setDamage(anyDouble());
    }
    @Test void ghostsCannotDealDamage() {
        var ghost = state(player);
        when(ghost.state()).thenReturn(PlayerLifecycleService.PlayerState.GHOST);
        when(lifecycle.player(instance, player.getUniqueId())).thenReturn(Optional.of(ghost));
        var event = new DamageCalculationEvent(player, enemy, null, 100, false);
        service.outgoing(event);
        assertTrue(event.isCancelled());
    }
    @Test void runicPowerScalesDungeonHealthWithoutDuplicatingNativeStatsOrApplyingOutside() throws Exception {
        var pet = mock(RunicPetAdapter.class);
        var field = BukkitDifficultyService.class.getDeclaredField("pet");
        field.setAccessible(true);
        field.set(service, pet);
        try (var plugins = mockStatic(org.bukkit.plugin.java.JavaPlugin.class)) {
            plugins.when(() -> org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(
                    me.lidan.cavecrawlers.objects.ConfigLoader.class)).thenReturn(mock(org.bukkit.plugin.java.JavaPlugin.class));
            Class.forName("me.lidan.cavecrawlers.objects.ConfigLoader");
        }
        try (var skills = mockStatic(SkillsManager.class)) {
            skills.when(SkillsManager::getInstance).thenReturn(mock(SkillsManager.class));
            for (int level : new int[]{0, 1, 50, 100}) {
                when(pet.activeLevel(player.getUniqueId())).thenReturn(level);
                // The native pet listener has already added its global stats.
                var stats = new Stats();
                stats.set(StatType.HEALTH, 1100);
                stats.set(StatType.DEFENSE, 550);
                stats.set(StatType.MAGIC_FIND, 110);
                service.stats(new StatsCalculateEvent(player, stats));
                assertEquals(1100 * (1 + level / 200D), stats.get(StatType.HEALTH).getValue(), 1E-9);
                assertEquals(550, stats.get(StatType.DEFENSE).getValue());
                assertEquals(137.5, stats.get(StatType.MAGIC_FIND).getValue());
            }
            when(runs.instanceFor(player.getUniqueId())).thenReturn(Optional.empty());
            var outside = new Stats();
            outside.set(StatType.HEALTH, 1100);
            outside.set(StatType.MAGIC_FIND, 110);
            service.stats(new StatsCalculateEvent(player, outside));
            assertEquals(1100, outside.get(StatType.HEALTH).getValue());
            assertEquals(110, outside.get(StatType.MAGIC_FIND).getValue());
        }
    }
    @Test void runicBossGivesExactlyFourOnceToKillerAndDebugRunGivesNone() throws Exception {
        when(enemy.getKiller()).thenReturn(player);
        var event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(enemy);
        track(enemy, true, true, instance);
        service.death(event);
        assertTrue(service.runicBoss(instance), "completion must still see Runic status after the enemy is removed");
        assertFalse(service.runicBoss(UUID.randomUUID()));
        service.death(event);
        verify(claims, times(1)).grantLoot(enemy.getUniqueId(), player, "RUNIC_FRAGMENT", 4);
        clearInvocations(claims);
        when(context.progressionEnabled()).thenReturn(false);
        track(enemy, true, true, instance);
        service.death(event);
        verifyNoInteractions(claims);
    }
    private double outgoingDamage() {
        var event = new DamageCalculationEvent(player, enemy, null, 100, false);
        service.outgoing(event);
        return event.getDamage();
    }
    private EntityDamageEvent damage(DamageSource source) {
        var event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getDamageSource()).thenReturn(source);
        when(event.getDamage()).thenReturn(100D);
        return event;
    }
    private static PlayerLifecycleService.PlayerSnapshot state(PlayerMock player) {
        var state = mock(PlayerLifecycleService.PlayerSnapshot.class);
        when(state.playerId()).thenReturn(player.getUniqueId());
        when(state.online()).thenReturn(true);
        when(state.state()).thenReturn(PlayerLifecycleService.PlayerState.ALIVE);
        return state;
    }
    private void track(Mob mob, boolean runic, boolean boss, UUID owner) throws Exception {
        var type = Class.forName(BukkitDifficultyService.class.getName() + "$Enemy");
        var constructor = type.getDeclaredConstructor(UUID.class, boolean.class, boolean.class);
        constructor.setAccessible(true);
        var field = BukkitDifficultyService.class.getDeclaredField("enemies");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") var enemies = (Map<UUID, Object>) field.get(service);
        enemies.put(mob.getUniqueId(), constructor.newInstance(owner, runic, boss));
    }
}
