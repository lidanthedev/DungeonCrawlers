package me.lidan.dungeonCrawlers.commands;

import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import revxrsal.commands.bukkit.BukkitLamp;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DifficultyIdSuggestionProviderTest {
    @Test void completesDifficultyArgumentAndFiltersPrefix() {
        var server = MockBukkit.mock();
        try {
            var plugin = MockBukkit.createMockPlugin();
            var player = server.addPlayer();
            player.setOp(true);
            BukkitLamp.builder(plugin).build().register(
                    new DungeonDifficultyDebugCommand(null, () -> false, null, null));
            assertEquals(List.of("normal", "hard", "insane", "extreme", "demonic", "hellish", "death",
                            "void", "hardcore", "impossible"),
                    server.getCommandMap().tabComplete(player, "dungeon instance generate-difficulty-debug floor_1 "));
            assertEquals(List.of("hard", "hellish", "hardcore"),
                    server.getCommandMap().tabComplete(player, "dungeon instance generate-difficulty-debug floor_1 h"));
        } finally { MockBukkit.unmock(); }
    }
}
