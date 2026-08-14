package me.lidan.dungeonCrawlers.integration;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public interface DungeonActionBar {
    void show(Player player, Component message);
}

