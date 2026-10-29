package me.lidan.dungeonCrawlers.core.generation;

import me.lidan.dungeonCrawlers.authoring.TemplateAuthoringService;
import me.lidan.dungeonCrawlers.authoring.TemplateCatalogLoader;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.*;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GenerationPreparationProviderTest {
    @Test void previewLoadsOnlySelectedRoomAndUsesItsSchematicInAllocatedSlot() throws Exception {
        var loader = mock(TemplateCatalogLoader.class);
        var authoring = mock(TemplateAuthoringService.class);
        var room = mock(RoomDefinition.class);
        var snapshot = new ConfigSnapshot(1, Map.of(), Map.of("boss", room, "broken", mock(RoomDefinition.class)),
                Map.of(), Map.of(), Set.of(), "config", Instant.EPOCH);
        var bounds = new Bounds(new Point(-2, 0, -2), new Point(2, 6, 2));
        var template = new Template("boss", RoomType.BOSS, Set.of(), bounds, Optional.empty(), Optional.empty(),
                List.of(), List.of(), List.of(), Optional.of(new Point(0, 1, 0)), Optional.empty(),
                List.of(), Set.of(), Set.of(), "content");
        when(loader.load(any())).thenAnswer(invocation -> {
            ConfigSnapshot selected = invocation.getArgument(0);
            assertEquals(Set.of("boss"), selected.rooms().keySet());
            return new TemplateCatalogLoader.LoadResult(Optional.of(Map.of("boss", new LayoutPlanner.CatalogEntry(room, template))), List.of());
        });
        when(authoring.schematic("boss")).thenReturn(new byte[]{1, 2, 3});
        UUID id = UUID.randomUUID();
        var origin = new Point(100, 64, 100);
        var slot = new SlotAllocator.SlotLease(0, id, SlotAllocator.SlotState.ALLOCATED, origin,
                new Bounds(new Point(0, -64, 0), new Point(200, 319, 200)));
        var prepared = new GenerationPreparationProvider(loader, authoring, new LayoutPlanner())
                .preparePreview(id, snapshot, slot, "boss");
        assertEquals(Set.of("boss"), prepared.schematics().keySet());
        assertEquals(bounds.translate(origin), prepared.plan().placements().getFirst().bounds());
        verify(authoring, never()).schematic("broken");
    }
}
