package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.core.encounter.ChainboundEncounter;
import me.lidan.dungeonCrawlers.core.encounter.ChainboundEncounter.Attack;
import me.lidan.dungeonCrawlers.core.encounter.ChainboundEncounter.Stage;
import me.lidan.dungeonCrawlers.core.encounter.EncounterFactory.EncounterContext;
import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.protection.TeleportPermitService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.event.entity.EntityDamageEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.Mockito.*;

class BukkitChainboundArenaTest {
    private static final Instant START = Instant.parse("2026-10-10T00:00:00Z");
    private final Player player = mock(Player.class);
    private final Mob boss = mock(Mob.class);
    private final BukkitFoundryScene scene = mock(BukkitFoundryScene.class);
    private final World world = mock(World.class);
    private Location position = new Location(world, 10, 66, 0);

    private BukkitChainboundArena arena(Stage stage) throws Exception {
        UUID instance = UUID.randomUUID(), bossId = UUID.randomUUID(), playerId = UUID.randomUUID();
        EncounterContext context = mock(EncounterContext.class);
        when(context.instanceId()).thenReturn(instance); when(context.diagnostics()).thenReturn(ignored -> { });
        Plugin plugin = mock(Plugin.class); Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getEntity(bossId)).thenReturn(boss); when(boss.isValid()).thenReturn(true);
        when(boss.getLocation()).thenAnswer(ignored -> new Location(world, 0, 66, 0));
        when(server.getPlayer(playerId)).thenReturn(player); when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true); when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenAnswer(ignored -> position.clone());
        when(player.getX()).thenAnswer(ignored -> position.getX());
        when(player.getZ()).thenAnswer(ignored -> position.getZ());
        when(player.getY()).thenAnswer(ignored -> position.getY());
        RunPreparationService runs = mock(RunPreparationService.class);
        var run = mock(RunPreparationService.RunSnapshot.class);
        when(run.participants()).thenReturn(List.of(playerId));
        when(runs.info(instance)).thenReturn(Optional.of(run)); when(runs.instanceFor(playerId)).thenReturn(Optional.of(instance));
        PlayerLifecycleService lifecycle = mock(PlayerLifecycleService.class);
        var state = mock(PlayerLifecycleService.PlayerSnapshot.class);
        when(state.online()).thenReturn(true); when(state.state()).thenReturn(PlayerLifecycleService.PlayerState.ALIVE);
        when(lifecycle.player(instance, playerId)).thenReturn(Optional.of(state));
        var arena = new BukkitChainboundArena(context, plugin, mock(GenerationService.class), runs, lifecycle,
                mock(TeleportPermitService.class), Clock.fixed(START, java.time.ZoneOffset.UTC));
        set(arena, "bossId", bossId); set(arena, "center", new Location(world, 0, 76, 0));
        set(arena, "lowered", true); set(arena, "stage", stage); set(arena, "scene", scene);
        set(arena, "settings", ChainboundEncounter.Settings.defaults());
        return arena;
    }
    private static void set(BukkitChainboundArena arena, String name, Object value) throws Exception {
        var field = BukkitChainboundArena.class.getDeclaredField(name); field.setAccessible(true); field.set(arena, value);
    }
    private static Object get(BukkitChainboundArena arena, String name) throws Exception {
        var field = BukkitChainboundArena.class.getDeclaredField(name); field.setAccessible(true); return field.get(arena);
    }
    private void sample(BukkitChainboundArena arena, long elapsed) throws Exception {
        var method = BukkitChainboundArena.class.getDeclaredMethod("tickAttack", long.class, List.class);
        method.setAccessible(true); method.invoke(arena, START.toEpochMilli() + elapsed, List.of(player));
    }
    @Test void escapingYourChainCagePreventsItsLockAndDamage() throws Exception {
        var arena = arena(Stage.RIVEN); arena.cast(Attack.CHAIN_CAGE, START);
        sample(arena, 1500); verify(player, never()).damage(anyDouble(), any(Entity.class));
        position.add(8, 0, 0); sample(arena, 2000); sample(arena, 4200);
        verify(player, never()).damage(anyDouble(), any(Entity.class));
        verify(scene, atLeastOnce()).hideCombatChains();
    }
    @Test void unbrokenCageDealsOneTelegraphedHitAndStopsPulling() throws Exception {
        var arena = arena(Stage.RIVEN); arena.cast(Attack.CHAIN_CAGE, START);
        sample(arena, 1999); verify(player, never()).damage(anyDouble(), any(Entity.class));
        sample(arena, 4200); sample(arena, 5000);
        verify(player, times(1)).damage(5_250_000D, boss);
    }
    @Test void clockworkChainsCanBeJumpedAndNeverHitDuringTheirWarning() throws Exception {
        var arena = arena(Stage.RIVEN); arena.cast(Attack.CLOCKWORK_REQUIEM, START);
        sample(arena, 1999); verify(player, never()).damage(anyDouble(), any(Entity.class));
        position.setY(67); sample(arena, 2000); verify(player, never()).damage(anyDouble(), any(Entity.class));
        position.setY(66); sample(arena, 2000); verify(player).damage(doubleThat(value -> Math.abs(value - 3_850_000D) < .001), eq(boss));
    }
    @Test void finalLanceLocksThreeTimesWithAFullSecondBetweenStrikes() throws Exception {
        var arena = arena(Stage.FINAL); arena.cast(Attack.LANCE, START);
        sample(arena, 1999); verify(player, never()).damage(anyDouble(), any(Entity.class));
        sample(arena, 2000); sample(arena, 2999); verify(player, times(1)).damage(anyDouble(), any(Entity.class));
        sample(arena, 3000); sample(arena, 3999); verify(player, times(2)).damage(anyDouble(), any(Entity.class));
        sample(arena, 4000); sample(arena, 5000); verify(player, times(3)).damage(anyDouble(), any(Entity.class));
    }
    @Test void leavingTheInstanceMakesCageDamageIneligible() throws Exception {
        var arena = arena(Stage.RIVEN); arena.cast(Attack.CHAIN_CAGE, START);
        when(player.isOnline()).thenReturn(false); sample(arena, 4200);
        verify(player, never()).damage(anyDouble(), any(Entity.class));
    }
    @Test void lethalFirstFormDamageIsAllowedButCinematicDamageIsCancelled() throws Exception {
        var arena = arena(Stage.FIRST);
        when(boss.getUniqueId()).thenReturn((UUID) get(arena, "bossId"));
        var hit = mock(EntityDamageEvent.class);
        when(hit.getEntity()).thenReturn(boss); when(hit.getDamage()).thenReturn(2_000_000_000D);
        arena.damage(hit);
        verify(hit, never()).setCancelled(true); verify(hit, never()).setDamage(anyDouble());
        set(arena, "stage", Stage.TRANSFORM);
        arena.damage(hit); verify(hit).setCancelled(true);
    }
    @Test void secondActorReusesTheSceneAndListenerWithoutCompoundingPartyScaling() throws Exception {
        var arena = arena(Stage.FIRST);
        Plugin plugin = (Plugin) get(arena, "plugin");
        var manager = mock(org.bukkit.plugin.PluginManager.class);
        when(plugin.getServer().getPluginManager()).thenReturn(manager);
        var run = ((RunPreparationService) get(arena, "runs"))
                .info(((EncounterContext) get(arena, "context")).instanceId()).orElseThrow();
        when(run.participants()).thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));
        UUID first = (UUID) get(arena, "bossId"), second = UUID.randomUUID();
        Mob replacement = mock(Mob.class);
        when(plugin.getServer().getEntity(second)).thenReturn(replacement); when(replacement.isValid()).thenReturn(true);
        ActiveMob firstActive = mock(ActiveMob.class, RETURNS_DEEP_STUBS);
        ActiveMob secondActive = mock(ActiveMob.class, RETURNS_DEEP_STUBS);
        when(firstActive.getEntity().getMaxHealth()).thenReturn(1_000_000_000D);
        when(secondActive.getEntity().getMaxHealth()).thenReturn(1_000_000_000D);
        MythicBukkit mythic = mock(MythicBukkit.class, RETURNS_DEEP_STUBS);
        when(mythic.getMobManager().getActiveMob(first)).thenReturn(Optional.of(firstActive));
        when(mythic.getMobManager().getActiveMob(second)).thenReturn(Optional.of(secondActive));
        try (var ignored = mockStatic(MythicBukkit.class)) {
            ignored.when(MythicBukkit::inst).thenReturn(mythic);
            arena.begin(first, ChainboundEncounter.Settings.defaults());
            arena.begin(second, ChainboundEncounter.Settings.defaults());
        }
        verify(firstActive.getEntity()).setHealthAndMax(1_450_000_000D);
        verify(secondActive.getEntity()).setHealthAndMax(1_450_000_000D);
        verify(manager, times(1)).registerEvents(arena, plugin);
        org.junit.jupiter.api.Assertions.assertSame(scene, get(arena, "scene"));
        org.junit.jupiter.api.Assertions.assertEquals(second, get(arena, "bossId"));
        verify(replacement).setInvulnerable(true); verify(replacement).setAI(false);
    }
}
