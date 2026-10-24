package me.lidan.dungeonCrawlers.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonAuthoringCommandTest {
    @Test
    void markerLegendNamesEveryMarkerBlockAndJigsawIdentity() {
        var legend = DungeonAuthoringCommand.markerLegend();

        for (String entry : new String[]{"- Entrance: JIGSAW named dungeoncrawlers:entrance (NORMAL/MINIBOSS/PORTAL)",
                "- Exit/door: JIGSAW named dungeoncrawlers:exit", "- Normal mob: GRAY_CONCRETE_POWDER",
                "- Miniboss mob: YELLOW_CONCRETE_POWDER", "- Player spawn/teleport: EMERALD_BLOCK",
                "- Class Selector NPC: ORANGE_CONCRETE_POWDER (START only; maximum 1)",
                "- Boss spawn: RED_CONCRETE_POWDER", "- Reward chest: LIME_CONCRETE_POWDER",
                "- Secret/blessing: CHEST; standard secret: TRAPPED_CHEST",
                "- Portal trigger: connected NETHER_PORTAL blocks"}) {
            assertTrue(legend.contains(entry), () -> "missing marker legend entry: " + entry);
        }
    }
}
