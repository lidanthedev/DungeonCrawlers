package me.lidan.dungeonCrawlers.core.stats;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.BlessingDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.BlessingStacking;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.StatModifiers;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BlessingLevelsTest {
    @Test
    void replaceBlessingReportsItsEffectiveLevelAtCap() {
        BlessingDefinition blessing = new BlessingDefinition("replacement", "Replacement", Material.STONE,
                BlessingStacking.REPLACE, 5, new StatModifiers(Map.of(), Map.of()));

        BlessingLevels.DiscoveryResult result = new BlessingLevels().discover(blessing);

        assertTrue(result.atCap());
    }
}
