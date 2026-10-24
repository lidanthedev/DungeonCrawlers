package me.lidan.dungeonCrawlers.core.template;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomMarkerTest {
    @Test
    void canonicalMarkersHaveUniqueIdsAndRecognizeTheirBlockTypes() {
        assertEquals(RoomMarker.values().length,
                RoomMarker.ordered().stream().map(RoomMarker::id).collect(Collectors.toSet()).size());
        for (RoomMarker marker : RoomMarker.values()) {
            assertTrue(RoomMarker.isAuthoringMarker(marker.blockType()), marker.id());
            assertEquals(marker, RoomMarker.byId(marker.id()).orElseThrow());
        }
        assertTrue(RoomMarker.CLASS_SELECTOR_NPC.matches("ORANGE_CONCRETE_POWDER"));
        assertEquals(Set.of(RoomMarker.values()), Set.copyOf(RoomMarker.ordered()));
    }
}
