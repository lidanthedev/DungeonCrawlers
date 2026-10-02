package me.lidan.dungeonCrawlers.commands;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ConfigSnapshot;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.FloorDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigRegistryService;
import me.lidan.dungeonCrawlers.core.difficulty.Difficulty;
import me.lidan.dungeonCrawlers.core.difficulty.DungeonProgressionService;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.BukkitLamp;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DungeonProgressionCommandTest {
    @Test void checksPermissionArgumentsDefaultsAndDifficultySuggestions() {
        var server = MockBukkit.mock();
        try {
            var plugin = MockBukkit.createMockPlugin();
            var admin = server.addPlayer("Admin");
            admin.setOp(true);
            var target = server.addPlayer("Target");
            var configs = mock(ConfigRegistryService.class);
            when(configs.snapshot()).thenReturn(new ConfigSnapshot(1, Map.of("floor_1", mock(FloorDefinition.class)),
                    Map.of(), Map.of(), Map.of(), Set.of(), "test", Instant.EPOCH));
            var progression = mock(DungeonProgressionService.class);
            var builder = BukkitLamp.builder(plugin);
            builder.suggestionProviders().addProviderForAnnotation(SuggestWith.class, annotation -> {
                if (annotation.value() == FloorIdSuggestionProvider.class) {
                    return new FloorIdSuggestionProvider<BukkitCommandActor>(() -> List.of("floor_1"));
                }
                if (annotation.value() == OfflinePlayerSuggestionProvider.class) {
                    return new OfflinePlayerSuggestionProvider<BukkitCommandActor>(() -> List.of("Target"));
                }
                return null;
            });
            var command = new DungeonProgressionCommand(configs, progression);
            builder.build().register(command);
            assertEquals(List.of("hard", "hellish", "hardcore"), server.getCommandMap().tabComplete(admin,
                    "dungeon completions add Target floor_1 h"));
            server.dispatchCommand(target, "dungeon completions add Target floor_1 hardcore");
            verifyNoInteractions(progression);
            server.dispatchCommand(admin, "dungeon completions add Target floor_1 hardcore");
            verify(progression).addCompletions(target.getUniqueId(), "floor_1", Difficulty.HARDCORE, 1);
            server.dispatchCommand(admin, "dungeon completions add Target floor_1 HARDCORE 3");
            verify(progression).addCompletions(target.getUniqueId(), "floor_1", Difficulty.HARDCORE, 3);
            clearInvocations(progression);
            server.dispatchCommand(admin, "dungeon completions add Target floor_1 hardcore 0");
            server.dispatchCommand(admin, "dungeon completions add Target unknown hardcore 1");
            server.dispatchCommand(admin, "dungeon completions add Target floor_1 unknown 1");
            verifyNoInteractions(progression);
            var offline = mock(org.bukkit.OfflinePlayer.class);
            var offlineId = java.util.UUID.randomUUID();
            when(offline.getUniqueId()).thenReturn(offlineId);
            when(offline.getName()).thenReturn("Offline");
            when(offline.hasPlayedBefore()).thenReturn(true);
            command.add(admin, offline, "floor_1", "hardcore", 2);
            verify(progression).addCompletions(offlineId, "floor_1", Difficulty.HARDCORE, 2);
            clearInvocations(progression);
            when(offline.hasPlayedBefore()).thenReturn(false);
            command.add(admin, offline, "floor_1", "hardcore", 2);
            verifyNoInteractions(progression);
        } finally { MockBukkit.unmock(); }
    }
}
