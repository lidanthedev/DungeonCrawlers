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
            var builder = BukkitLamp.builder(plugin);
            AdminSuggestionProviders.register(builder, null, null, java.util.List::of, java.util.List::of,
                    java.util.List::of, java.util.List::of);
            builder.suggestionProviders().addProviderForAnnotation(revxrsal.commands.annotation.SuggestWith.class,
                    annotation -> annotation.value() == FloorIdSuggestionProvider.class
                            ? new FloorIdSuggestionProvider<>(java.util.List::of) : null);
            assertDoesNotThrow(() -> builder.build().register(
                    new DungeonDifficultyDebugCommand(null, () -> false, null, null)));
        } finally { MockBukkit.unmock(); }
    }
}
