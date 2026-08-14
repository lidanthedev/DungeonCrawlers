package me.lidan.dungeonCrawlers.config;

import me.lidan.cavecrawlers.boostedyaml.YamlDocument;
import me.lidan.cavecrawlers.boostedyaml.settings.loader.LoaderSettings;
import me.lidan.cavecrawlers.boostedyaml.settings.updater.UpdaterSettings;
import me.lidan.cavecrawlers.utils.BasicDefaultVersioning;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BoostedConfigMigrationTest {
    @TempDir Path directory;

    @Test
    void versionOneMainConfigReceivesVersionTwoDefaultsWithoutLosingValues() throws Exception {
        Path configFile = directory.resolve("config.yml");
        Files.writeString(configFile, """
                schema-version: 1
                fallback-spawn-world: custom_world
                compatibility:
                  economy-test-account-uuid: ""
                """);
        BasicDefaultVersioning oldVersioning = new BasicDefaultVersioning(
                BoostedConfigFactory.VERSION_ROUTE, 1);
        LoaderSettings loader = LoaderSettings.builder()
                .setCreateFileIfAbsent(false)
                .setAutoUpdate(false)
                .setAllowDuplicateKeys(false)
                .setDetailedErrors(true)
                .build();
        UpdaterSettings oldUpdater = updater(oldVersioning);
        YamlDocument config;
        try (InputStream document = Files.newInputStream(configFile);
             InputStream defaults = oldVersioning.getVirtualDefaults()) {
            config = YamlDocument.create(document, defaults, loader, oldUpdater);
        }

        BasicDefaultVersioning newVersioning = new BasicDefaultVersioning(
                BoostedConfigFactory.VERSION_ROUTE, BoostedConfigFactory.CURRENT_SCHEMA_VERSION);
        try (InputStream defaults = BoostedConfigMigrationTest.class.getClassLoader()
                .getResourceAsStream("config.yml")) {
            assertNotNull(defaults);
            config.update(defaults, updater(newVersioning));
        }
        assertAll(
                () -> assertEquals(2, BoostedConfigFactory.schemaVersion(config), config.dump()),
                () -> assertEquals(10, config.getInt("backups.retention-count"), config.dump()),
                () -> assertEquals("custom_world", config.getString("fallback-spawn-world"), config.dump()));
    }

    @Test
    void schemaVersionRejectsFractionalAndOutOfRangeNumbers() {
        var config = mock(me.lidan.cavecrawlers.boostedyaml.block.implementation.Section.class);
        when(config.get(BoostedConfigFactory.VERSION_ROUTE)).thenReturn(1.5, (long) Integer.MAX_VALUE + 1);

        assertEquals(-1, BoostedConfigFactory.schemaVersion(config));
        assertEquals(-1, BoostedConfigFactory.schemaVersion(config));
    }

    private static UpdaterSettings updater(BasicDefaultVersioning versioning) {
        return UpdaterSettings.builder()
                .setVersioning(versioning)
                .setKeepAll(true)
                .setOptionSorting(UpdaterSettings.OptionSorting.NONE)
                .setAutoSave(false)
                .build();
    }
}
