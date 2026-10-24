package me.lidan.dungeonCrawlers;

import me.lidan.cavecrawlers.skills.SkillInfo;
import me.lidan.cavecrawlers.skills.SkillsManager;
import me.lidan.dungeonCrawlers.core.difficulty.Difficulty;
import me.lidan.dungeonCrawlers.core.difficulty.DungeonProgressionService;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class DungeonXpTest {
    @BeforeAll static void initializeNativeConfigLoader() throws Exception {
        try (var plugins = mockStatic(org.bukkit.plugin.java.JavaPlugin.class)) {
            plugins.when(() -> org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(
                    me.lidan.cavecrawlers.objects.ConfigLoader.class))
                    .thenReturn(mock(org.bukkit.plugin.java.JavaPlugin.class));
            Class.forName("me.lidan.cavecrawlers.objects.ConfigLoader");
        }
    }
    @Test void awardsThroughUnmodifiedNativeApi() {
        var plugin = mock(DungeonCrawlers.class);
        var server = mock(Server.class);
        var player = mock(Player.class);
        var skills = mock(SkillsManager.class);
        var dungeon = mock(SkillInfo.class);
        UUID id = UUID.randomUUID();
        when(plugin.getServer()).thenReturn(server);
        when(server.getPlayer(id)).thenReturn(player);
        when(player.isOnline()).thenReturn(true);
        when(skills.getSkillInfo("dungeon")).thenReturn(dungeon);
        doCallRealMethod().when(plugin).awardDungeonXp(any());
        try (var nativeSkills = mockStatic(SkillsManager.class)) {
            nativeSkills.when(SkillsManager::getInstance).thenReturn(skills);
            plugin.awardDungeonXp(outcome(id));
            verify(skills).giveXp(player, dungeon, 1125, true);
        }
    }

    @Test void offlinePlayersAreNotMutatedByTheNativeGrant() {
        var plugin = mock(DungeonCrawlers.class);
        var server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        doCallRealMethod().when(plugin).awardDungeonXp(any());
        try (var nativeSkills = mockStatic(SkillsManager.class)) {
            plugin.awardDungeonXp(outcome(UUID.randomUUID()));
            nativeSkills.verifyNoInteractions();
        }
    }

    @Test void nativeFailureDoesNotAbortDungeonFinalization() {
        var plugin = mock(DungeonCrawlers.class);
        var server = mock(Server.class);
        var player = mock(Player.class);
        var skills = mock(SkillsManager.class);
        var dungeon = mock(SkillInfo.class);
        var logger = mock(Logger.class);
        UUID id = UUID.randomUUID();
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(logger);
        when(server.getPlayer(id)).thenReturn(player);
        when(player.isOnline()).thenReturn(true);
        when(player.getName()).thenReturn("TestPlayer");
        when(skills.getSkillInfo("dungeon")).thenReturn(dungeon);
        doThrow(new IllegalStateException("unavailable")).when(skills).giveXp(player, dungeon, 1125, true);
        doCallRealMethod().when(plugin).awardDungeonXp(any());
        try (var nativeSkills = mockStatic(SkillsManager.class)) {
            nativeSkills.when(SkillsManager::getInstance).thenReturn(skills);
            assertDoesNotThrow(() -> plugin.awardDungeonXp(outcome(id)));
            verify(logger).warning("Dungeon XP grant failed for TestPlayer: unavailable");
        }
    }

    private static DungeonProgressionService.Outcome outcome(UUID player) {
        return new DungeonProgressionService.Outcome(2, UUID.randomUUID(), UUID.randomUUID(), player,
                "floor_3", Difficulty.HARD, true, 1125);
    }
}
