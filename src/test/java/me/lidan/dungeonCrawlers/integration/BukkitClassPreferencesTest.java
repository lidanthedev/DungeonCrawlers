package me.lidan.dungeonCrawlers.integration;

import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BukkitClassPreferencesTest {
    @Test
    void preferenceLivesInPlayerDataAcrossAdapterRecreation() {
        var server = MockBukkit.mock();
        try {
            var plugin = MockBukkit.createMockPlugin("DungeonCrawlers");
            var player = server.addPlayer();
            var preferences = new BukkitClassPreferences(plugin);
            assertTrue(preferences.read(player.getUniqueId()).isEmpty());
            preferences.write(player.getUniqueId(), "tank");
            assertEquals("tank", new BukkitClassPreferences(plugin).read(player.getUniqueId()).orElseThrow());
            preferences.write(player.getUniqueId(), "mage");
            assertEquals("mage", preferences.read(player.getUniqueId()).orElseThrow());
            assertTrue(preferences.read(java.util.UUID.randomUUID()).isEmpty());
        } finally {
            MockBukkit.unmock();
        }
    }
}
