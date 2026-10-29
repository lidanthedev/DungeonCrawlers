package me.lidan.dungeonCrawlers.integration.cave;

import me.lidan.cavecrawlers.items.ItemInfo;
import me.lidan.cavecrawlers.items.ItemType;
import me.lidan.cavecrawlers.items.ItemsManager;
import me.lidan.cavecrawlers.items.Rarity;
import me.lidan.cavecrawlers.items.abilities.AbilityManager;
import me.lidan.cavecrawlers.items.abilities.ClickAbility;
import me.lidan.cavecrawlers.stats.StatType;
import me.lidan.cavecrawlers.stats.Stats;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;

/** Native CaveCrawlers items. Nearby effects belong to the support service; cooldowns,
 * mana, and action-bar feedback use the normal {@link ClickAbility} flow. */
public final class DungeonSupportItems implements AutoCloseable, Listener {
    private static final String VITALITY_ITEM_ID = "DC_VITALITY_TOTEM";
    private static final NamespacedKey NATIVE_ITEM_ID = Objects.requireNonNull(
            NamespacedKey.fromString("cavecrawlers:item_id"));
    private final JavaPlugin plugin;
    private final BiPredicate<Player, String> activate;
    private final AbilityManager abilities = AbilityManager.getInstance();
    private final ItemsManager items = ItemsManager.getInstance();
    private final Set<String> itemIds = new LinkedHashSet<>();

    public DungeonSupportItems(JavaPlugin plugin, BiPredicate<Player, String> activate) {
        this.plugin = Objects.requireNonNull(plugin);
        this.activate = Objects.requireNonNull(activate);
    }

    public void register() {
        if (!itemIds.isEmpty()) {
            return;
        }
        var resource = Objects.requireNonNull(plugin.getResource("support-items.yml"), "support-items.yml");
        var definitions = YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
        try {
            for (String itemId : definitions.getKeys(false)) {
                var definition = Objects.requireNonNull(definitions.getConfigurationSection(itemId));
                String abilityId = Objects.requireNonNull(definition.getString("ability"));
                if (abilities.getAbilityByID(abilityId) != null) {
                    throw new IllegalStateException("Support ability is already registered: " + abilityId);
                }
                var ability = new SupportAbility(abilityId,
                        Objects.requireNonNull(definition.getString("ability-name")),
                        Objects.requireNonNull(definition.getString("ability-description"))
                                + " Effects have half strength outside dungeons.",
                        definition.getDouble("mana-cost", 0),
                        definition.getLong("cooldown-seconds") * 1000);
                abilities.registerAbility(abilityId, ability);
                itemIds.add(itemId);
                if (items.getItemByID(itemId) == null) {
                    var stats = new Stats();
                    var statValues = Objects.requireNonNull(definition.getConfigurationSection("stats"));
                    for (String stat : statValues.getKeys(false)) {
                        stats.set(StatType.valueOf(stat), statValues.getDouble(stat));
                    }
                    var item = new ItemInfo(Objects.requireNonNull(definition.getString("name")),
                            definition.getString("description"), stats, ItemType.WAND,
                            new ItemStack(Material.valueOf(Objects.requireNonNull(definition.getString("material")))),
                            Rarity.EPIC, abilityId);
                    // Persist native definitions so /cc reload items keeps existing support items usable.
                    items.setItem(itemId, item);
                } else if (VITALITY_ITEM_ID.equals(itemId)) {
                    var item = items.getItemByID(itemId);
                    if (item.getBaseItem().getType() == Material.TOTEM_OF_UNDYING) {
                        item.getBaseItem().setType(Material.BEACON);
                        items.setItem(itemId, item);
                    }
                }
            }
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            plugin.getServer().getOnlinePlayers().forEach(this::migrateInventory);
        } catch (RuntimeException exception) {
            close();
            throw exception;
        }
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
        for (String itemId : itemIds) {
            ItemInfo item = items.getItemByID(itemId);
            if (item != null && item.getAbility() instanceof SupportAbility ability && ability.owner() == this) {
                items.unregisterItem(itemId);
            }
        }
        // Settings variants clone ClickAbility and also register listeners in CaveCrawlers.
        abilities.getAbilityMap().entrySet().removeIf(entry -> {
            if (entry.getValue() instanceof SupportAbility ability && ability.owner() == this) {
                HandlerList.unregisterAll(ability);
                return true;
            }
            return false;
        });
        itemIds.clear();
    }

    private boolean migrateLegacyTotem(ItemStack stack) {
        if (stack == null || stack.getType() != Material.TOTEM_OF_UNDYING || !stack.hasItemMeta()
                || !VITALITY_ITEM_ID.equals(stack.getItemMeta().getPersistentDataContainer()
                .get(NATIVE_ITEM_ID, PersistentDataType.STRING))) {
            return false;
        }
        // Change only material: native identity, admin metadata, enchants and quantity survive.
        stack.setType(Material.BEACON);
        return true;
    }

    private void migrateInventory(Player player) {
        var inventory = player.getInventory();
        var contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (migrateLegacyTotem(contents[slot])) {
                inventory.setItem(slot, contents[slot]);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        migrateInventory(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHeld(PlayerItemHeldEvent event) {
        migrateInventory(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        migrateInventory(event.getPlayer());
        migrateLegacyTotem(event.getItem());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        migrateLegacyTotem(event.getMainHandItem());
        migrateLegacyTotem(event.getOffHandItem());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player && event.getHand() != null) {
            var stack = player.getInventory().getItem(event.getHand());
            if (migrateLegacyTotem(stack)) {
                player.getInventory().setItem(event.getHand(), stack);
                event.setCancelled(true);
            }
        }
    }

    private final class SupportAbility extends ClickAbility {
        private final String id;

        private SupportAbility(String id, String name, String description, double cost, long cooldown) {
            super(name, description, cost, cooldown);
            this.id = id;
        }

        private DungeonSupportItems owner() {
            return DungeonSupportItems.this;
        }

        @Override
        protected boolean useAbility(PlayerEvent event) {
            return activate.test(event.getPlayer(), id);
        }
    }
}
