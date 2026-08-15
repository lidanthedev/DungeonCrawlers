package me.lidan.dungeonCrawlers.authoring;

import me.lidan.cavecrawlers.utils.BoostedCustomConfig;
import me.lidan.dungeonCrawlers.config.BoostedConfigFactory;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ConfigSnapshot;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.EncounterCapability;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.FloorDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.Generation;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.Limits;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomType;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.TemplateRefs;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.Vector3i;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class TemplateAuthoringServiceTest {
    private Path directory;

    @BeforeEach
    void createDirectory() throws Exception {
        // BoostedYAML 1.3.7 retains input streams on Windows; Gradle output is removed after the worker exits.
        Path root = Path.of("build", "tmp", "template-authoring");
        Files.createDirectories(root);
        directory = Files.createTempDirectory(root, "case-");
        Files.createDirectories(directory.resolve("templates"));
        Files.writeString(directory.resolve("rooms.yml"), """
                schema-version: 1
                rooms:
                  start:
                    type: start
                  portal:
                    type: portal
                  boss:
                    type: boss
                  normal:
                    type: normal
                    encounters: [normal]
                    min-floor: 1
                    weight: 7
                  spare:
                    type: normal
                    encounters: [normal]
                """);
        for (String id : List.of("start", "portal", "boss", "normal", "spare")) {
            Files.write(directory.resolve("templates/" + id + ".schem"), ("old-" + id).getBytes());
        }
    }

    @Test
    void createIsNonOverwritingAndWritesRoomConfigThroughBoostedYaml() throws Exception {
        ConfigSnapshot snapshot = snapshot();
        try (MockedStatic<JavaPlugin> ignored = providingPlugin()) {
            var service = service(snapshot, 10, stage -> { });
            var created = service.create("new_room", RoomType.NORMAL, Set.of(EncounterCapability.MINIBOSS),
                    "new-schematic".getBytes());

            assertTrue(created.successful(), created.detail());
            assertArrayEquals("new-schematic".getBytes(), Files.readAllBytes(directory.resolve("templates/new_room.schem")));
            String rooms = Files.readString(directory.resolve("rooms.yml"));
            assertTrue(rooms.contains("new_room:"), rooms);
            assertTrue(rooms.contains("miniboss"), rooms);

            var duplicate = service.create("new_room", RoomType.NORMAL, Set.of(EncounterCapability.NORMAL),
                    "replacement".getBytes());
            assertFalse(duplicate.successful());
            assertArrayEquals("new-schematic".getBytes(), Files.readAllBytes(directory.resolve("templates/new_room.schem")));
        }
    }

    @Test
    void updatePreservesGenerationMetadataAndRetainsBoundedBackups() throws Exception {
        try (MockedStatic<JavaPlugin> ignored = providingPlugin()) {
            var service = service(snapshot(), 2, stage -> { });
            assertTrue(service.update("normal", "version-1".getBytes()).successful());
            assertTrue(service.update("normal", "version-2".getBytes()).successful());
            assertTrue(service.update("normal", "version-3".getBytes()).successful());

            String rooms = Files.readString(directory.resolve("rooms.yml"));
            assertTrue(rooms.contains("weight: 7"), rooms);
            assertTrue(rooms.contains("min-floor: 1"), rooms);
            assertArrayEquals("version-3".getBytes(), Files.readAllBytes(directory.resolve("templates/normal.schem")));
            try (var backups = Files.list(directory.resolve("backups/authoring"))) {
                assertEquals(2, backups.filter(Files::isDirectory).count());
            }
        }
    }

    @Test
    void deleteRejectsReferencesAndArchivesUnreferencedContent() throws Exception {
        try (MockedStatic<JavaPlugin> ignored = providingPlugin()) {
            var service = service(snapshot(), 10, stage -> { });
            Files.writeString(directory.resolve("templates/spare.meta.yml"), "schema-version: 1\n");
            var referenced = service.delete("start", Set.of());
            var active = service.delete("spare", Set.of("spare"));
            var deleted = service.delete("spare", Set.of());

            assertFalse(referenced.successful());
            assertTrue(referenced.detail().contains("templates.start"), referenced.detail());
            assertFalse(active.successful());
            assertTrue(deleted.successful(), deleted.detail());
            assertFalse(Files.exists(directory.resolve("templates/spare.schem")));
            assertFalse(Files.exists(directory.resolve("templates/spare.meta.yml")));
            assertFalse(Files.readString(directory.resolve("rooms.yml")).contains("spare:"));
            try (var backups = Files.walk(directory.resolve("backups/authoring"))) {
                var backupNames = backups.map(path -> path.getFileName().toString()).toList();
                assertTrue(backupNames.contains("spare.schem"));
                assertTrue(backupNames.contains("spare.meta.yml"));
            }
        }
    }

    @Test
    void injectedPreCommitFailureLeavesFilesUnchanged() throws Exception {
        byte[] rooms = Files.readAllBytes(directory.resolve("rooms.yml"));
        byte[] schematic = Files.readAllBytes(directory.resolve("templates/normal.schem"));
        try (MockedStatic<JavaPlugin> ignored = providingPlugin()) {
            var service = service(snapshot(), 10, stage -> { throw new IllegalStateException("injected"); });
            var result = service.create("new_room", RoomType.NORMAL, Set.of(EncounterCapability.NORMAL),
                    "new".getBytes());

            assertFalse(result.successful());
            assertArrayEquals(rooms, Files.readAllBytes(directory.resolve("rooms.yml")));
            assertFalse(Files.exists(directory.resolve("templates/new_room.schem")));
            assertArrayEquals(schematic, Files.readAllBytes(directory.resolve("templates/normal.schem")));
        }
    }

    private TemplateAuthoringService service(ConfigSnapshot snapshot, int retention,
                                             TemplateAuthoringService.FailureInjector failures) {
        return new TemplateAuthoringService(directory, new BoostedConfigFactory(), () -> snapshot,
                Clock.fixed(Instant.parse("2026-08-14T10:00:00Z"), ZoneOffset.UTC), retention, failures);
    }

    private static ConfigSnapshot snapshot() {
        Map<String, RoomDefinition> rooms = new LinkedHashMap<>();
        rooms.put("start", room("start", RoomType.START, Set.of(), 1));
        rooms.put("portal", room("portal", RoomType.PORTAL, Set.of(), 1));
        rooms.put("boss", room("boss", RoomType.BOSS, Set.of(), 1));
        rooms.put("normal", room("normal", RoomType.NORMAL, Set.of(EncounterCapability.NORMAL), 7));
        rooms.put("spare", room("spare", RoomType.NORMAL, Set.of(EncounterCapability.NORMAL), 1));
        FloorDefinition floor = new FloorDefinition("floor_1", 1, "Floor I",
                new TemplateRefs("start", "portal", "boss", new Vector3i(0, 0, 300)),
                new Generation(1, 0, false, 64, 1), List.of(), List.of(), "Boss", "basic", List.of(),
                List.of(), Map.of(), new Limits(5, 512, 16_777_216, 256, 2, 1_000));
        return new ConfigSnapshot(2, Map.of("floor_1", floor), rooms, Map.of(), Map.of(), Set.of("basic"),
                "hash", Instant.EPOCH);
    }

    private static RoomDefinition room(String id, RoomType type, Set<EncounterCapability> capabilities, double weight) {
        return new RoomDefinition(id, type, capabilities, 1, null, weight);
    }

    private static MockedStatic<JavaPlugin> providingPlugin() {
        MockedStatic<JavaPlugin> mocked = mockStatic(JavaPlugin.class);
        mocked.when(() -> JavaPlugin.getProvidingPlugin(BoostedCustomConfig.class)).thenReturn(mock(JavaPlugin.class));
        return mocked;
    }
}
