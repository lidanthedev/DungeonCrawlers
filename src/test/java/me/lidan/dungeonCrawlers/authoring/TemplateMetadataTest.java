package me.lidan.dungeonCrawlers.authoring;

import me.lidan.cavecrawlers.utils.BoostedCustomConfig;
import me.lidan.dungeonCrawlers.config.BoostedConfigFactory;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.EncounterCapability;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomType;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Connector;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.ConnectorKind;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Facing;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Secret;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.SecretKind;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Template;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class TemplateMetadataTest {
    @Test
    void roundTripsMarkerOffsetsWithoutSolidBlockPayload() throws Exception {
        Path directory = Files.createTempDirectory("template-metadata-");
        Path schematic = directory.resolve("room.schem");
        Path metadata = directory.resolve("room.meta.yml");
        Files.write(schematic, new byte[]{1, 2, 3});
        Template template = template();

        try (MockedStatic<JavaPlugin> ignored = mockStatic(JavaPlugin.class)) {
            ignored.when(() -> JavaPlugin.getProvidingPlugin(BoostedCustomConfig.class)).thenReturn(mock(JavaPlugin.class));
            BoostedConfigFactory factory = new BoostedConfigFactory();
            TemplateMetadata.write(factory, metadata, TemplateMetadata.fromTemplate(template, schematic));
            TemplateMetadata loaded = TemplateMetadata.read(factory, metadata);

            assertEquals(template.bounds(), loaded.bounds());
            assertEquals(template.normalMobs(), loaded.normalMobs());
            assertEquals(template.minibossMobs(), loaded.minibossMobs());
            assertEquals(template.entrance(), loaded.entrance());
            assertEquals(template.secrets(), loaded.secrets());
            assertTrue(loaded.toTemplate("room", room()).solidBlocks().isEmpty());
        }
    }

    private static Template template() {
        return new Template("room", RoomType.NORMAL, Set.of(EncounterCapability.NORMAL),
                new Bounds(new Point(0, 0, 0), new Point(4, 4, 4)),
                Optional.of(new Connector(ConnectorKind.ENTRANCE, new Point(0, 2, 2), Facing.WEST)),
                Optional.of(new Connector(ConnectorKind.EXIT, new Point(4, 2, 2), Facing.EAST)),
                List.of(new Point(2, 1, 2)), List.of(new Point(3, 1, 2)), List.of(), Optional.empty(),
                Optional.of(new Point(2, 1, 3)), List.of(new Secret(new Point(2, 1, 4), SecretKind.STANDARD)),
                Set.of(new Point(1, 1, 1)), Set.of(new Point(1, 1, 1)), "hash");
    }

    private static RoomDefinition room() {
        return new RoomDefinition("room", RoomType.NORMAL, Set.of(EncounterCapability.NORMAL), 1, null, 1);
    }
}
