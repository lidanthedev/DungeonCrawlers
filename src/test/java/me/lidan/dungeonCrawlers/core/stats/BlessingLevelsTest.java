package me.lidan.dungeonCrawlers.core.stats;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.BlessingDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.BlessingStacking;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.StatModifiers;
import me.lidan.cavecrawlers.utils.Range;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlessingLevelsTest {
    @Test
    void replaceBlessingReportsItsEffectiveLevelAtCap() {
        BlessingDefinition blessing = new BlessingDefinition("replacement", "Replacement", Material.STONE,
                BlessingStacking.REPLACE, 5, new StatModifiers(Map.of(), Map.of()));

        BlessingLevels.DiscoveryResult result = new BlessingLevels().discover(blessing);

        assertEquals(1, result.currentLevel());
        assertTrue(result.atCap());
    }

    @Test
    void reportsAwardedLevelsSeparatelyFromAccumulatedLevel() {
        BlessingDefinition blessing = new BlessingDefinition("levels", "Levels", Material.STONE,
                BlessingStacking.LEVELS, 10, new Range(2, 4), StatModifiers.empty());

        BlessingLevels levels = new BlessingLevels();
        BlessingLevels.DiscoveryResult first = levels.discover(blessing, 4);
        BlessingLevels.DiscoveryResult second = levels.discover(blessing, 2);

        assertEquals(4, first.levelsAwarded());
        assertEquals(4, first.currentLevel());
        assertEquals(2, second.levelsAwarded());
        assertEquals(6, second.currentLevel());
    }
}
