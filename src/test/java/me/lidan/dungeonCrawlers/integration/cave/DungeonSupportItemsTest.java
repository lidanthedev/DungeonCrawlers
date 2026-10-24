package me.lidan.dungeonCrawlers.integration.cave;

import com.google.gson.JsonObject;
import me.lidan.cavecrawlers.CaveCrawlers;
import me.lidan.cavecrawlers.items.ItemInfo;
import me.lidan.cavecrawlers.items.ItemType;
import me.lidan.cavecrawlers.items.ItemsManager;
import me.lidan.cavecrawlers.items.Rarity;
import me.lidan.cavecrawlers.items.abilities.AbilityManager;
import me.lidan.cavecrawlers.items.abilities.ItemAbility;
import me.lidan.cavecrawlers.stats.ActionBarManager;
import me.lidan.cavecrawlers.stats.StatType;
import me.lidan.cavecrawlers.stats.Stats;
import me.lidan.cavecrawlers.stats.StatsManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.HashMap;
import java.util.List;
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
            var legacyStats = new Stats();
            legacyStats.set(StatType.HEALTH, 999);
            var legacyBase = legacyTotem();
            var legacyMeta = legacyBase.getItemMeta();
            var legacyDefinition = new ItemInfo("Admin Vitality", "Admin description", legacyStats,
                    ItemType.WAND, legacyBase, Rarity.LEGENDARY, "dc_vitality_totem");
            itemRegistry.put("DC_VITALITY_TOTEM", legacyDefinition);
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
            when(plugin.getServer()).thenReturn(MockBukkit.getMock());
            when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getPluginLoader()).thenReturn(MockBukkit.createMockPlugin().getPluginLoader());
            when(plugin.getResource("support-items.yml")).thenAnswer(call ->
                    getClass().getResourceAsStream("/support-items.yml"));
            var legacyOwner = MockBukkit.getMock().addPlayer();
            legacyOwner.getInventory().setItem(0, legacyTotem());
            legacyOwner.getInventory().setItem(1, new ItemStack(Material.TOTEM_OF_UNDYING));
            @SuppressWarnings("unchecked")
            BiPredicate<Player, String> activate = mock(BiPredicate.class);
            var support = new DungeonSupportItems(plugin, activate);
            support.register();
            support.register();
            assertEquals(5, HandlerList.getRegisteredListeners(plugin).size());
            assertEquals(9, itemRegistry.size());
            verify(items, times(9)).setItem(anyString(), any());
            assertSame(legacyDefinition, itemRegistry.get("DC_VITALITY_TOTEM"));
            assertEquals("Admin Vitality", legacyDefinition.getName());
            assertEquals("Admin description", legacyDefinition.getDescription());
            assertEquals(Rarity.LEGENDARY, legacyDefinition.getRarity());
            assertEquals(999, legacyDefinition.getStats().get(StatType.HEALTH).getValue());
            assertEquals(Material.BEACON, legacyDefinition.getBaseItem().getType());
            assertEquals(legacyMeta, legacyDefinition.getBaseItem().getItemMeta());
            assertEquals(Material.BEACON, legacyOwner.getInventory().getItem(0).getType());
            assertEquals(Material.TOTEM_OF_UNDYING, legacyOwner.getInventory().getItem(1).getType());
            assertEquals(75, itemRegistry.get("DC_AEGIS_STANDARD").getStats().get(StatType.DEFENSE).getValue());
            assertEquals(150, itemRegistry.get("DC_DAWNLIGHT_TOME").getStats().get(StatType.INTELLIGENCE).getValue());
            assertEquals(25_000, abilityRegistry.get("dc_renewal_staff").getCooldown());
            assertEquals(0, abilityRegistry.get("dc_renewal_staff").getCost());
            assertEquals(8_000, abilityRegistry.get("dc_mending_wand").getCooldown());
            assertEquals(100, abilityRegistry.get("dc_mending_wand").getCost());

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

            var stats = new Stats();
            stats.set(StatType.MANA, 200);
            var manager = mock(StatsManager.class);
            when(manager.getStats(player)).thenReturn(stats);
            try (var nativeStats = mockStatic(StatsManager.class);
                 var nativeBars = mockStatic(ActionBarManager.class);
                 var nativeCave = mockStatic(CaveCrawlers.class)) {
                nativeStats.when(StatsManager::getInstance).thenReturn(manager);
                nativeBars.when(ActionBarManager::getInstance).thenReturn(bars);
                nativeCave.when(CaveCrawlers::getInstance).thenReturn(cave);
                var wand = abilityRegistry.get("dc_mending_wand");
                when(activate.test(player, "dc_mending_wand")).thenReturn(false);
                wand.activateAbility(event);
                assertEquals(200, stats.get(StatType.MANA).getValue());
                assertEquals(0, wand.getAbilityCooldown().getCooldown(player.getUniqueId()));
                when(activate.test(player, "dc_mending_wand")).thenReturn(true);
                wand.activateAbility(event);
                assertEquals(100, stats.get(StatType.MANA).getValue());
                wand.activateAbility(event);
                verify(activate, times(2)).test(player, "dc_mending_wand");
                assertEquals(100, stats.get(StatType.MANA).getValue());
            }

            itemRegistry.put("DC_RENEWAL_STAFF", itemRegistry.get("DC_RENEWAL_STAFF").clone());
            abilityRegistry.put("dc_renewal_staff{}",
                    abilityRegistry.get("dc_renewal_staff").buildAbilityWithSettings(new JsonObject()));
            ItemAbility replacement = mock(ItemAbility.class);
            abilityRegistry.put("dc_aegis_standard", replacement);
            support.close();
            assertTrue(HandlerList.getRegisteredListeners(plugin).isEmpty());
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

    @Test
    void migratesLegacyInventoryWithoutLosingMetadataAndPreventsOnlyItsVanillaResurrection() {
        MockBukkit.mock();
        try (var nativeAbilities = mockStatic(AbilityManager.class);
             var nativeItems = mockStatic(ItemsManager.class)) {
            nativeAbilities.when(AbilityManager::getInstance).thenReturn(mock(AbilityManager.class));
            nativeItems.when(ItemsManager::getInstance).thenReturn(mock(ItemsManager.class));
            var support = new DungeonSupportItems(mock(JavaPlugin.class), (player, id) -> true);
            var player = MockBukkit.getMock().addPlayer();
            var legacy = legacyTotem();
            var originalMeta = legacy.getItemMeta();
            player.getInventory().setItem(4, legacy);
            var join = mock(PlayerJoinEvent.class);
            when(join.getPlayer()).thenReturn(player);
            support.onJoin(join);
            var converted = player.getInventory().getItem(4);
            assertEquals(Material.BEACON, converted.getType());
            assertEquals(2, converted.getAmount());
            assertEquals(originalMeta, converted.getItemMeta());

            // A legacy copy retrieved from a chest after startup cannot trigger vanilla resurrection.
            player.getInventory().setItemInOffHand(legacyTotem());
            var resurrection = new EntityResurrectEvent(player, EquipmentSlot.OFF_HAND);
            support.onResurrect(resurrection);
            assertTrue(resurrection.isCancelled());
            assertEquals(Material.BEACON, player.getInventory().getItemInOffHand().getType());
            assertEquals(originalMeta, player.getInventory().getItemInOffHand().getItemMeta());

            player.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
            var vanilla = new EntityResurrectEvent(player, EquipmentSlot.OFF_HAND);
            support.onResurrect(vanilla);
            assertFalse(vanilla.isCancelled());
            assertEquals(Material.TOTEM_OF_UNDYING, player.getInventory().getItemInOffHand().getType());

            var unrelated = legacyTotem();
            var unrelatedMeta = unrelated.getItemMeta();
            unrelatedMeta.getPersistentDataContainer().set(NamespacedKey.fromString("cavecrawlers:item_id"),
                    PersistentDataType.STRING, "OTHER_TOTEM");
            unrelated.setItemMeta(unrelatedMeta);
            player.getInventory().setItemInMainHand(unrelated);
            var other = new EntityResurrectEvent(player, EquipmentSlot.HAND);
            support.onResurrect(other);
            assertFalse(other.isCancelled());
            assertEquals(Material.TOTEM_OF_UNDYING, player.getInventory().getItemInMainHand().getType());
        } finally {
            MockBukkit.unmock();
        }
    }

    private static ItemStack legacyTotem() {
        var stack = new ItemStack(Material.TOTEM_OF_UNDYING, 2);
        var meta = stack.getItemMeta();
        meta.setDisplayName("Custom Vitality");
        meta.setLore(List.of("Admin lore"));
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.getPersistentDataContainer().set(NamespacedKey.fromString("cavecrawlers:item_id"),
                PersistentDataType.STRING, "DC_VITALITY_TOTEM");
        meta.getPersistentDataContainer().set(NamespacedKey.fromString("test:custom_value"),
                PersistentDataType.INTEGER, 37);
        stack.setItemMeta(meta);
        return stack;
    }
}
