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
        var maximum = mock(org.bukkit.attribute.AttributeInstance.class);
        when(online.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)).thenReturn(maximum);
        when(maximum.getValue()).thenReturn(1500.0);
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
        assertEquals("ꜰʟᴏᴏʀ ɪ §8• §cɪᴍᴘᴏꜱꜱɪʙʟᴇ", java.util.concurrent.CompletableFuture.supplyAsync(
                () -> expansion.onRequest(player, "sidebar_floor")).get());
        verify(generation, times(1)).layoutContext(INSTANCE);
        assertEquals("50", expansion.onRequest(player, "clear_percent"));
        assertEquals("1", expansion.onRequest(player, "rooms_cleared"));
        assertEquals("2", expansion.onRequest(player, "rooms_total"));
        assertEquals("2", expansion.onRequest(player, "sidebar_deaths"));
        String name = "ʟɪᴅᴀɴ";
        assertEquals("§b[ᴛ] §f" + name + " §a1,235❤", java.util.concurrent.CompletableFuture.supplyAsync(
                () -> expansion.onRequest(player, "sidebar_party_1")).get());
        verify(online, times(1)).getHealth();
        when(participant.state()).thenReturn(PlayerLifecycleService.PlayerState.GHOST);
        assertEquals("§b[ᴛ] §f" + name + " §c☠", expansion.onRequest(player, "sidebar_party_1"));
        when(participant.state()).thenReturn(PlayerLifecycleService.PlayerState.ALIVE);
        when(participant.online()).thenReturn(false);
        assertEquals("§b[ᴛ] §f" + name + " §8ᴏꜰꜰʟɪɴᴇ", expansion.onRequest(player, "sidebar_party_1"));
        when(participant.online()).thenReturn(true);
        when(online.isDead()).thenReturn(true);
        expansion.refreshSnapshots();
        assertEquals("§b[ᴛ] §f" + name + " §c☠", expansion.onRequest(player, "sidebar_party_1"));
        when(online.isDead()).thenReturn(false);
        when(online.getHealth()).thenReturn(980.0);
        expansion.refreshSnapshots();
        assertEquals("§b[ᴛ] §f" + name + " §e980❤", expansion.onRequest(player, "sidebar_party_1"));
        when(maximum.getValue()).thenReturn(1000.0);
        double[] health = {1000, 750, 749, 500, 499, 250, 249, 1};
        String[] colors = {"§a", "§a", "§e", "§e", "§6", "§6", "§c", "§c"};
        for (int index = 0; index < health.length; index++) {
            when(online.getHealth()).thenReturn(health[index]);
            expansion.refreshSnapshots();
            String suffix = colors[index] + String.format(java.util.Locale.US, "%,.0f", health[index]) + "❤";
            assertEquals("§b[ᴛ] §f" + name + " " + suffix, java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> expansion.onRequest(player, "sidebar_party_1")).get());
        }
        when(maximum.getValue()).thenReturn(4.0);
        expansion.refreshSnapshots();
        assertEquals("§b[ᴛ] §f" + name + " §61❤", expansion.onRequest(player, "sidebar_party_1"));
        when(online.getHealth()).thenReturn(0.0);
        expansion.refreshSnapshots();
        assertEquals("§b[ᴛ] §f" + name + " §c☠", expansion.onRequest(player, "sidebar_party_1"));
        assertEquals("", expansion.onRequest(player, "sidebar_party_2"));
        assertEquals("", expansion.onRequest(player, "sidebar_score"));
        var score = mock(me.lidan.dungeonCrawlers.core.score.ScoreService.FinalScoreSnapshot.class);
        when(score.total()).thenReturn(305);
        when(score.rank()).thenReturn(me.lidan.dungeonCrawlers.core.score.DungeonRank.S_PLUS);
        when(score.elapsed()).thenReturn(java.time.Duration.ofSeconds(95));
        finalScore.set(score);
        when(run.state()).thenReturn(RunPreparationService.RunState.COMPLETED);
        assertEquals("§fꜱᴄᴏʀᴇ: §a305 §7(ꜱ+)", expansion.onRequest(player, "sidebar_score"));
        assertEquals("1m 35s", expansion.onRequest(player, "player_elapsed_time"));
        assertEquals("§aᴄʟᴀɪᴍ ʏᴏᴜʀ ʀᴇᴡᴀʀᴅꜱ", expansion.onRequest(player, "sidebar_phase"));
        when(runs.instanceFor(PLAYER)).thenReturn(Optional.empty());
        assertEquals("false", expansion.onRequest(player, "in_dungeon"));
        assertEquals("0", expansion.onRequest(player, "rooms_cleared"));
        assertEquals("", expansion.onRequest(player, "sidebar_party_1"));
    }

    @Test
    void roomSecretsFollowEachViewersLocationAndDiscoveryWithoutAsyncPlayerReads() throws Exception {
        var runs = mock(RunPreparationService.class);
        var run = mock(RunPreparationService.RunSnapshot.class);
        var generation = mock(GenerationService.class);
        var secrets = mock(SecretDiscoveryService.class);
        var plugin = mock(JavaPlugin.class);
        var server = mock(org.bukkit.Server.class);
        var viewer = mock(org.bukkit.entity.Player.class);
        var other = mock(org.bukkit.entity.Player.class);
        var world = mock(org.bukkit.World.class);
        when(world.getName()).thenReturn("dungeon_instances");
        when(viewer.getUniqueId()).thenReturn(PLAYER);
        when(other.getUniqueId()).thenReturn(OUTSIDER);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPlayer(PLAYER)).thenReturn(viewer);
        when(server.getPlayer(OUTSIDER)).thenReturn(other);
        when(server.getOfflinePlayer(PLAYER)).thenReturn(viewer);
        when(server.getOfflinePlayer(OUTSIDER)).thenReturn(other);
        when(run.instanceId()).thenReturn(INSTANCE);
        when(run.state()).thenReturn(RunPreparationService.RunState.RUNNING);
        when(run.participants()).thenReturn(List.of(PLAYER, OUTSIDER));
        when(run.selectedClasses()).thenReturn(Map.of());
        when(runs.snapshots()).thenReturn(List.of(run));
        when(runs.info(INSTANCE)).thenReturn(Optional.of(run));
        when(runs.instanceFor(PLAYER)).thenReturn(Optional.of(INSTANCE));
        when(runs.instanceFor(OUTSIDER)).thenReturn(Optional.of(INSTANCE));
        var point1 = new me.lidan.dungeonCrawlers.core.template.TemplateModels.Point(4, 64, 4);
        var point2 = new me.lidan.dungeonCrawlers.core.template.TemplateModels.Point(24, 64, 4);
        var bounds = new me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds(
                point1, new me.lidan.dungeonCrawlers.core.template.TemplateModels.Point(30, 70, 10));
        when(generation.protectionRegions()).thenReturn(List.of(new GenerationService.InstanceRegion(
                "dungeon_instances", INSTANCE, bounds, java.util.Set.of(PLAYER, OUTSIDER))));
        when(viewer.getLocation()).thenReturn(new org.bukkit.Location(world, 4, 64, 4));
        when(other.getLocation()).thenReturn(new org.bukkit.Location(world, 24, 64, 4));
        var room1 = mock(me.lidan.dungeonCrawlers.core.location.LocationContextService.RoomContext.class);
        var room2 = mock(me.lidan.dungeonCrawlers.core.location.LocationContextService.RoomContext.class);
        when(room1.instanceId()).thenReturn(INSTANCE);
        when(room2.instanceId()).thenReturn(INSTANCE);
        when(room1.index()).thenReturn(1);
        when(room2.index()).thenReturn(2);
        when(room1.templateId()).thenReturn("room_one");
        when(room2.templateId()).thenReturn("room_two");
        when(secrets.locate(INSTANCE, point1)).thenReturn(Optional.of(room1));
        when(secrets.locate(INSTANCE, point2)).thenReturn(Optional.of(room2));
        var found = mock(SecretDiscoveryService.SecretSnapshot.class);
        var hidden = mock(SecretDiscoveryService.SecretSnapshot.class);
        var elsewhere = mock(SecretDiscoveryService.SecretSnapshot.class);
        when(found.id()).thenReturn(new me.lidan.dungeonCrawlers.core.template.TemplateModels.SecretId(INSTANCE, 1, point1));
        when(hidden.id()).thenReturn(new me.lidan.dungeonCrawlers.core.template.TemplateModels.SecretId(INSTANCE, 1, point2));
        when(elsewhere.id()).thenReturn(new me.lidan.dungeonCrawlers.core.template.TemplateModels.SecretId(INSTANCE, 2, point2));
        when(found.discovered()).thenReturn(true);
        when(elsewhere.discovered()).thenReturn(true);
        when(secrets.info(INSTANCE)).thenReturn(Optional.of(new SecretDiscoveryService.InstanceSnapshot(
                INSTANCE, List.of(found, hidden, elsewhere), Map.of())));
        var expansion = new DungeonPlaceholderExpansion(plugin, generation, runs,
                mock(PlayerLifecycleService.class), secrets, new DebugSettings(false), ignored -> null);
        expansion.refreshSnapshots();
        assertEquals("1/2", java.util.concurrent.CompletableFuture.supplyAsync(
                () -> expansion.onRequest(viewer, "sidebar_room_secrets")).get());
        assertEquals("1/1", expansion.onRequest(other, "sidebar_room_secrets"));
        assertEquals("1", expansion.onRequest(viewer, "player_current_room_secrets_found"));
        assertEquals("2", expansion.onRequest(viewer, "player_current_room_secrets_total"));
        assertEquals("1/2", expansion.onRequest(viewer, "player_current_room_secrets"));
        assertEquals("1", expansion.onRequest(viewer, "player_current_room"));
        assertEquals("room_one", expansion.onRequest(viewer, "player_current_room_id"));
        assertEquals("2/3", expansion.onRequest(viewer, "player_secrets"));
        verify(viewer, times(1)).getLocation();
        verify(secrets, times(1)).locate(INSTANCE, point1);
        when(hidden.discovered()).thenReturn(true);
        assertEquals("2/2", expansion.onRequest(viewer, "sidebar_room_secrets"));
        when(viewer.getLocation()).thenReturn(new org.bukkit.Location(world, 24, 64, 4));
        expansion.refreshSnapshots();
        assertEquals("1/1", expansion.onRequest(viewer, "sidebar_room_secrets"));
        assertEquals("2", expansion.onRequest(viewer, "player_current_room"));
        when(viewer.getLocation()).thenReturn(new org.bukkit.Location(world, 50, 64, 4));
        expansion.refreshSnapshots();
        assertEquals("0/0", expansion.onRequest(viewer, "sidebar_room_secrets"));
        var otherWorld = mock(org.bukkit.World.class);
        when(otherWorld.getName()).thenReturn("world");
        when(viewer.getLocation()).thenReturn(new org.bukkit.Location(otherWorld, 4, 64, 4));
        expansion.refreshSnapshots();
        assertEquals("0/0", expansion.onRequest(viewer, "sidebar_room_secrets"));
        when(server.getPlayer(PLAYER)).thenReturn(null);
        expansion.refreshSnapshots();
        assertEquals("0/0", expansion.onRequest(viewer, "sidebar_room_secrets"));
        when(runs.instanceFor(PLAYER)).thenReturn(Optional.empty());
        assertEquals("0/0", expansion.onRequest(viewer, "player_current_room_secrets"));
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
