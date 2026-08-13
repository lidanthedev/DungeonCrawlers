package me.lidan.dungeonCrawlers.commands;

import org.bukkit.command.CommandSender;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;

@Command("dungeoncrawlers")
@CommandPermission("dungeoncrawlers.use")
public class DungeonCrawlersCommand {
    @Subcommand("hello")
    public void hello(CommandSender sender) {
        sender.sendMessage("Hello World!");
    }
}
