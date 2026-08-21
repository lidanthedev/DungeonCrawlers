package me.lidan.dungeonCrawlers.commands;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class InstanceIdSuggestionProviderTest {
    @Test
    void returnsSortedActiveInstanceIds() {
        UUID first = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000000");
        InstanceIdSuggestionProvider<?> provider =
                new InstanceIdSuggestionProvider<>(() -> List.of(first.toString(), second.toString()));

        assertEquals(List.of(second.toString(), first.toString(), "this"),
                provider.getSuggestions(null).stream().toList());
    }

    @Test
    void providersKeepIndependentSources() {
        InstanceIdSuggestionProvider<?> first = new InstanceIdSuggestionProvider<>(() -> List.of("first"));
        InstanceIdSuggestionProvider<?> second = new InstanceIdSuggestionProvider<>(() -> List.of("second"));

        assertEquals(List.of("first", "this"), first.getSuggestions(null).stream().toList());
        assertEquals(List.of("second", "this"), second.getSuggestions(null).stream().toList());
    }
}
