package me.lidan.dungeonCrawlers.integration.cave;

import me.lidan.cavecrawlers.gui.SellMenu;
import me.lidan.cavecrawlers.items.ItemInfo;
import me.lidan.cavecrawlers.items.ItemType;
import me.lidan.cavecrawlers.items.ItemsManager;
import me.lidan.cavecrawlers.items.Rarity;
import me.lidan.cavecrawlers.stats.StatType;
import me.lidan.cavecrawlers.stats.Stats;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Installs native item files and /sell defaults once; administrator edits survive reloads. */
public final class DungeonRewardItems {
    private DungeonRewardItems() { }

    public static void register(JavaPlugin plugin) {
        if (register(plugin, ItemsManager.getInstance(), SellMenu.config)) SellMenu.config.save();
    }

    static boolean register(JavaPlugin plugin, ItemsManager items, YamlConfiguration prices) {
        var resource = Objects.requireNonNull(plugin.getResource("reward-items.yml"), "reward-items.yml");
        var definitions = YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
        boolean changed = false;
        for (String id : definitions.getKeys(false)) {
            var definition = Objects.requireNonNull(definitions.getConfigurationSection(id));
            double price = definition.getDouble("sell-price", -1);
            if (!Double.isFinite(price) || price <= 0) throw new IllegalArgumentException("Invalid sell price: " + id);
            if (items.getItemByID(id) == null) {
                var stats = new Stats();
                var values = Objects.requireNonNull(definition.getConfigurationSection("stats"));
                for (String stat : values.getKeys(false)) stats.set(StatType.valueOf(stat), values.getDouble(stat));
                var base = new ItemStack(Material.valueOf(Objects.requireNonNull(definition.getString("material"))));
                if (definition.contains("color") && base.getItemMeta() instanceof LeatherArmorMeta meta) {
                    meta.setColor(Color.fromRGB(definition.getInt("color")));
                    base.setItemMeta(meta);
                }
                items.setItem(id, new ItemInfo(Objects.requireNonNull(definition.getString("name")),
                        definition.getString("description"), stats,
                        ItemType.valueOf(Objects.requireNonNull(definition.getString("type"))), base,
                        Rarity.valueOf(Objects.requireNonNull(definition.getString("rarity"))), definition.getString("ability")));
            }
            if (!prices.contains("prices." + id)) {
                prices.set("prices." + id, price);
                changed = true;
            }
        }
        return changed;
    }
}
