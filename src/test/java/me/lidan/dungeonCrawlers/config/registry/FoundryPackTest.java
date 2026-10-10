package me.lidan.dungeonCrawlers.config.registry;

import me.lidan.dungeonCrawlers.config.BoostedConfigFactory;
import me.lidan.dungeonCrawlers.config.FoundryPack;
import me.lidan.dungeonCrawlers.core.encounter.ChainboundEncounter.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class FoundryPackTest {
    @TempDir Path directory;
    private void defaults() throws Exception {
        Files.createDirectories(directory.resolve("floors"));
        for (String resource : new String[]{"classes.yml", "blessings.yml", "rooms.yml", "rooms_foundry.yml",
                "foundry.yml", "difficulties.yml", "floors/floor_1.yml", "floors/floor_3.yml"})
            try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input, resource); Files.copy(input, directory.resolve(resource));
            }
    }
    @Test void completeFloorRegistersWithIsolatedPoolAndShippedSchematics() throws Exception {
        defaults();
        var loaded = new ConfigLoader(new EncounterRegistry()).load(directory);
        assertTrue(loaded.successful(), loaded.errors().toString());
        var floor = loaded.snapshot().floors().get("floor_3");
        assertEquals("chainbound", floor.encounterId());
        assertEquals(7, floor.generation().roomPool().size());
        assertTrue(loaded.snapshot().floors().get("floor_1").generation().roomPool().isEmpty());
        for (String id : FoundryPack.ROOMS) {
            assertTrue(loaded.snapshot().rooms().containsKey(id));
            try (var input = getClass().getClassLoader().getResourceAsStream("foundry/templates/" + id + ".schem")) {
                assertNotNull(input, id);
                assertTrue(new java.util.zip.GZIPInputStream(input).readAllBytes().length > 1000);
            }
        }
    }
    @Test void badPoolAndUnsafeBossSettingsAreRejectedAndSettingsAffectConfigHash() throws Exception {
        defaults();
        var loader = new ConfigLoader(new EncounterRegistry());
        String hash = loader.load(directory).snapshot().hash();
        Path settings = directory.resolve("foundry.yml");
        Files.writeString(settings, Files.readString(settings).replace("damage: 3500000", "damage: 4000000"));
        assertNotEquals(hash, loader.load(directory).snapshot().hash());
        Files.writeString(settings, Files.readString(settings).replace("final-threshold: 0.18", "final-threshold: 1.0"));
        assertFalse(loader.load(directory).successful());
        Path floor = directory.resolve("floors/floor_3.yml");
        Files.writeString(floor, Files.readString(floor).replace("room-pool: [foundry_resonance", "room-pool: [missing_room"));
        assertTrue(loader.load(directory).errors().stream().anyMatch(error -> error.contains("missing_room")));
    }
    @Test void schemaThreeMigrationPreservesEditsAndAddsOnlyAnUnrestrictedPool() throws Exception {
        Files.createDirectories(directory.resolve("floors"));
        Path file = directory.resolve("floors/custom.yml");
        Files.writeString(file, "schema-version: 3\nnumber: 3\nboss: { mob: AdminBoss }\ngeneration: { rooms: 12 }\n");
        var factory = new BoostedConfigFactory(); factory.migrateFloor(file);
        var config = factory.open(file);
        assertEquals(4, BoostedConfigFactory.schemaVersion(config));
        assertEquals("AdminBoss", config.getString("boss.mob"));
        assertEquals(12, config.getInt("generation.rooms"));
        assertTrue(config.getList("generation.room-pool").isEmpty());
        assertTrue(Files.isRegularFile(directory.resolve("backups/floors-v3/custom.yml")));
    }
    @Test void schemaOneKeepsAdministratorTuningWhileIgnoringTheRetiredThreshold() throws Exception {
        Path file = directory.resolve("foundry.yml");
        String defaults;
        try (var input = getClass().getResourceAsStream("/foundry.yml")) {
            defaults = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        Files.writeString(file, defaults);
        assertEquals(Settings.defaults(), FoundryPack.load(new TestBoostedConfigFactory(), file));
        String legacy = defaults.replace("schema-version: 2", "schema-version: 1")
                .replace("health: 200000000", "health: 345000000")
                .replace("boss:\n", "boss:\n  transform-threshold: 0.65\n");
        Files.writeString(file, legacy);
        var settings = FoundryPack.load(new TestBoostedConfigFactory(), file);
        assertEquals(345_000_000, settings.health());
        assertEquals(Settings.defaults().transformMillis(), settings.transformMillis());
        assertEquals(Settings.defaults().finalThreshold(), settings.finalThreshold());
        assertEquals(legacy, Files.readString(file));
        Files.writeString(file, legacy.replace("schema-version: 1", "schema-version: 3"));
        assertThrows(java.io.IOException.class, () -> FoundryPack.load(new TestBoostedConfigFactory(), file));
    }
}
