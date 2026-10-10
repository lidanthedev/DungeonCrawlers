package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ClassDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.StatModifiers;
import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.secret.SecretDiscoveryService;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.bukkit.Material;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import me.lidan.dungeonCrawlers.core.combat.CombatRoomService;
import me.lidan.dungeonCrawlers.core.difficulty.Difficulty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

class DungeonPlaceholderExpansionTest {
    private static final UUID INSTANCE = UUID.fromString("00000000-0000-0000-0000-000000000041");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final UUID OUTSIDER = UUID.fromString("00000000-0000-0000-0000-000000000043");

    @BeforeEach
    void setUpBukkit() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDownBukkit() {
        MockBukkit.unmock();
    }

    @Test
    void instanceThisResolvesUsingThePlaceholderPlayer() {
        RunPreparationService runs = mock(RunPreparationService.class);
        RunPreparationService.RunSnapshot run = mock(RunPreparationService.RunSnapshot.class);
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.getUniqueId()).thenReturn(PLAYER);
        when(run.instanceId()).thenReturn(INSTANCE);
        when(run.state()).thenReturn(RunPreparationService.RunState.RUNNING);
        when(runs.instanceFor(PLAYER)).thenReturn(Optional.of(INSTANCE));
        when(runs.info(INSTANCE)).thenReturn(Optional.of(run));

        DungeonPlaceholderExpansion expansion = expansion(runs);

