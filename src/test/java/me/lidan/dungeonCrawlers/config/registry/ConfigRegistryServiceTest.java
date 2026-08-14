package me.lidan.dungeonCrawlers.config.registry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigRegistryServiceTest {
    @TempDir Path directory;

    @Test
    void failedReloadAndActiveReservationPreserveSnapshot() throws Exception {
        copyDefaults();
        ConfigRegistryService service = service();
        assertTrue(service.initialize().swapped());
        var original = service.snapshot();

        assertFalse(service.reload(1).swapped());
        assertEquals(original, service.snapshot());

        Files.writeString(directory.resolve("classes.yml"), "schema-version: 1\nclasses: null\n");
        assertFalse(service.reload(0).swapped());
        assertEquals(original, service.snapshot());
    }

    @Test
    void successfulReloadCreatesBackupAndAtomicallySwaps() throws Exception {
        copyDefaults();
        ConfigRegistryService service = service();
        assertTrue(service.initialize().swapped());
        String originalHash = service.snapshot().hash();
        Path floor = directory.resolve("floors/floor_1.yml");
        Files.writeString(floor, Files.readString(floor).replace("Floor I", "First Floor"));

        assertTrue(service.reload(0).swapped());
        assertNotEquals(originalHash, service.snapshot().hash());
        try (var backups = Files.list(directory.resolve("backups"))) {
            Path backup = backups.findFirst().orElseThrow();
            assertTrue(Files.readString(backup.resolve("floors/floor_1.yml")).contains("Floor I"));
            assertFalse(Files.readString(backup.resolve("floors/floor_1.yml")).contains("First Floor"));
        }
    }

    @Test
    void reloadBackupRetentionDeletesOnlyOldReloadSnapshots() throws Exception {
        copyDefaults();
        ConfigRegistryService service = service(2);
        assertTrue(service.initialize().swapped());
        Path migrationBackup = directory.resolve("backups/config-migrations/config-schema-v0.yml");
        Files.createDirectories(migrationBackup.getParent());
        Files.writeString(migrationBackup, "legacy");
        Path floor = directory.resolve("floors/floor_1.yml");
        for (int version = 1; version <= 3; version++) {
            String content = Files.readString(floor).replaceAll("display-name: .*", "display-name: \"Floor " + version + "\"");
            Files.writeString(floor, content);
            assertTrue(service.reload(0).swapped());
        }

        try (var backups = Files.list(directory.resolve("backups"))) {
            assertEquals(2, backups.filter(path -> path.getFileName().toString().matches(
                    "config-\\d{8}-\\d{6}-\\d{3}(?:-\\d+)?")).count());
        }
        assertEquals("legacy", Files.readString(migrationBackup));
    }

    @Test
    void rejectsRetentionOutsideSupportedRange() {
        assertThrows(IllegalArgumentException.class, () -> service(0));
        assertThrows(IllegalArgumentException.class, () -> service(1_001));
    }

    @Test
    void reloadRejectsInvalidCountAndUninitializedRegistry() {
        ConfigRegistryService service = service();
        assertThrows(IllegalArgumentException.class, () -> service.reload(-1));
        assertThrows(IllegalStateException.class, () -> service.reload(0));
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

    private ConfigRegistryService service() {
        return service(ConfigRegistryService.DEFAULT_BACKUP_RETENTION);
    }

    private ConfigRegistryService service(int retention) {
        ConfigLoader loader = new ConfigLoader(new EncounterRegistry(), java.time.Clock.systemUTC(),
                new TestBoostedConfigFactory());
        return new ConfigRegistryService(directory, loader, java.time.Clock.systemUTC(), retention);
    }
}
