package me.lidan.dungeonCrawlers.integration.cave;

import me.lidan.cavecrawlers.CaveCrawlers;
import me.lidan.cavecrawlers.api.ItemsAPI;
import me.lidan.dungeonCrawlers.integration.CaveItemsGateway;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public final class CaveItemsAdapter implements CaveItemsGateway {
    private ItemsAPI api() {
        return CaveCrawlers.getAPI().getItemsAPI();
    }

    @Override
    public boolean isConfigured(String itemId) {
        return api().getItemByID(itemId) != null;
    }

    @Override
    public Optional<ItemStack> build(String itemId, int amount) {
        if (amount < 1 || !isConfigured(itemId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(api().buildItem(itemId, amount));
    }
}

