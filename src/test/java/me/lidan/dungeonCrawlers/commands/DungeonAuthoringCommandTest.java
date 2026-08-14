package me.lidan.dungeonCrawlers.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonAuthoringCommandTest {
    @Test
    void markerLegendNamesEveryMarkerBlockAndJigsawIdentity() {
        String legend = String.join("\n", DungeonAuthoringCommand.markerLegend());

        for (String marker : new String[]{"GRAY_CONCRETE_POWDER", "YELLOW_CONCRETE_POWDER", "EMERALD_BLOCK",
                "RED_CONCRETE_POWDER", "LIME_CONCRETE_POWDER", "CHEST", "TRAPPED_CHEST", "NETHER_PORTAL",
                "dungeoncrawlers:entrance", "dungeoncrawlers:exit"}) {
            assertTrue(legend.contains(marker), () -> "missing marker from legend: " + marker);
        }
    }
}
