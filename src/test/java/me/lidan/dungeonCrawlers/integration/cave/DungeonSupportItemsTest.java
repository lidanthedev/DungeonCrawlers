package me.lidan.dungeonCrawlers.integration.cave;

import com.google.gson.JsonObject;
import me.lidan.cavecrawlers.CaveCrawlers;
import me.lidan.cavecrawlers.items.ItemInfo;
import me.lidan.cavecrawlers.items.ItemsManager;
import me.lidan.cavecrawlers.items.abilities.AbilityManager;
import me.lidan.cavecrawlers.items.abilities.ItemAbility;
import me.lidan.cavecrawlers.stats.ActionBarManager;
import me.lidan.cavecrawlers.stats.StatType;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DungeonSupportItemsTest {
    @BeforeAll
    static void initializeNativeClassesWithPluginFixture() throws Exception {
        try (var nativePlugin = mockStatic(JavaPlugin.class)) {
            nativePlugin.when(() -> JavaPlugin.getPlugin(CaveCrawlers.class)).thenReturn(null);
            Class.forName(me.lidan.cavecrawlers.stats.StatsManager.class.getName());
            Class.forName(ActionBarManager.class.getName());
        }
    }

    @Test
    void persistsNativeItemsRoutesAbilitiesAndCleansUpReloadedItemsAndSettingsVariants() {
        MockBukkit.mock();
        try (var nativeAbilities = mockStatic(AbilityManager.class);
             var nativeItems = mockStatic(ItemsManager.class)) {
            var abilities = mock(AbilityManager.class);
            var items = mock(ItemsManager.class);
            nativeAbilities.when(AbilityManager::getInstance).thenReturn(abilities);
            nativeItems.when(ItemsManager::getInstance).thenReturn(items);
            Map<String, ItemAbility> abilityRegistry = new HashMap<>();
            Map<String, ItemInfo> itemRegistry = new HashMap<>();
            when(abilities.getAbilityMap()).thenReturn(abilityRegistry);
            when(abilities.getAbilityByID(anyString())).thenAnswer(call -> abilityRegistry.get(call.getArgument(0)));
            doAnswer(call -> abilityRegistry.put(call.getArgument(0), call.getArgument(1)))
                    .when(abilities).registerAbility(anyString(), any());
            when(items.getItemByID(anyString())).thenAnswer(call -> itemRegistry.get(call.getArgument(0)));
            doAnswer(call -> {
                ItemInfo item = call.getArgument(1);
                item.setID(call.getArgument(0));
                return itemRegistry.put(call.getArgument(0), item);
            }).when(items).setItem(anyString(), any());
            doAnswer(call -> itemRegistry.remove(call.getArgument(0))).when(items).unregisterItem(anyString());
            var plugin = mock(JavaPlugin.class);
            when(plugin.getResource("support-items.yml")).thenAnswer(call ->
                    getClass().getResourceAsStream("/support-items.yml"));
            @SuppressWarnings("unchecked")
            BiPredicate<Player, String> activate = mock(BiPredicate.class);
            var support = new DungeonSupportItems(plugin, activate);
            support.register();
            support.register();
            assertEquals(4, itemRegistry.size());
            verify(items, times(4)).setItem(anyString(), any());
            assertEquals(75, itemRegistry.get("DC_AEGIS_STANDARD").getStats().get(StatType.DEFENSE).getValue());
            assertEquals(150, itemRegistry.get("DC_DAWNLIGHT_TOME").getStats().get(StatType.INTELLIGENCE).getValue());
            assertEquals(25_000, abilityRegistry.get("dc_renewal_staff").getCooldown());
            assertEquals(0, abilityRegistry.get("dc_renewal_staff").getCost());

            var player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            var inventory = mock(PlayerInventory.class);
            when(player.getInventory()).thenReturn(inventory);
            when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.AIR));
            var event = mock(PlayerInteractEvent.class);
            when(event.getPlayer()).thenReturn(player);
            when(activate.test(player, "dc_renewal_staff")).thenReturn(true);
            var bars = mock(ActionBarManager.class);
            var cave = mock(CaveCrawlers.class);
            when(cave.getConfig()).thenReturn(mock(FileConfiguration.class));
            try (var nativeBars = mockStatic(ActionBarManager.class);
                 var nativeCave = mockStatic(CaveCrawlers.class)) {
                nativeBars.when(ActionBarManager::getInstance).thenReturn(bars);
                nativeCave.when(CaveCrawlers::getInstance).thenReturn(cave);
                abilityRegistry.get("dc_renewal_staff").activateAbility(event);
                verify(activate).test(player, "dc_renewal_staff");
                // The normal ItemAbility flow owns the cooldown: an immediate recast
                // reports on the action bar without reaching the dungeon effect.
                abilityRegistry.get("dc_renewal_staff").activateAbility(event);
                verify(activate, times(1)).test(player, "dc_renewal_staff");
                verify(bars, times(1)).showActionBar(eq(player),
                        argThat((String message) -> message.contains("Still on cooldown")));
            }

            itemRegistry.put("DC_RENEWAL_STAFF", itemRegistry.get("DC_RENEWAL_STAFF").clone());
            abilityRegistry.put("dc_renewal_staff{}",
                    abilityRegistry.get("dc_renewal_staff").buildAbilityWithSettings(new JsonObject()));
            ItemAbility replacement = mock(ItemAbility.class);
            abilityRegistry.put("dc_aegis_standard", replacement);
            support.close();
            assertEquals(Map.of("dc_aegis_standard", replacement), abilityRegistry);
            assertEquals(1, itemRegistry.size());
            assertTrue(itemRegistry.containsKey("DC_AEGIS_STANDARD"));

            abilityRegistry.clear();
            var existing = itemRegistry.get("DC_AEGIS_STANDARD");
            existing.setName("Admin's Aegis");
            support.register();
            assertSame(existing, itemRegistry.get("DC_AEGIS_STANDARD"));
            assertEquals("Admin's Aegis", itemRegistry.get("DC_AEGIS_STANDARD").getName());
            verify(items, times(1)).setItem(eq("DC_AEGIS_STANDARD"), any());
            support.close();
            assertTrue(itemRegistry.isEmpty());
            assertTrue(abilityRegistry.isEmpty());
        } finally {
            MockBukkit.unmock();
        }
    }
}
