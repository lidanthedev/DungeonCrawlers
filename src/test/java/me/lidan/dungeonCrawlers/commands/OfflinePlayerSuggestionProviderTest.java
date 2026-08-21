package me.lidan.dungeonCrawlers.commands;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class OfflinePlayerSuggestionProviderTest {
    @Test
    void returnsDistinctSortedKnownPlayerNames() {
        OfflinePlayerSuggestionProvider<?> provider = new OfflinePlayerSuggestionProvider<>(
                () -> Arrays.asList("zeta", "LidanTheGamer", "", null, "lidanTheGamer", "alpha"));

        assertEquals(List.of("alpha", "LidanTheGamer", "lidanTheGamer", "zeta"),
                provider.getSuggestions(null).stream().toList());
    }
}
