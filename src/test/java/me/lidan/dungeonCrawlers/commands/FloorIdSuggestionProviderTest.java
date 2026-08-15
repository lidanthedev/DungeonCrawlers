package me.lidan.dungeonCrawlers.commands;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FloorIdSuggestionProviderTest {
    @Test
    void returnsSortedConfiguredFloorIds() {
        FloorIdSuggestionProvider<?> provider =
                new FloorIdSuggestionProvider<>(() -> List.of("floor_10", "floor_2", "floor_1"));

        assertEquals(List.of("floor_1", "floor_10", "floor_2"),
                provider.getSuggestions(null).stream().toList());
    }
}
