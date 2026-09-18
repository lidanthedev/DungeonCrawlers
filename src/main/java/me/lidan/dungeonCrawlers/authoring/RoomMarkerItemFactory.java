package me.lidan.dungeonCrawlers.authoring;

import me.lidan.dungeonCrawlers.core.template.RoomMarker;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Creates and recognizes the admin-only room-authoring marker kit. */
public final class RoomMarkerItemFactory {
    public static final String PDC_KEY = "room_marker";
    private final NamespacedKey markerKey;

    public RoomMarkerItemFactory(Plugin plugin) {
        markerKey = plugin == null ? new NamespacedKey("dungeoncrawlers", PDC_KEY)
                : new NamespacedKey(plugin, PDC_KEY);
    }

    public ItemStack create(RoomMarker marker) {
        Objects.requireNonNull(marker, "marker");
        ItemStack item = new ItemStack(material(marker));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(me.lidan.cavecrawlers.utils.MiniMessageUtils.miniMessage(marker.displayName()));
        meta.lore(List.of(
                me.lidan.cavecrawlers.utils.MiniMessageUtils.miniMessage("<gray>" + marker.description() + "</gray>"),
                me.lidan.cavecrawlers.utils.MiniMessageUtils.miniMessage(
                        "<dark_gray>DungeonCrawlers room marker: " + marker.id() + "</dark_gray>")));
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.STRING, marker.id());
        item.setItemMeta(meta);
        return item;
    }

    public Optional<RoomMarker> marker(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return Optional.empty();
        String id = item.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.STRING);
        return RoomMarker.byId(id);
    }

    public boolean isSetupItem(ItemStack item) {
        return marker(item).isPresent();
    }

    /** Removes only this plugin's marker items while leaving unrelated inventory contents untouched. */
    public int removeSetupItems(Inventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        int removed = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!isSetupItem(item)) continue;
            removed += item.getAmount();
            inventory.setItem(slot, null);
        }
        return removed;
    }

    public List<ItemStack> createKit() {
        return RoomMarker.ordered().stream().map(this::create).toList();
    }

    /** Portal blocks are created by lighting an obsidian frame, so the kit supplies the useful tool. */
    private static Material material(RoomMarker marker) {
        return switch (marker) {
            case ENTRANCE, EXIT -> Material.JIGSAW;
            case NORMAL_MOB -> Material.GRAY_CONCRETE_POWDER;
            case MINIBOSS_MOB -> Material.YELLOW_CONCRETE_POWDER;
            case PLAYER_SPAWN -> Material.EMERALD_BLOCK;
            case CLASS_SELECTOR_NPC -> Material.ORANGE_CONCRETE_POWDER;
            case BOSS_SPAWN -> Material.RED_CONCRETE_POWDER;
            case REWARD_CHEST -> Material.LIME_CONCRETE_POWDER;
            case BLESSING_CHEST -> Material.CHEST;
            case STANDARD_CHEST -> Material.TRAPPED_CHEST;
            case PORTAL -> Material.FLINT_AND_STEEL;
        };
    }
}