        assertEquals("running", expansion.onRequest(player, "instance_this_state"));
        assertEquals("true", expansion.onRequest(player, "instance_this_exists"));
        assertEquals(INSTANCE.toString(), expansion.onRequest(player, "instance_this_id"));
        assertEquals("running", expansion.onRequest(player, "instance_" + INSTANCE + "_state"));
    }

    @Test
    void instanceThisUsesMissingInstanceFallbackOutsideADungeon() {
        RunPreparationService runs = mock(RunPreparationService.class);
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.getUniqueId()).thenReturn(OUTSIDER);
        when(runs.instanceFor(OUTSIDER)).thenReturn(Optional.empty());

        DungeonPlaceholderExpansion expansion = expansion(runs);

        assertEquals("false", expansion.onRequest(player, "instance_this_exists"));
        assertEquals("0", expansion.onRequest(player, "instance_this_players"));
        for (String field : new String[]{"score", "skill", "time", "exploration", "bonus"}) {
            assertEquals("0", expansion.onRequest(player, "instance_this_" + field));
        }
        assertEquals("", expansion.onRequest(player, "instance_this_state"));
        for (String key : new String[]{"score", "player_score", "player_skill_score", "player_time_score",
                "player_exploration_score", "player_bonus_score"}) {
            assertEquals("0", expansion.onRequest(player, key));
        }
    }

    @Test
    void classPlaceholdersReadTheRunSelectionAndLockAfterPreparing() {
        RunPreparationService runs = mock(RunPreparationService.class);
        RunPreparationService.RunSnapshot run = mock(RunPreparationService.RunSnapshot.class);
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.getUniqueId()).thenReturn(PLAYER);
        when(run.instanceId()).thenReturn(INSTANCE);
        when(run.state()).thenReturn(RunPreparationService.RunState.PREPARING);
        when(run.selectedClasses()).thenReturn(Map.of(PLAYER, "tank"));
        when(runs.instanceFor(PLAYER)).thenReturn(Optional.of(INSTANCE));
        when(runs.info(INSTANCE)).thenReturn(Optional.of(run));
        when(runs.selectedClass(PLAYER)).thenReturn(Optional.of(new ClassDefinition(
                "tank", "<green>Tank", Material.SHIELD, StatModifiers.empty())));

        DungeonPlaceholderExpansion expansion = expansion(runs);

        assertEquals("true", expansion.onRequest(player, "player_has_class"));
        assertEquals("Tank", expansion.onRequest(player, "player_class"));
        assertEquals("tank", expansion.onRequest(player, "player_class_id"));
        assertEquals("false", expansion.onRequest(player, "player_class_locked"));

        when(run.state()).thenReturn(RunPreparationService.RunState.RUNNING);
        assertEquals("true", expansion.onRequest(player, "player_class_locked"));
    }

    @Test
    void dungeonSidebarPublishesGenerationDataForAsyncTabAndUsesLiveRoomAndPartyState() throws Exception {
        var runs = mock(RunPreparationService.class);
        var run = mock(RunPreparationService.RunSnapshot.class);
        var generation = mock(GenerationService.class);
        var layout = mock(GenerationService.LayoutContext.class);
        var floor = mock(me.lidan.dungeonCrawlers.config.registry.ConfigModels.FloorDefinition.class);
        var combat = mock(CombatRoomService.class);
        var room1 = mock(CombatRoomService.RoomSnapshot.class);
        var room2 = mock(CombatRoomService.RoomSnapshot.class);
        var lifecycle = mock(PlayerLifecycleService.class);
        var participant = mock(PlayerLifecycleService.PlayerSnapshot.class);
        var player = mock(OfflinePlayer.class);
        var plugin = mock(JavaPlugin.class);
        var server = mock(org.bukkit.Server.class);
        var online = mock(org.bukkit.entity.Player.class);
        when(server.getPlayer(PLAYER)).thenReturn(online);
        when(online.getHealth()).thenReturn(1234.1);
        when(plugin.getServer()).thenReturn(server);
        when(server.getOfflinePlayer(PLAYER)).thenReturn(player);
        when(player.getName()).thenReturn("Lidan");
        when(player.getUniqueId()).thenReturn(PLAYER);
        when(run.instanceId()).thenReturn(INSTANCE);
        when(run.state()).thenReturn(RunPreparationService.RunState.RUNNING);
        when(run.participants()).thenReturn(List.of(PLAYER));
        when(run.selectedClasses()).thenReturn(Map.of(PLAYER, "tank"));
        when(runs.instanceFor(PLAYER)).thenReturn(Optional.of(INSTANCE));
        when(runs.info(INSTANCE)).thenReturn(Optional.of(run));
        when(runs.snapshots()).thenReturn(List.of(run));
        when(generation.layoutContext(INSTANCE)).thenReturn(Optional.of(layout));
        when(layout.floor()).thenReturn(floor);
        when(floor.displayName()).thenReturn("<gold>Floor I</gold>");
        when(layout.difficulty()).thenReturn(Difficulty.IMPOSSIBLE.defaults());
        when(room1.state()).thenReturn(CombatRoomService.RoomState.CLEARED);
        when(room2.state()).thenReturn(CombatRoomService.RoomState.ACTIVE);
        when(combat.info(INSTANCE)).thenReturn(Optional.of(new CombatRoomService.InstanceSnapshot(
                INSTANCE, List.of(room1, room2))));
        when(participant.playerId()).thenReturn(PLAYER);
        when(participant.state()).thenReturn(PlayerLifecycleService.PlayerState.ALIVE);
        when(participant.online()).thenReturn(true);
        when(participant.deaths()).thenReturn(2);
        when(lifecycle.info(INSTANCE)).thenReturn(Optional.of(new PlayerLifecycleService.InstanceSnapshot(
                INSTANCE, true, false, "running", List.of(participant))));
        var finalScore = new java.util.concurrent.atomic.AtomicReference<me.lidan.dungeonCrawlers.core.score.ScoreService.FinalScoreSnapshot>();
        var expansion = new DungeonPlaceholderExpansion(plugin, generation, runs, lifecycle,
                mock(SecretDiscoveryService.class), combat, new DebugSettings(false), ignored -> finalScore.get());
        expansion.refreshSnapshots();
        assertEquals("Floor I §8• §cImpossible", java.util.concurrent.CompletableFuture.supplyAsync(
                () -> expansion.onRequest(player, "sidebar_floor")).get());
        verify(generation, times(1)).layoutContext(INSTANCE);
        assertEquals("50", expansion.onRequest(player, "clear_percent"));
        assertEquals("1", expansion.onRequest(player, "rooms_cleared"));
        assertEquals("2", expansion.onRequest(player, "rooms_total"));
        assertEquals("2", expansion.onRequest(player, "sidebar_deaths"));
        String name = "Lidan";
        assertEquals("§b[T] §f" + name + " §a1,235❤", java.util.concurrent.CompletableFuture.supplyAsync(
                () -> expansion.onRequest(player, "sidebar_party_1")).get());
        verify(online, times(1)).getHealth();
        when(participant.state()).thenReturn(PlayerLifecycleService.PlayerState.GHOST);
        assertEquals("§b[T] §f" + name + " §c☠", expansion.onRequest(player, "sidebar_party_1"));
        when(participant.state()).thenReturn(PlayerLifecycleService.PlayerState.ALIVE);
        when(participant.online()).thenReturn(false);
        assertEquals("§b[T] §f" + name + " §8OFFLINE", expansion.onRequest(player, "sidebar_party_1"));
        when(participant.online()).thenReturn(true);
        when(online.isDead()).thenReturn(true);
        expansion.refreshSnapshots();
        assertEquals("§b[T] §f" + name + " §c☠", expansion.onRequest(player, "sidebar_party_1"));
        when(online.isDead()).thenReturn(false);
        when(online.getHealth()).thenReturn(980.0);
        expansion.refreshSnapshots();
        assertEquals("§b[T] §f" + name + " §a980❤", expansion.onRequest(player, "sidebar_party_1"));
        when(online.getHealth()).thenReturn(0.0);
        expansion.refreshSnapshots();
        assertEquals("§b[T] §f" + name + " §c☠", expansion.onRequest(player, "sidebar_party_1"));
        assertEquals("", expansion.onRequest(player, "sidebar_party_2"));
        assertEquals("", expansion.onRequest(player, "sidebar_score"));
        var score = mock(me.lidan.dungeonCrawlers.core.score.ScoreService.FinalScoreSnapshot.class);
        when(score.total()).thenReturn(305);
        when(score.rank()).thenReturn(me.lidan.dungeonCrawlers.core.score.DungeonRank.S_PLUS);
        when(score.elapsed()).thenReturn(java.time.Duration.ofSeconds(95));
        finalScore.set(score);
        when(run.state()).thenReturn(RunPreparationService.RunState.COMPLETED);
        assertEquals("§fScore: §a305 §7(S+)", expansion.onRequest(player, "sidebar_score"));
        assertEquals("1m 35s", expansion.onRequest(player, "player_elapsed_time"));
        assertEquals("§aClaim your rewards", expansion.onRequest(player, "sidebar_phase"));
        when(runs.instanceFor(PLAYER)).thenReturn(Optional.empty());
        assertEquals("false", expansion.onRequest(player, "in_dungeon"));
        assertEquals("0", expansion.onRequest(player, "rooms_cleared"));
        assertEquals("", expansion.onRequest(player, "sidebar_party_1"));
    }

    private static DungeonPlaceholderExpansion expansion(RunPreparationService runs) {
        return new DungeonPlaceholderExpansion(
                mock(JavaPlugin.class),
                mock(GenerationService.class),
                runs,
                mock(PlayerLifecycleService.class),
                mock(SecretDiscoveryService.class),
                new DebugSettings(false),
                ignored -> null);
    }
}
