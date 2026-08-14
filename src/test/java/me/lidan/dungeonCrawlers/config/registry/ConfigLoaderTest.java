package me.lidan.dungeonCrawlers.config.registry;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {
    @TempDir Path directory;

    @Test
    void builtInPackLoadsWithDefaultsAndStableHash() throws Exception {
        copyDefaults();
        ConfigLoader loader = loader();

        ConfigLoadResult first = loader.load(directory);
        ConfigLoadResult second = loader.load(directory);

        assertTrue(first.successful(), first.errors().toString());
        assertEquals(first.snapshot().hash(), second.snapshot().hash());
        assertEquals(RoomType.START, first.snapshot().rooms().get("dungeon_start").type());
        assertEquals(3000, first.snapshot().floors().get("floor_1").templates().bossOffset().z());
        assertEquals(1, first.snapshot().floors().get("floor_1").rewards().get("wooden").items().get(1).minimumAmount());
    }

    @Test
    void invalidStatMaterialAndMalformedMapsAreRejected() throws Exception {
        copyDefaults();
        Path classes = directory.resolve("classes.yml");
        String content = Files.readString(classes)
                .replace("icon: IRON_SWORD", "icon: NOT_A_MATERIAL")
                .replace("stat-add: { STRENGTH: 50 }", "stat-add: { MANA: 50 }")
                .replaceFirst("stat-multiply: \\{\\}", "stat-multiply: nope");
        Files.writeString(classes, content);

        ConfigLoadResult result = loader().load(directory);

        assertFalse(result.successful());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("invalid material")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("invalid value MANA")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("stat-multiply must be a map")));
    }

    @Test
    void nullStatMapIsRejectedButOmittedMapUsesDefaults() throws Exception {
        copyDefaults();
        Path classes = directory.resolve("classes.yml");
        Files.writeString(classes, Files.readString(classes).replace("stat-add: { STRENGTH: 50 }", "stat-add:"));
        ConfigLoadResult malformed = loader().load(directory);
        assertTrue(malformed.errors().stream().anyMatch(error -> error.contains("stat-add must be a map")));

        copyDefaults();
        Files.writeString(classes, Files.readString(classes).replace("    stat-multiply: {}\n", ""));
        assertTrue(loader().load(directory).successful());
    }

    @Test
    void crossReferencesTypesAndCapabilitiesFailTogether() throws Exception {
        copyDefaults();
        Path floor = directory.resolve("floors/floor_1.yml");
        Files.writeString(floor, Files.readString(floor)
                .replace("allowed: [berserker, mage, tank, healer, archer]", "allowed: [missing_class]")
                .replace("id: crypt_strength", "id: missing_blessing"));
        Path rooms = directory.resolve("rooms.yml");
        Files.writeString(rooms, Files.readString(rooms)
                .replace("dungeon_start:\n    type: start", "dungeon_start:\n    type: portal"));

        ConfigLoadResult result = loader().load(directory);

        assertTrue(result.errors().stream().anyMatch(error -> error.contains("missing class")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("missing blessing")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("requires START")));
    }

    @Test
    void unknownEncounterWarnsAndUsesRegisteredBasicFallback() throws Exception {
        copyDefaults();
        Path floor = directory.resolve("floors/floor_1.yml");
        Files.writeString(floor, Files.readString(floor).replace("encounter: basic", "encounter: future_boss"));

        ConfigLoadResult result = loader().load(directory);

        assertTrue(result.successful(), result.errors().toString());
        assertEquals("basic", result.snapshot().floors().get("floor_1").encounterId());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("future_boss")));
    }

    @Test
    void contentHashChangesWithValidContent() throws Exception {
        copyDefaults();
        ConfigLoader loader = loader();
        String before = loader.load(directory).snapshot().hash();
        Path floor = directory.resolve("floors/floor_1.yml");
        Files.writeString(floor, Files.readString(floor).replace("Floor I", "First Floor"));
        assertNotEquals(before, loader.load(directory).snapshot().hash());
    }

    private void copyDefaults() throws IOException {
        Path resources = Path.of("src/main/resources");
        for (String file : new String[]{"classes.yml", "blessings.yml", "rooms.yml"}) {
            Files.copy(resources.resolve(file), directory.resolve(file), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.createDirectories(directory.resolve("floors"));
        Files.copy(resources.resolve("floors/floor_1.yml"), directory.resolve("floors/floor_1.yml"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static ConfigLoader loader() {
        return new ConfigLoader(new EncounterRegistry(), java.time.Clock.systemUTC(),
                new TestBoostedConfigFactory());
    }
}
