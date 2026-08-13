package me.lidan.dungeonCrawlers.integration;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public interface CaveItemsGateway {
    boolean isConfigured(String itemId);

    Optional<ItemStack> build(String itemId, int amount);
}

