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
            assertEquals(600, registry.get("DC_ENCORE_SCYTHE").getStats().get(StatType.DAMAGE).getValue());
            assertTrue(registry.get("DC_RINGMASTER_CANE").getAbilityID().startsWith("BASIC_LASER{"));
            for (var item : registry.values()) {
                assertNotEquals(Material.ENCHANTED_BOOK, item.getBaseItem().getType());
                assertTrue(item.getStats().get(StatType.DAMAGE).getValue() < 700);
                assertTrue(item.getStats().get(StatType.HEALTH).getValue() < 1600);
            }
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
