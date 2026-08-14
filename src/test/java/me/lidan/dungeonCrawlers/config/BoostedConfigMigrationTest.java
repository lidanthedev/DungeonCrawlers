package me.lidan.dungeonCrawlers.config;

import me.lidan.cavecrawlers.utils.BoostedCustomConfig;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BoostedConfigMigrationTest {
    Path directory;

    @BeforeEach
    void createDirectory() throws Exception {
        // BoostedYAML 1.3.7 retains its file input stream on Windows. Keep these
        // files in Gradle's disposable output until the test worker releases it.
        Path testRoot = Path.of("build", "tmp", "boosted-config-migration");
        Files.createDirectories(testRoot);
        directory = Files.createTempDirectory(testRoot, "case-");
    }

    @Test
    void versionOneMainConfigReceivesVersionTwoDefaultsWithoutLosingValues() throws Exception {
        Path configFile = directory.resolve("config.yml");
        Files.writeString(configFile, """
                schema-version: 1
                fallback-spawn-world: custom_world
                compatibility:
                  economy-test-account-uuid: ""
                """);
        Path backups = directory.resolve("backups");

        BoostedCustomConfig config;
        BoostedCustomConfig reopened;
        try (MockedStatic<JavaPlugin> ignored = providingPlugin()) {
            config = new BoostedConfigFactory().openMainConfig(configFile, backups);
            reopened = new BoostedConfigFactory().openMainConfig(configFile, backups);
        }

        assertAll(
                () -> assertEquals(BoostedConfigFactory.CURRENT_SCHEMA_VERSION,
                        BoostedConfigFactory.schemaVersion(reopened), reopened.dump()),
                () -> assertEquals(10, reopened.getInt("backups.retention-count"), reopened.dump()),
                () -> assertEquals("replace", reopened.getString("authoring.emerald-marker-policy"), reopened.dump()),
                () -> assertEquals("custom_world", reopened.getString("fallback-spawn-world"), reopened.dump()),
                () -> assertTrue(Files.readString(configFile).contains(
                        "schema-version: " + BoostedConfigFactory.CURRENT_SCHEMA_VERSION)),
                () -> assertFalse(Files.exists(backups.resolve("config-schema-v0.yml"))),
                () -> assertEquals(BoostedConfigFactory.CURRENT_SCHEMA_VERSION,
                        BoostedConfigFactory.schemaVersion(config)));
    }

    @Test
    void unversionedMainConfigCreatesV0BackupBeforeMigration() throws Exception {
        Path configFile = directory.resolve("config.yml");
        String original = """
                fallback-spawn-world: legacy_world
                compatibility:
                  economy-test-account-uuid: ""
                """;
        Files.writeString(configFile, original);
        Path backups = directory.resolve("backups");

        BoostedCustomConfig migrated;
        try (MockedStatic<JavaPlugin> ignored = providingPlugin()) {
            migrated = new BoostedConfigFactory().openMainConfig(configFile, backups);
        }

        assertAll(
                () -> assertEquals(original, Files.readString(backups.resolve("config-schema-v0.yml"))),
                () -> assertEquals(BoostedConfigFactory.CURRENT_SCHEMA_VERSION,
                        BoostedConfigFactory.schemaVersion(migrated), migrated.dump()),
                () -> assertEquals("legacy_world", migrated.getString("fallback-spawn-world"), migrated.dump()),
                () -> assertTrue(Files.readString(configFile).contains(
                        "schema-version: " + BoostedConfigFactory.CURRENT_SCHEMA_VERSION)));
    }

    @Test
    void schemaVersionRejectsFractionalAndOutOfRangeNumbers() {
        var config = mock(me.lidan.cavecrawlers.boostedyaml.block.implementation.Section.class);
        when(config.get(BoostedConfigFactory.VERSION_ROUTE)).thenReturn(1.5, (long) Integer.MAX_VALUE + 1);

        assertEquals(-1, BoostedConfigFactory.schemaVersion(config));
        assertEquals(-1, BoostedConfigFactory.schemaVersion(config));
    }

    private static MockedStatic<JavaPlugin> providingPlugin() {
        MockedStatic<JavaPlugin> mocked = mockStatic(JavaPlugin.class);
        JavaPlugin plugin = mock(JavaPlugin.class);
        mocked.when(() -> JavaPlugin.getProvidingPlugin(BoostedCustomConfig.class)).thenReturn(plugin);
        return mocked;
    }
}
