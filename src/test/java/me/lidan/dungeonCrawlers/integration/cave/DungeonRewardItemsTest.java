package me.lidan.dungeonCrawlers.integration.cave;

import me.lidan.cavecrawlers.items.ItemInfo;
import me.lidan.cavecrawlers.items.ItemsManager;
import me.lidan.cavecrawlers.stats.StatType;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DungeonRewardItemsTest {
    @Test
    void nativeGearAndSellPricesPersistOnceAndPreserveAdministratorEdits() {
        MockBukkit.mock();
        try {
            var plugin = mock(JavaPlugin.class);
            when(plugin.getResource("reward-items.yml")).thenAnswer(call -> getClass().getResourceAsStream("/reward-items.yml"));
            var items = mock(ItemsManager.class);
            Map<String, ItemInfo> registry = new HashMap<>();
            when(items.getItemByID(anyString())).thenAnswer(call -> registry.get(call.getArgument(0)));
            doAnswer(call -> registry.put(call.getArgument(0), call.getArgument(1)))
                    .when(items).setItem(anyString(), any());
            var prices = new YamlConfiguration();
            prices.set("prices.WITHER_SWORD", 640000);
            assertTrue(DungeonRewardItems.register(plugin, items, prices));
            assertEquals(9, registry.size());
            assertEquals(64000, prices.getDouble("prices.DC_JESTER_COAT"));
            assertEquals(320000, prices.getDouble("prices.DC_RINGMASTER_CANE"));
            assertEquals(640000, prices.getDouble("prices.WITHER_SWORD"));
            assertEquals(2000, registry.get("DC_ENCORE_SCYTHE").getStats().get(StatType.DAMAGE).getValue());
            assertTrue(registry.get("DC_RINGMASTER_CANE").getAbilityID().startsWith("BASIC_LASER{"));
            for (var item : registry.values()) {
                assertNotEquals(Material.ENCHANTED_BOOK, item.getBaseItem().getType());
                assertNull(item.getDescription());
                if (item.getType() == me.lidan.cavecrawlers.items.ItemType.ARMOR) {
                    var minimums = Map.of(StatType.HEALTH, 3600, StatType.DEFENSE, 3600,
                            StatType.STRENGTH, 700, StatType.INTELLIGENCE, 1000,
                            StatType.CRIT_DAMAGE, 50, StatType.CRIT_CHANCE, 20,
                            StatType.ABILITY_DAMAGE, 40, StatType.MAGIC_FIND, 25);
                    minimums.forEach((stat, minimum) ->
                            assertTrue(item.getStats().get(stat).getValue() >= minimum, item.getName() + " " + stat));
                }
            }
            assertEquals(1200, registry.get("DC_RINGMASTER_MASK").getStats().get(StatType.INTELLIGENCE).getValue());
            assertEquals(50, registry.get("DC_RINGMASTER_MASK").getStats().get(StatType.ABILITY_DAMAGE).getValue());
            assertEquals(1200, registry.get("DC_RINGMASTER_CANE").getStats().get(StatType.INTELLIGENCE).getValue());
            assertEquals(40, registry.get("DC_RINGMASTER_CANE").getStats().get(StatType.ABILITY_DAMAGE).getValue());
            assertEquals(200, registry.get("DC_ACROBAT_BOOTS").getStats().get(StatType.SPEED).getValue());
            String bowAbility = registry.get("DC_CARDTHROWER").getAbilityID();
            var bowSettings = com.google.gson.JsonParser.parseString(bowAbility.substring(bowAbility.indexOf('{'))).getAsJsonObject();
            assertEquals(3, bowSettings.get("amount").getAsInt());
            assertEquals(3, bowSettings.get("yawDiff").getAsInt());
            assertEquals(5, bowSettings.get("maxPower").getAsInt());
            assertEquals("", bowSettings.get("description").getAsString());
            registry.get("DC_JESTER_CAP").setName("Admin cap");
            prices.set("prices.DC_JESTER_CAP", 123);
            assertFalse(DungeonRewardItems.register(plugin, items, prices));
            assertEquals("Admin cap", registry.get("DC_JESTER_CAP").getName());
            assertEquals(123, prices.getDouble("prices.DC_JESTER_CAP"));
            verify(items, times(9)).setItem(anyString(), any());
        } finally {
            MockBukkit.unmock();
        }
    }
}
