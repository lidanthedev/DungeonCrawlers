package me.lidan.dungeonCrawlers.commands;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import revxrsal.commands.bukkit.BukkitLamp;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
class DungeonDifficultyDebugCommandTest {
    @Test void debugCommandsRegisterWithoutParsingListenerEvents() {
        MockBukkit.mock();
        try {
            var plugin = MockBukkit.createMockPlugin();
            assertDoesNotThrow(() -> BukkitLamp.builder(plugin).build().register(
                    new DungeonDifficultyDebugCommand(null, () -> false, null, null)));
        } finally { MockBukkit.unmock(); }
    }
}
