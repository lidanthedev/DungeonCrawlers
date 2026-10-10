package me.lidan.dungeonCrawlers.config.registry;

import me.lidan.dungeonCrawlers.config.BoostedConfigFactory;
import me.lidan.dungeonCrawlers.config.FoundryPack;
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
        Files.writeString(settings, Files.readString(settings).replace("damage: 650000", "damage: 700000"));
        assertNotEquals(hash, loader.load(directory).snapshot().hash());
        Files.writeString(settings, Files.readString(settings).replace("final-threshold: 0.18", "final-threshold: 0.8"));
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
}
