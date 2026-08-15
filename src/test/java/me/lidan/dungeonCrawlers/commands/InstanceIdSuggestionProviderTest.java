package me.lidan.dungeonCrawlers.commands;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class InstanceIdSuggestionProviderTest {
    @AfterEach
    void resetSource() {
        InstanceIdSuggestionProvider.setSource(List::of);
    }

    @Test
    void returnsSortedActiveInstanceIds() {
        UUID first = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000000");
        InstanceIdSuggestionProvider.setSource(() -> List.of(first.toString(), second.toString()));

        assertEquals(List.of(second.toString(), first.toString()),
                new InstanceIdSuggestionProvider().getSuggestions(null).stream().toList());
    }
}
