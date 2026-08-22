package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.core.claim.RewardClaimService;
import me.lidan.cavecrawlers.utils.MiniMessageUtils;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/** Blocks every common transfer path while a mailbox item is awaiting its durable delivery ACK. */
public final class BukkitRewardMailboxListener implements Listener {
    private final RewardClaimService claims;

    public BukkitRewardMailboxListener(RewardClaimService claims) {
        this.claims = Objects.requireNonNull(claims, "claims");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        recover(event.getPlayer());
    }

    /** Runs the same durable recovery path for players who stayed online across a plugin reload. */
    public void recover(Player player) {
        claims.deliverPending(player, result -> {
            if (result.successful()) {
                player.sendMessage(MiniMessageUtils.miniMessage(
                        "<green>Reward delivered to your inventory.</green>"));
            } else if (result.pending()) {
                player.sendMessage(MiniMessageUtils.miniMessage(
                        "<yellow>Reward delivery pending: " + result.detail() + "</yellow>"));
            } else {
                player.sendMessage(MiniMessageUtils.miniMessage(
                        "<red>Reward delivery failed: " + result.detail() + "</red>"));
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (pending(event.getCurrentItem()) || pending(event.getCursor())
                || java.util.Arrays.stream(event.getView().getTopInventory().getContents()).anyMatch(
                BukkitRewardMailboxListener::pending)
                || (event.getHotbarButton() >= 0 && pending(event.getWhoClicked().getInventory()
                .getItem(event.getHotbarButton())))
                || (event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND
                && pending(event.getWhoClicked().getInventory().getItemInOffHand()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (pending(event.getOldCursor()) || event.getNewItems().values().stream().anyMatch(
                BukkitRewardMailboxListener::pending)
                || java.util.Arrays.stream(event.getView().getTopInventory().getContents()).anyMatch(
                BukkitRewardMailboxListener::pending)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrop(PlayerDropItemEvent event) {
        if (pending(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPickup(EntityPickupItemEvent event) {
        if (pending(event.getItem().getItemStack())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        if (pending(event.getItem().getItemStack())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMove(InventoryMoveItemEvent event) {
        if (pending(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlace(BlockPlaceEvent event) {
        if (pending(event.getItemInHand())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (pending(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (pending(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (pending(event.getPlayer().getInventory().getItem(event.getHand()))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (pending(event.getMainHandItem()) || pending(event.getOffHandItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (pending(event.getPlayer().getInventory().getItem(event.getHand()))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (pending(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCraft(CraftItemEvent event) {
        if (pending(event.getCurrentItem()) || event.getInventory().getMatrix() != null
                && java.util.Arrays.stream(event.getInventory().getMatrix()).anyMatch(
                BukkitRewardMailboxListener::pending)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDeath(PlayerDeathEvent event) {
        boolean hasPending = event.getDrops().stream().anyMatch(BukkitRewardMailboxListener::pending);
        if (hasPending) {
            event.getDrops().removeIf(BukkitRewardMailboxListener::pending);
            event.setKeepInventory(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onItemDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Item item && pending(item.getItemStack())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onItemDespawn(ItemDespawnEvent event) {
        if (pending(event.getEntity().getItemStack())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onItemMerge(ItemMergeEvent event) {
        if (pending(event.getEntity().getItemStack()) || pending(event.getTarget().getItemStack())) {
            event.setCancelled(true);
        }
    }

    private static boolean pending(ItemStack item) {
        return RewardClaimService.isPending(item);
    }
}
