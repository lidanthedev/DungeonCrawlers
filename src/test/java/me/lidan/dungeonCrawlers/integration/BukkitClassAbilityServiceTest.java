package me.lidan.dungeonCrawlers.integration;

import me.lidan.cavecrawlers.CaveCrawlers;
import me.lidan.cavecrawlers.damage.DamageCalculationEvent;
import me.lidan.cavecrawlers.damage.DamageManager;
import me.lidan.cavecrawlers.damage.FinalDamageCalculation;
import me.lidan.cavecrawlers.items.ItemsManager;
import me.lidan.cavecrawlers.stats.ActionBarManager;
import me.lidan.cavecrawlers.stats.StatType;
import me.lidan.cavecrawlers.stats.Stats;
import me.lidan.cavecrawlers.stats.StatsCalculateEvent;
import me.lidan.cavecrawlers.stats.StatsManager;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ConfigSnapshot;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ClassDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigRegistryService;
import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner.LayoutPlan;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner.Placement;
import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BukkitClassAbilityServiceTest {
    @BeforeAll
    static void initializeNativeStatsWithPluginFixture() throws Exception {
        try (var nativePlugin = mockStatic(org.bukkit.plugin.java.JavaPlugin.class)) {
            nativePlugin.when(() -> org.bukkit.plugin.java.JavaPlugin.getPlugin(me.lidan.cavecrawlers.CaveCrawlers.class))
                    .thenReturn(null);
            Class.forName(StatsManager.class.getName());
            Class.forName(me.lidan.cavecrawlers.stats.ActionBarManager.class.getName());
        }
    }

    private final UUID instance = UUID.randomUUID();
    private final RunPreparationService runs = mock(RunPreparationService.class);
    private final PlayerLifecycleService lifecycle = mock(PlayerLifecycleService.class);
    private final GenerationService generation = mock(GenerationService.class);
    private final ConfigRegistryService config = mock(ConfigRegistryService.class);
    private final BukkitEntityIdentity identity = mock(BukkitEntityIdentity.class);
    private final BukkitBossIdentity bosses = mock(BukkitBossIdentity.class);
    private final BukkitDifficultyService difficulty = mock(BukkitDifficultyService.class);
    private final Plugin plugin = mock(Plugin.class);
    private final Server server = mock(Server.class);
    private final World world = mock(World.class);
    private final MutableClock clock = new MutableClock();
    private final ArrayDeque<Runnable> scheduled = new ArrayDeque<>();
    private final List<ItemDisplay> displays = new ArrayList<>();
    private final StatsManager statsManager = mock(StatsManager.class);
    private final DamageManager damageManager = mock(DamageManager.class);
    private final ItemsManager itemsManager = mock(ItemsManager.class);
    private final ActionBarManager actionBar = mock(ActionBarManager.class);
    private final CaveCrawlers caveCrawlers = mock(CaveCrawlers.class);
    private final Arrow damageArrow = mock(Arrow.class);
    private MockedStatic<StatsManager> nativeStats;
    private MockedStatic<DungeonClassScaling> levels;
    private MockedStatic<DamageManager> nativeDamage;
    private MockedStatic<ItemsManager> nativeItems;
    private MockedStatic<ActionBarManager> nativeBars;
    private MockedStatic<CaveCrawlers> nativeCave;
    private BukkitClassAbilityService service;
    private Player source;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        nativeStats = mockStatic(StatsManager.class);
        nativeStats.when(StatsManager::getInstance).thenReturn(statsManager);
        nativeStats.when(() -> StatsManager.getMaxHealth(any())).thenReturn(1000D);
        when(statsManager.getStats(any(Player.class))).thenAnswer(ignored -> {
            var stats = new Stats();
            stats.set(StatType.INTELLIGENCE, 100);
            stats.set(StatType.STRENGTH, 100);
            return stats;
        });
        levels = mockStatic(DungeonClassScaling.class);
        levels.when(() -> DungeonClassScaling.dungeonLevel(any())).thenReturn(20);
        nativeDamage = mockStatic(DamageManager.class);
        nativeDamage.when(DamageManager::getInstance).thenReturn(damageManager);
        nativeItems = mockStatic(ItemsManager.class);
        nativeItems.when(ItemsManager::getInstance).thenReturn(itemsManager);
        when(itemsManager.getItemFromItemStack(any())).thenReturn(null);
        nativeBars = mockStatic(ActionBarManager.class);
        nativeBars.when(ActionBarManager::getInstance).thenReturn(actionBar);
        nativeCave = mockStatic(CaveCrawlers.class);
        nativeCave.when(CaveCrawlers::getInstance).thenReturn(caveCrawlers);
        when(caveCrawlers.getConfig()).thenReturn(mock(FileConfiguration.class));
        when(damageManager.launchProjectile(any(Player.class), eq(Arrow.class), any(FinalDamageCalculation.class)))
                .thenReturn(damageArrow);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getName()).thenReturn("DungeonCrawlers");
        when(plugin.namespace()).thenReturn("dungeoncrawlers");
        var scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            scheduled.add(call.getArgument(1));
            return null;
        });
        when(world.getName()).thenReturn("dungeon_instances");
        when(world.spawn(any(Location.class), eq(ItemDisplay.class), any(Consumer.class))).thenAnswer(call -> {
            var display = mock(ItemDisplay.class);
            when(display.isValid()).thenReturn(true);
            displays.add(display);
            Consumer<ItemDisplay> initialize = call.getArgument(2);
            initialize.accept(display);
            return display;
        });
        var snapshot = mock(ConfigSnapshot.class);
        when(config.snapshot()).thenReturn(snapshot);
        when(snapshot.classes()).thenReturn(Map.of());
        source = player(0, 64, 0, instance, PlayerLifecycleService.PlayerState.ALIVE);
        select("berserker", RunPreparationService.RunState.RUNNING, List.of(source));
        room();
        service = new BukkitClassAbilityService(plugin, runs, lifecycle, generation, config, identity,
                bosses, difficulty, clock, "dungeon_instances");
    }

    @AfterEach
    void tearDown() {
        if (service != null) service.close();
        nativeCave.close();
        nativeBars.close();
        nativeItems.close();
        nativeDamage.close();
        levels.close();
        nativeStats.close();
        MockBukkit.unmock();
    }

    @Test
    void dropCancelsEquipmentDropOnCooldownAndForGhostsButLeavesOutsidePlayersAlone() {
        var event = mock(PlayerDropItemEvent.class);
        when(event.getPlayer()).thenReturn(source);
        service.onDrop(event);
        verify(statsManager, never()).calculateStats(source);
        verify(itemsManager, never()).getItemFromItemStack(any());
        assertEquals(0, service.classAbility("berserker").getAbilityCooldown().getCooldown(source.getUniqueId()));
        ItemStack restoredWeapon = new ItemStack(Material.DIAMOND_SWORD);
        when(source.getInventory().getItemInMainHand()).thenReturn(restoredWeapon);
        runScheduled();
        verify(itemsManager).getItemFromItemStack(same(restoredWeapon));
        service.onDrop(event);
        runScheduled();
        verify(event, times(2)).setCancelled(true);
        verify(statsManager, times(1)).calculateStats(source);
        verify(actionBar).showActionBar(eq(source),
                argThat((String message) -> message.contains("Still on cooldown")));

        state(source, PlayerLifecycleService.PlayerState.GHOST);
        service.onDrop(event);
        verify(event, times(3)).setCancelled(true);
        verify(statsManager, times(1)).calculateStats(source);
        Player outside = mock(Player.class);
        when(outside.getUniqueId()).thenReturn(UUID.randomUUID());
        when(event.getPlayer()).thenReturn(outside);
        service.onDrop(event);
        verify(event, times(3)).setCancelled(true);
        assertTrue(scheduled.isEmpty());
    }

    @Test
    void queuedDropDoesNotCastAfterPlayerLeavesTheDungeon() {
        var event = mock(PlayerDropItemEvent.class);
        when(event.getPlayer()).thenReturn(source);
        service.onDrop(event);
        verify(event).setCancelled(true);
        when(runs.instanceFor(source.getUniqueId())).thenReturn(Optional.empty());
        runScheduled();
        verify(itemsManager, never()).getItemFromItemStack(any());
        verify(statsManager, never()).calculateStats(any());
    }

    @Test
    void rejectsGhostsFinishedRunsAndPlayersOutsideDungeonWorld() {
        state(source, PlayerLifecycleService.PlayerState.GHOST);
        service.activateClass(dropEvent(source));
        state(source, PlayerLifecycleService.PlayerState.ALIVE);
        select("berserker", RunPreparationService.RunState.COMPLETED, List.of(source));
        assertFalse(service.activateSupport(source, "dc_renewal_staff"));
        select("berserker", RunPreparationService.RunState.RUNNING, List.of(source));
        when(world.getName()).thenReturn("world");
        service.activateClass(dropEvent(source));
        verify(statsManager, never()).calculateStats(any());
    }

    @Test
    void bloodrageScalesStatsAndOwnedEnemyDamageThenExpiresWithNativeCooldown() {
        service.activateClass(dropEvent(source));
        runScheduled();
        Stats stats = new Stats();
        stats.set(StatType.STRENGTH, 100);
        service.onStats(new StatsCalculateEvent(source, stats));
        assertEquals(145, stats.get(StatType.STRENGTH).getValue(), .001);
        Mob own = mob(1, 64, 0, true);
        var outgoing = new DamageCalculationEvent(source, own, null, 100, false);
        service.onOutgoing(outgoing);
        assertEquals(145, outgoing.getDamage(), .001);
        var foreign = new DamageCalculationEvent(source, mob(2, 64, 0, false), null, 100, false);
        service.onOutgoing(foreign);
        assertEquals(100, foreign.getDamage());
        clock.advance(10);
        service.tick();
        stats.set(StatType.STRENGTH, 100);
        service.onStats(new StatsCalculateEvent(source, stats));
        assertEquals(100, stats.get(StatType.STRENGTH).getValue());
        verify(statsManager, times(2)).calculateStats(source);
        // The native ItemAbility cooldown blocks an immediate recast without a new effect.
        service.activateClass(dropEvent(source));
        verify(statsManager, times(2)).calculateStats(source);
        verify(actionBar, times(1)).showActionBar(eq(source),
                argThat((String message) -> message.contains("Still on cooldown")));
        service.classAbility("berserker").getAbilityCooldown().resetCooldown(source.getUniqueId());
        service.activateClass(dropEvent(source));
        runScheduled();
        verify(statsManager, times(3)).calculateStats(source);
    }

    @Test
    void failedEmptyRoomCastSpendsNoCooldownAndLightningOnlyStrikesOwnedRoomMobs() {
        select("mage", RunPreparationService.RunState.RUNNING, List.of(source));
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());
        service.activateClass(dropEvent(source));
        verify(damageManager, never()).launchProjectile(any(Player.class), eq(Arrow.class), any(FinalDamageCalculation.class));
        verify(actionBar, times(1)).showActionBar(eq(source),
                argThat((Component message) -> PlainTextComponentSerializer.plainText().serialize(message)
                        .contains("No dungeon enemies")));
        Mob own = mob(1, 64, 0, true);
        Mob foreign = mob(2, 64, 0, false);
        Mob otherRoom = mob(30, 64, 0, true);
        Mob boss = mob(3, 64, 0, false);
        when(bosses.read(boss)).thenReturn(Optional.of(instance));
        Mob performer = mob(4, 64, 0, false);
        when(difficulty.belongsTo(performer, instance)).thenReturn(true);
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(own, foreign, otherRoom, boss, performer));
        service.activateClass(dropEvent(source));
        verify(own).addPotionEffect(argThat(effect -> effect.getType().equals(PotionEffectType.GLOWING)
                && effect.getDuration() == 200));
        verify(own).damage(eq(80000D), argThat((DamageSource damage) -> damage.getDirectEntity() instanceof Arrow
                && damage.getCausingEntity().equals(source) && damage.getDamageType().equals(DamageType.ARROW)));
        verify(foreign, never()).damage(anyDouble(), any(DamageSource.class));
        verify(otherRoom, never()).damage(anyDouble(), any(DamageSource.class));
        verify(boss).damage(eq(80000D), any(DamageSource.class));
        verify(performer).damage(eq(80000D), any(DamageSource.class));
        verify(damageManager, times(1)).launchProjectile(eq(source), eq(Arrow.class), any(FinalDamageCalculation.class));
        verify(damageArrow).remove();
        // The native cooldown spent by the successful cast blocks an immediate recast.
        service.activateClass(dropEvent(source));
        verify(damageManager, times(1)).launchProjectile(eq(source), eq(Arrow.class), any(FinalDamageCalculation.class));
    }

    @Test
    void tankReductionPreventsLethalTransitionAndRestoresPreviousTargetAtExpiry() {
        select("tank", RunPreparationService.RunState.RUNNING, List.of(source));
        Player previous = player(2, 64, 0, instance, PlayerLifecycleService.PlayerState.ALIVE);
        Mob own = mob(1, 64, 0, true);
        when(own.getTarget()).thenReturn(previous);
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(own));
        service.activateClass(dropEvent(source));
        verify(own).setTarget(source);
        when(own.getTarget()).thenReturn(source);
        var target = mock(EntityTargetLivingEntityEvent.class);
        when(target.getEntity()).thenReturn(own);
        service.onTarget(target);
        verify(target).setTarget(source);

        when(source.getHealth()).thenReturn(20D);
        var incoming = new EntityDamageEvent(source, EntityDamageEvent.DamageCause.ENTITY_ATTACK, mock(DamageSource.class), 30);
        service.onIncoming(incoming);
        new BukkitDungeonLifecycleListener(lifecycle, runs, plugin, clock).onLethalDamage(incoming);
        assertEquals(15, incoming.getFinalDamage());
        assertFalse(incoming.isCancelled());
        verify(lifecycle, never()).lethal(any(), any(), any());

        clock.advance(10);
        service.tick();
        verify(own).setTarget(previous);
        var after = new EntityDamageEvent(source, EntityDamageEvent.DamageCause.ENTITY_ATTACK, mock(DamageSource.class), 30);
        service.onIncoming(after);
        assertEquals(30, after.getDamage());
    }

    @Test
    void supportHealIncludesOnlyLivingNearbyPartyMembersInSameInstanceAndWorld() {
        Player near = player(16, 64, 0, instance, PlayerLifecycleService.PlayerState.ALIVE);
        Player far = player(17, 64, 0, instance, PlayerLifecycleService.PlayerState.ALIVE);
        Player ghost = player(2, 64, 0, instance, PlayerLifecycleService.PlayerState.GHOST);
        Player foreign = player(2, 64, 0, UUID.randomUUID(), PlayerLifecycleService.PlayerState.ALIVE);
        Player otherWorld = player(2, 64, 0, instance, PlayerLifecycleService.PlayerState.ALIVE);
        World elsewhere = mock(World.class);
        when(elsewhere.getName()).thenReturn("dungeon_instances");
        when(otherWorld.getWorld()).thenReturn(elsewhere);
        select("healer", RunPreparationService.RunState.RUNNING,
                List.of(source, near, far, ghost, foreign, otherWorld));
        assertTrue(service.activateSupport(source, "dc_renewal_staff"));
        nativeStats.verify(() -> StatsManager.healPlayerPercent(source, 30));
        nativeStats.verify(() -> StatsManager.healPlayerPercent(near, 30));
        for (Player excluded : List.of(far, ghost, foreign, otherWorld)) {
            nativeStats.verify(() -> StatsManager.healPlayerPercent(eq(excluded), anyDouble()), never());
        }
        // Direct effect calls carry no cooldown; the native ItemAbility flow gates recasts.
        assertTrue(service.activateSupport(source, "dc_renewal_staff"));
        assertTrue(service.activateSupport(source, "dc_dawnlight_tome"));
        clock.advance(1);
        service.tick();
        nativeStats.verify(() -> StatsManager.healPlayerPercent(near, 5));
        state(near, PlayerLifecycleService.PlayerState.GHOST);
        clock.advance(1);
        service.tick();
        nativeStats.verify(() -> StatsManager.healPlayerPercent(near, 5), times(1));
    }

    @Test
    void lifesurgeFullyHealsDistantLivingPartyMembersAndRegeneratesForTenSeconds() {
        Player distant = player(50, 64, 0, instance, PlayerLifecycleService.PlayerState.ALIVE);
        select("healer", RunPreparationService.RunState.RUNNING, List.of(source, distant));
        service.activateClass(dropEvent(source));
        nativeStats.verify(() -> StatsManager.healPlayerPercent(distant, 100));
        clock.advance(1);
        service.tick();
        nativeStats.verify(() -> StatsManager.healPlayerPercent(distant, 8));
        clock.advance(9);
        service.tick();
        nativeStats.verify(() -> StatsManager.healPlayerPercent(distant, 8), times(1));
    }

    @Test
    void healerDungeonLevelIncreasesSupportHealingAndRegenerationButFullHealRemainsFull() {
        select("healer", RunPreparationService.RunState.RUNNING, List.of(source));
        var healer = mock(ClassDefinition.class);
        when(healer.healingPercentPerLevel()).thenReturn(2D);
        when(config.snapshot().classes()).thenReturn(Map.of("healer", healer));
        assertTrue(service.activateSupport(source, "dc_renewal_staff"));
        nativeStats.verify(() -> StatsManager.healPlayerPercent(eq(source), doubleThat(value -> Math.abs(value - 42) < .001)));
        clock.advance(1);
        service.tick();
        nativeStats.verify(() -> StatsManager.healPlayerPercent(source, 7));
        service.activateClass(dropEvent(source));
        nativeStats.verify(() -> StatsManager.healPlayerPercent(source, 100));
        clock.advance(1);
        service.tick();
        nativeStats.verify(() -> StatsManager.healPlayerPercent(eq(source), doubleThat(value -> Math.abs(value - 11.2) < .001)));
    }

    @Test
    void explosiveArrowCancelsDirectDamageAndHitsOnlyOwnedMobsInsideEightBlockRoomBlast() {
        select("archer", RunPreparationService.RunState.RUNNING, List.of(source));
        Arrow arrow = arrow();
        service.activateClass(dropEvent(source));
        var direct = new EntityDamageByEntityEvent(arrow, source, EntityDamageEvent.DamageCause.PROJECTILE, mock(DamageSource.class), 10);
        service.onArrowDamage(direct);
        assertTrue(direct.isCancelled());
        Mob own = mob(7, 64, 0, true);
        Mob foreign = mob(3, 64, 0, false);
        Mob outsideSphere = mob(7, 64, 7, true);
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(own, foreign, outsideSphere));
        var hit = mock(ProjectileHitEvent.class);
        when(hit.getEntity()).thenReturn(arrow);
        service.onArrowHit(hit);
        verify(hit).setCancelled(true);
        verify(arrow).remove();
        verify(own).damage(eq(80_000D), argThat((DamageSource damage) -> damage.getDirectEntity() instanceof Arrow
                && damage.getCausingEntity().equals(source) && damage.getDamageType().equals(DamageType.ARROW)));
        verify(foreign, never()).damage(anyDouble(), any(DamageSource.class));
        verify(outsideSphere, never()).damage(anyDouble(), any(DamageSource.class));
        service.onArrowHit(hit);
        verify(own, times(1)).damage(anyDouble(), any(DamageSource.class));
        verify(damageArrow).remove();
    }

    @Test
    void leavingInstanceRemovesActiveArrow() {
        select("archer", RunPreparationService.RunState.RUNNING, List.of(source));
        Arrow arrow = arrow();
        service.activateClass(dropEvent(source));
        when(runs.instanceFor(source.getUniqueId())).thenReturn(Optional.empty());
        service.tick();
        verify(arrow).remove();
    }

    @Test
    void expiredArrowIsRemovedAndCannotExplodeFromALateHit() {
        select("archer", RunPreparationService.RunState.RUNNING, List.of(source));
        Arrow arrow = arrow();
        service.activateClass(dropEvent(source));
        clock.advance(10);
        service.tick();
        verify(arrow).remove();
        var hit = mock(ProjectileHitEvent.class);
        when(hit.getEntity()).thenReturn(arrow);
        service.onArrowHit(hit);
        verify(damageManager, never()).launchProjectile(any(Player.class), eq(Arrow.class), any(FinalDamageCalculation.class));
    }

    @Test
    void temporaryStatsCannotTransferToAnotherInstanceAndAreRefreshedOnClose() {
        service.activateClass(dropEvent(source));
        runScheduled();
        UUID nextInstance = UUID.randomUUID();
        var run = runs.info(instance);
        when(runs.instanceFor(source.getUniqueId())).thenReturn(Optional.of(nextInstance));
        when(runs.info(nextInstance)).thenReturn(run);
        UUID id = source.getUniqueId();
        when(lifecycle.player(nextInstance, id)).thenReturn(Optional.of(new PlayerLifecycleService.PlayerSnapshot(
                id, PlayerLifecycleService.PlayerState.ALIVE, true, null, null, 0)));
        Stats stats = new Stats();
        stats.set(StatType.STRENGTH, 100);
        service.onStats(new StatsCalculateEvent(source, stats));
        assertEquals(100, stats.get(StatType.STRENGTH).getValue());
        service.close();
        verify(statsManager, times(2)).calculateStats(source);
    }

    @Test
    void bannerModelsAndParticleAreasExpireReplaceAndCleanUpWithTheirCaster() {
        assertTrue(service.activateSupport(source, "dc_aegis_standard"));
        var aegis = displays.getFirst();
        verify(aegis).setItemStack(argThat(item -> item.getType() == Material.WHITE_BANNER));
        verify(aegis).setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        verify(aegis).setPersistent(false);
        verify(aegis).setInvulnerable(true);
        verify(world, times(120)).spawnParticle(eq(Particle.DUST), any(Location.class), eq(1),
                eq(0D), eq(0D), eq(0D), eq(0D), any(Particle.DustOptions.class));
        clock.advance(1);
        service.tick();
        verify(world, times(240)).spawnParticle(eq(Particle.DUST), any(Location.class), eq(1),
                eq(0D), eq(0D), eq(0D), eq(0D), any(Particle.DustOptions.class));
        assertTrue(service.activateSupport(source, "dc_aegis_standard"));
        verify(aegis).remove();
        clock.advance(10);
        service.tick();
        verify(displays.get(1)).remove();

        assertTrue(service.activateSupport(source, "dc_war_standard"));
        verify(displays.get(2)).setItemStack(argThat(item -> item.getType() == Material.RED_BANNER));
        when(runs.instanceFor(source.getUniqueId())).thenReturn(Optional.empty());
        service.tick();
        verify(displays.get(2)).remove();
        when(runs.instanceFor(source.getUniqueId())).thenReturn(Optional.of(instance));
        assertTrue(service.activateSupport(source, "dc_wind_standard"));
        verify(displays.get(3)).setItemStack(argThat(item -> item.getType() == Material.LIGHT_BLUE_BANNER));
        service.close();
        verify(displays.get(3)).remove();
    }

    @Test
    void newBuffItemsAffectNearbyPartyStatsAndOwnedEnemyDamage() {
        Player near = player(15, 64, 0, instance, PlayerLifecycleService.PlayerState.ALIVE);
        Player far = player(17, 64, 0, instance, PlayerLifecycleService.PlayerState.ALIVE);
        select("mage", RunPreparationService.RunState.RUNNING, List.of(source, near, far));
        for (String id : List.of("dc_war_standard", "dc_wind_standard", "dc_focus_orb", "dc_vitality_totem")) {
            assertTrue(service.activateSupport(source, id));
        }
        Stats stats = new Stats();
        for (StatType type : List.of(StatType.STRENGTH, StatType.CRIT_DAMAGE, StatType.SPEED,
                StatType.INTELLIGENCE, StatType.HEALTH)) stats.set(type, 100);
        service.onStats(new StatsCalculateEvent(near, stats));
        assertEquals(135, stats.get(StatType.STRENGTH).getValue(), .001);
        assertEquals(125, stats.get(StatType.CRIT_DAMAGE).getValue(), .001);
        assertEquals(130, stats.get(StatType.SPEED).getValue(), .001);
        assertEquals(25, stats.get(StatType.ATTACK_SPEED).getValue(), .001);
        assertEquals(140, stats.get(StatType.INTELLIGENCE).getValue(), .001);
        assertEquals(500, stats.get(StatType.ABILITY_DAMAGE).getValue(), .001);
        assertEquals(125, stats.get(StatType.HEALTH).getValue(), .001);
        Stats capped = new Stats();
        capped.set(StatType.SPEED, StatsManager.SPEED_LIMIT);
        capped.set(StatType.ATTACK_SPEED, StatsManager.ATTACK_SPEED_LIMIT);
        service.onStats(new StatsCalculateEvent(near, capped));
        assertEquals(StatsManager.SPEED_LIMIT, capped.get(StatType.SPEED).getValue());
        assertEquals(StatsManager.ATTACK_SPEED_LIMIT, capped.get(StatType.ATTACK_SPEED).getValue());
        Stats distant = new Stats(); distant.set(StatType.STRENGTH, 100);
        service.onStats(new StatsCalculateEvent(far, distant));
        assertEquals(100, distant.get(StatType.STRENGTH).getValue());
        var hit = new DamageCalculationEvent(near, mob(1, 64, 0, true), null, 100, false);
        service.onOutgoing(hit);
        assertEquals(115, hit.getDamage(), .001);
        nativeStats.verify(() -> StatsManager.healPlayerPercent(near, 15));
        clock.advance(10); service.tick();
        stats.set(StatType.HEALTH, 100);
        service.onStats(new StatsCalculateEvent(near, stats));
        assertEquals(100, stats.get(StatType.HEALTH).getValue());
    }

    @Test
    void mendingWandUsesZombieSwordFlatAndPercentFormulaForInjuredLivingPartyOnly() {
        Player near = player(7, 64, 0, instance, PlayerLifecycleService.PlayerState.ALIVE);
        Player far = player(9, 64, 0, instance, PlayerLifecycleService.PlayerState.ALIVE);
        Player ghost = player(1, 64, 0, instance, PlayerLifecycleService.PlayerState.GHOST);
        Player foreign = player(1, 64, 0, UUID.randomUUID(), PlayerLifecycleService.PlayerState.ALIVE);
        when(source.getHealth()).thenReturn(1000D);
        select("healer", RunPreparationService.RunState.RUNNING, List.of(source, near, far, ghost, foreign));
        var healer = mock(ClassDefinition.class);
        when(healer.healingPercentPerLevel()).thenReturn(2D);
        when(config.snapshot().classes()).thenReturn(Map.of("healer", healer));
        assertTrue(service.activateSupport(source, "dc_mending_wand"));
        nativeStats.verify(() -> StatsManager.healPlayer(near, 140));
        nativeStats.verify(() -> StatsManager.healPlayerPercent(near, 14));
        for (Player excluded : List.of(source, far, ghost, foreign)) {
            nativeStats.verify(() -> StatsManager.healPlayer(eq(excluded), anyDouble()), never());
            nativeStats.verify(() -> StatsManager.healPlayerPercent(eq(excluded), anyDouble()), never());
        }
        when(near.getHealth()).thenReturn(1000D);
        assertFalse(service.activateSupport(source, "dc_mending_wand"));
        nativeStats.verify(() -> StatsManager.healPlayer(near, 140), times(1));
        assertTrue(displays.isEmpty());
    }

    private Player player(double x, double y, double z, UUID instanceId, PlayerLifecycleService.PlayerState state) {
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(player.getHealth()).thenReturn(500D);
        when(player.isValid()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenAnswer(ignored -> new Location(world, x, y, z));
        when(player.getEyeLocation()).thenAnswer(ignored -> new Location(world, x, y + 1.6, z));
        var inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.AIR));
        when(server.getPlayer(id)).thenReturn(player);
        when(runs.instanceFor(id)).thenReturn(Optional.of(instanceId));
        when(lifecycle.player(instanceId, id)).thenReturn(Optional.of(new PlayerLifecycleService.PlayerSnapshot(
                id, state, true, null, null, 0)));
        return player;
    }

    private PlayerEvent dropEvent(Player player) {
        var event = mock(PlayerDropItemEvent.class);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    private void runScheduled() {
        while (!scheduled.isEmpty()) scheduled.remove().run();
    }

    private void state(Player player, PlayerLifecycleService.PlayerState state) {
        UUID id = player.getUniqueId();
        when(lifecycle.player(instance, id)).thenReturn(Optional.of(
                new PlayerLifecycleService.PlayerSnapshot(id, state, true, null, null, 0)));
    }

    private void select(String classId, RunPreparationService.RunState state, List<Player> party) {
        var run = mock(RunPreparationService.RunSnapshot.class);
        Map<UUID, String> selected = Map.of(source.getUniqueId(), classId);
        List<UUID> participants = party.stream().map(Player::getUniqueId).toList();
        when(run.state()).thenReturn(state);
        when(run.selectedClasses()).thenReturn(selected);
        when(run.participants()).thenReturn(participants);
        when(runs.info(instance)).thenReturn(Optional.of(run));
    }

    private void room() {
        var room = mock(Placement.class);
        when(room.bounds()).thenReturn(new Bounds(new Point(-10, 60, -10), new Point(10, 80, 10)));
        var plan = mock(LayoutPlan.class);
        when(plan.placements()).thenReturn(List.of(room));
        when(generation.layoutPlan(instance)).thenReturn(Optional.of(plan));
    }

    private Mob mob(double x, double y, double z, boolean owned) {
        Mob mob = mock(Mob.class);
        when(mob.getUniqueId()).thenReturn(UUID.randomUUID());
        when(mob.isValid()).thenReturn(true);
        when(mob.getWorld()).thenReturn(world);
        when(mob.getLocation()).thenAnswer(ignored -> new Location(world, x, y, z));
        when(server.getEntity(mob.getUniqueId())).thenReturn(mob);
        when(identity.belongsTo(mob, instance)).thenReturn(owned);
        return mob;
    }

    private Arrow arrow() {
        Arrow arrow = mock(Arrow.class);
        when(arrow.getUniqueId()).thenReturn(UUID.randomUUID());
        var data = mock(PersistentDataContainer.class);
        when(data.has(any(NamespacedKey.class), eq(PersistentDataType.BYTE))).thenReturn(true);
        when(arrow.getPersistentDataContainer()).thenReturn(data);
        when(arrow.getLocation()).thenAnswer(ignored -> new Location(world, 0, 64, 0));
        when(server.getEntity(arrow.getUniqueId())).thenReturn(arrow);
        when(source.launchProjectile(eq(Arrow.class), any())).thenReturn(arrow);
        return arrow;
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.EPOCH;
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
