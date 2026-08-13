package me.lidan.dungeonCrawlers.compatibility;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PluginMetadataTest {
    @Test
    void declaresExactRequiredAndOptionalPluginEdges() {
        var stream = getClass().getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(stream);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));

        assertEquals(List.of("CaveCrawlers", "FastAsyncWorldEdit", "MythicMobs", "Vault"), yaml.getStringList("depend"));
        assertEquals(List.of("Parties", "Essentials"), yaml.getStringList("softdepend"));
        assertEquals("1.21", yaml.getString("api-version"));
        assertFalse(yaml.getStringList("libraries").stream().anyMatch(value -> value.contains("CaveCrawlers")));
    }
}

