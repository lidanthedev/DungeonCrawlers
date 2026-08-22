package me.lidan.dungeonCrawlers.integration;

import me.lidan.dungeonCrawlers.core.portal.PortalEncounterService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Opens the claim-free reward view when a player uses a completed run's Ender Chest. */
public final class BukkitRewardChestListener implements Listener {
    private final PortalEncounterService encounters;
    private final String generationWorldName;
    private final BiConsumer<Player, UUID> opener;

    public BukkitRewardChestListener(PortalEncounterService encounters, String generationWorldName,
                                     BiConsumer<Player, UUID> opener) {
        this.encounters = Objects.requireNonNull(encounters, "encounters");
        this.generationWorldName = Objects.requireNonNull(generationWorldName, "generationWorldName");
        this.opener = Objects.requireNonNull(opener, "opener");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRewardChest(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        var block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENDER_CHEST
                || block.getWorld() == null || !generationWorldName.equals(block.getWorld().getName())) return;
        UUID instanceId = encounters.rewardAt(new Point(block.getX(), block.getY(), block.getZ())).orElse(null);
        if (instanceId == null) return;
        event.setCancelled(true);
        opener.accept(event.getPlayer(), instanceId);
    }
}
