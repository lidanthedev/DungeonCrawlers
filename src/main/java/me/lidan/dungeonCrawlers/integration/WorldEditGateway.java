package me.lidan.dungeonCrawlers.integration;

import org.bukkit.entity.Player;

public interface WorldEditGateway {
    SelectionResult selection(Player player);

    record SelectionResult(boolean successful, String detail) {}
}

