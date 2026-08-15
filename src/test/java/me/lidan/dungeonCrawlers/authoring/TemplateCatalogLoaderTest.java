package me.lidan.dungeonCrawlers.authoring;

import me.lidan.cavecrawlers.utils.BoostedCustomConfig;
import me.lidan.dungeonCrawlers.config.BoostedConfigFactory;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.EncounterCapability;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.FloorDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.Generation;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.Limits;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomType;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.TemplateRefs;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.Vector3i;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ConfigSnapshot;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Connector;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.ConnectorKind;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.EmeraldPolicy;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Facing;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Template;
import me.lidan.dungeonCrawlers.core.template.TemplateValidator;
import me.lidan.dungeonCrawlers.integration.WorldEditGateway;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TemplateCatalogLoaderTest {
    @Test
    void usesPersistedMetadataWithoutReadingSchematicBlocks() throws Exception {
        Path directory = Files.createTempDirectory("template-catalog-");
        Files.createDirectories(directory.resolve("templates"));
        Files.writeString(directory.resolve("rooms.yml"), "schema-version: 1\nrooms: {}\n");
        Path schematic = directory.resolve("templates/room.schem");
        Files.write(schematic, new byte[]{1, 2, 3});

        Template template = template();
        try (MockedStatic<JavaPlugin> ignored = mockStatic(JavaPlugin.class)) {
            ignored.when(() -> JavaPlugin.getProvidingPlugin(BoostedCustomConfig.class)).thenReturn(mock(JavaPlugin.class));
            BoostedConfigFactory factory = new BoostedConfigFactory();
            TemplateMetadata.write(factory, directory.resolve("templates/room.meta.yml"),
                    TemplateMetadata.fromTemplate(template, schematic));
            var snapshot = snapshot();
            var authoring = new TemplateAuthoringService(directory, new BoostedConfigFactory(), () -> snapshot, 2);
            WorldEditGateway worldEdit = mock(WorldEditGateway.class);
            var result = new TemplateCatalogLoader(authoring, worldEdit, new TemplateValidator(), EmeraldPolicy.REPLACE)
                    .load(snapshot);

            assertTrue(result.successful(), result.errors().toString());
            verify(worldEdit, never()).read(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.anyLong());
        }
    }

    private static Template template() {
        return new Template("room", RoomType.NORMAL, Set.of(EncounterCapability.NORMAL),
                new Bounds(new Point(0, 0, 0), new Point(4, 4, 4)),
                Optional.of(new Connector(ConnectorKind.ENTRANCE, new Point(0, 2, 2), Facing.WEST)),
                Optional.of(new Connector(ConnectorKind.EXIT, new Point(4, 2, 2), Facing.EAST)),
                List.of(new Point(2, 1, 2)), List.of(), List.of(), Optional.empty(), Optional.empty(),
                List.of(), Set.of(), Set.of(), "hash");
    }

    private static ConfigSnapshot snapshot() {
        Map<String, RoomDefinition> rooms = new LinkedHashMap<>();
        rooms.put("room", new RoomDefinition("room", RoomType.NORMAL, Set.of(EncounterCapability.NORMAL), 1, null, 1));
        FloorDefinition floor = new FloorDefinition("floor_1", 1, "Floor I",
                new TemplateRefs("room", "room", "room", new Vector3i(0, 0, 0)),
                new Generation(1, 0, false, 1, 0), List.of(), List.of(), "Boss", "basic", List.of(),
                List.of(), Map.of(), new Limits(1, 512, 16_777_216, 256, 2, 1_000));
        return new ConfigSnapshot(1, Map.of("floor_1", floor), rooms, Map.of(), Map.of(), Set.of("basic"),
                "hash", Instant.EPOCH);
    }
}
