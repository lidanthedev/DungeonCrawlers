package me.lidan.dungeonCrawlers.integration.cave;

import me.lidan.cavecrawlers.CaveCrawlers;
import me.lidan.dungeonCrawlers.integration.DungeonActionBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public final class CaveActionBarAdapter implements DungeonActionBar {
    @Override
    public void show(Player player, Component message) {
        CaveCrawlers.getAPI().getActionBarAPI().showActionBar(player, message);
    }
}

