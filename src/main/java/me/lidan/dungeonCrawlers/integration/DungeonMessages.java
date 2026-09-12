package me.lidan.dungeonCrawlers.integration;

import me.lidan.cavecrawlers.utils.MiniMessageUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

/** Shared player-facing message formatting for DungeonCrawlers. */
public final class DungeonMessages {
    private static final Component PREFIX = MiniMessageUtils.miniMessage(
            "<dark_gray>[<aqua><bold>DungeonCrawlers</bold></aqua>]</dark_gray> ");

    private DungeonMessages() { }

    public static Component parse(String miniMessage) {
        return prefix(MiniMessageUtils.miniMessage(miniMessage));
    }

    public static Component prefix(Component message) {
        return PREFIX.append(message);
    }

    public static void send(CommandSender sender, String miniMessage) {
        sender.sendMessage(parse(miniMessage));
    }

    public static void send(CommandSender sender, Component message) {
        sender.sendMessage(prefix(message));
    }

    public static String info(String message) {
        return "<gray>" + message + "</gray>";
    }

    public static String success(String message) {
        return "<green>" + message + "</green>";
    }

    public static String warning(String message) {
        return "<yellow>" + message + "</yellow>";
    }

    public static String error(String message) {
        return "<red>" + message + "</red>";
    }
}
