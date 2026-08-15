package me.lidan.dungeonCrawlers.commands;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RoomIdSuggestionProviderTest {
    @Test
    void returnsSortedConfiguredRoomIds() {
        RoomIdSuggestionProvider<?> provider =
                new RoomIdSuggestionProvider<>(() -> List.of("zombie_room", "dungeon_start", "dungeon_portal"));

        assertEquals(List.of("dungeon_portal", "dungeon_start", "zombie_room"),
                provider.getSuggestions(null).stream().toList());
    }
}
