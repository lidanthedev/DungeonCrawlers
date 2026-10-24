package me.lidan.dungeonCrawlers.commands;

import me.lidan.dungeonCrawlers.config.registry.ConfigRegistryService;
import me.lidan.dungeonCrawlers.core.difficulty.DungeonProgressionService;
import me.lidan.dungeonCrawlers.integration.DungeonMessages;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Default;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.Locale;

@Command("dungeon")
@CommandPermission("dungeoncrawlers.admin.progression")
public final class DungeonProgressionCommand {
    private final ConfigRegistryService configs;
    private final DungeonProgressionService progression;

    public DungeonProgressionCommand(ConfigRegistryService configs, DungeonProgressionService progression) {
        this.configs = configs;
        this.progression = progression;
    }

    @Subcommand("completions add")
    public void add(CommandSender sender,
                    @SuggestWith(OfflinePlayerSuggestionProvider.class) OfflinePlayer player,
                    @SuggestWith(FloorIdSuggestionProvider.class) String floorId,
                    @SuggestWith(DifficultyIdSuggestionProvider.class) String difficultyId,
                    @Default("1") int amount) {
        if (!player.isOnline() && !player.hasPlayedBefore()) {
            DungeonMessages.send(sender, DungeonMessages.error("That player has not joined the server."));
            return;
        }
        if (!configs.snapshot().floors().containsKey(floorId)) {
            DungeonMessages.send(sender, DungeonMessages.error("Unknown floor."));
            return;
        }
        var rules = configs.snapshot().difficulties().get(difficultyId.toLowerCase(Locale.ROOT));
        if (rules == null) {
            DungeonMessages.send(sender, DungeonMessages.error("Unknown dungeon difficulty."));
            return;
        }
        if (amount <= 0) {
            DungeonMessages.send(sender, DungeonMessages.error("Completion amount must be positive."));
            return;
        }
        try {
            long total = progression.addCompletions(player.getUniqueId(), floorId, rules.tier(), amount);
            String name = player.getName() == null ? player.getUniqueId().toString() : player.getName();
            DungeonMessages.send(sender, DungeonMessages.success("Added <white>" + amount + "</white> "
                    + rules.tier().displayName() + " completions for <white>" + name + "</white> on <white>"
                    + floorId + "</white>. Total: <white>" + total + "</white>. Unlocked through <white>"
                    + rules.tier().next().displayName() + "</white>. No XP or loot awarded."));
        } catch (RuntimeException exception) {
            sender.getServer().getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not grant dungeon completions to " + player.getUniqueId(), exception);
            DungeonMessages.send(sender, DungeonMessages.error("Completion grant failed; no unlock was applied."));
        }
    }
}
