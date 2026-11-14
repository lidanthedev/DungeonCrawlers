package me.lidan.dungeonCrawlers.commands;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ClassDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.StatModifiers;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DungeonClassMenuServiceTest {
    @Test
    void classLoreShowsPassivesAndCurrentLevelValuesAlongsideDropAbilities() {
        var expected = Map.of("berserker", "Dungeon kills heal 5.0%", "mage", "mana 100.0% faster",
                "tank", "protect allies within 12.0 blocks", "healer", "health 100.0% faster", "archer", "+45.0 Attack Speed");
        expected.forEach((id, text) -> {
            var definition = new ClassDefinition(id, id, Material.STONE, new StatModifiers(Map.of(), Map.of()));
            var plain = PlainTextComponentSerializer.plainText();
            String lore = DungeonClassMenuService.classLore(definition, 60, false).stream()
                    .map(plain::serialize).collect(Collectors.joining("\n"));
            assertTrue(lore.contains(text), id + " must describe its current passive");
            assertTrue(lore.contains("Dungeon skill level: 60"));
            assertTrue(lore.contains("DROP"));
            assertTrue(lore.contains("Cooldown:"));
            if (id.equals("tank")) {
                assertTrue(lore.contains("80%"));
                assertTrue(lore.contains("50% max health per hit"));
            }
            String starting = DungeonClassMenuService.classLore(definition, 0, false).stream()
                    .map(plain::serialize).collect(Collectors.joining("\n"));
            assertNotEquals(lore, starting);
        });
    }
}
