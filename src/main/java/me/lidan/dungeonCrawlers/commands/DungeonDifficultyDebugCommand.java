package me.lidan.dungeonCrawlers.commands;
import me.lidan.dungeonCrawlers.integration.BukkitDifficultyService;
import me.lidan.dungeonCrawlers.integration.DungeonMessages;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.annotation.CommandPermission;
import java.util.UUID;
import java.util.function.BooleanSupplier;
@Command("dungeon")
@CommandPermission("dungeoncrawlers.admin.debug")
public final class DungeonDifficultyDebugCommand {
    private final BukkitDifficultyService service;
    private final BooleanSupplier debug;
    private final RunPreparationService runs;
    private final DungeonPhaseFiveCommand preparation;
    public DungeonDifficultyDebugCommand(BukkitDifficultyService service, BooleanSupplier debug,
                                         RunPreparationService runs, DungeonPhaseFiveCommand preparation) {
        this.service = service; this.debug = debug;
        this.runs = runs; this.preparation = preparation;
    }
    /** Exercises the same class/snapshot gate and lifecycle as a physical start-door click. */
    @Subcommand("door interact")
    public void interact(Player player) {
        if (!debug.getAsBoolean()) {
            DungeonMessages.send(player, "<red>Debug mode is disabled.</red>"); return;
        }
        var run = runs.instanceFor(player.getUniqueId()).flatMap(runs::info);
        if (run.isEmpty()) DungeonMessages.send(player, "<red>You are not in a dungeon.</red>");
        else preparation.openDoorAt(player, run.orElseThrow().door().center());
    }
    @Subcommand("runic force")
    public void force(CommandSender sender, UUID entityId) {
        if (debug.getAsBoolean()) service.forceRunic(sender, entityId);
        else DungeonMessages.send(sender, "<red>Debug mode is disabled.</red>");
    }
    @Subcommand("instance generate-difficulty-debug")
    public void generate(Player player, String floorId,
                         @SuggestWith(DifficultyIdSuggestionProvider.class) String tier, long seed) {
        if (debug.getAsBoolean()) preparation.startDebug(player, floorId, tier, seed);
        else DungeonMessages.send(player, "<red>Debug mode is disabled.</red>");
    }
    @Subcommand("difficulty info")
    public void info(CommandSender sender, @Optional Player player) {
        if (player == null && sender instanceof Player self) player = self;
        if (player == null) DungeonMessages.send(sender, "<red>Specify an online player.</red>");
        else service.info(sender, player);
    }
}
