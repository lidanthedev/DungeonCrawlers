package me.lidan.dungeonCrawlers.commands;

import me.lidan.cavecrawlers.utils.MiniMessageUtils;
import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import org.bukkit.command.CommandSender;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.UUID;

/** Phase 8 death, ghost, rejoin, escape, and wipe diagnostics. */
@Command("dungeon")
public final class DungeonPhaseEightCommand {
    private final PlayerLifecycleService lifecycle;
    private final RunPreparationService runs;

    public DungeonPhaseEightCommand(PlayerLifecycleService lifecycle, RunPreparationService runs) {
        this.lifecycle = lifecycle;
        this.runs = runs;
    }

    @Subcommand("escape")
    @CommandPermission("dungeoncrawlers.use")
    public void escape(Player player) {
        UUID instanceId = runs.instanceFor(player.getUniqueId()).orElse(null);
        if (instanceId == null) {
            send(player, "<red>[FAIL] you are not in a running dungeon</red>");
            return;
        }
        var result = lifecycle.escape(instanceId, player.getUniqueId());
        sendResult(player, result.successful(), result.detail());
    }

    @Subcommand("leave")
    @CommandPermission("dungeoncrawlers.use")
    public void leave(Player player) {
        escape(player);
    }

    @Subcommand("player info")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void info(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                     @SuggestWith(OfflinePlayerSuggestionProvider.class) OfflinePlayer player) {
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        lifecycle.player(id, player.getUniqueId()).ifPresentOrElse(value -> send(sender, "<green>[PASS] instance=" + id
                        + " player=" + playerLabel(player) + " state=" + value + "</green>"),
                () -> send(sender, "<red>[FAIL] unknown lifecycle player</red>"));
    }

    @Subcommand("player death")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void death(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                      @SuggestWith(OfflinePlayerSuggestionProvider.class) OfflinePlayer player) {
        transition(sender, instanceId, player, lifecycle::lethal);
    }

    @Subcommand("player ghost")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void ghost(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                      @SuggestWith(OfflinePlayerSuggestionProvider.class) OfflinePlayer player) {
        death(sender, instanceId, player);
    }

    @Subcommand("player revive")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void revive(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                       @SuggestWith(OfflinePlayerSuggestionProvider.class) OfflinePlayer player) {
        transition(sender, instanceId, player, lifecycle::scheduleAdminRevive);
    }

    @Subcommand("player remove")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void remove(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                       @SuggestWith(OfflinePlayerSuggestionProvider.class) OfflinePlayer player) {
        transition(sender, instanceId, player, lifecycle::remove);
    }

    @Subcommand("instance wipe")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void wipe(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        var result = lifecycle.wipe(id, "admin wipe");
        sendResult(sender, result.successful(), result.detail());
    }

    private void transition(CommandSender sender, String instanceId, OfflinePlayer player,
                            Transition transition) {
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        var result = transition.apply(id, player.getUniqueId());
        sendResult(sender, result.successful(), "player=" + playerLabel(player) + " " + result.detail());
    }

    private static UUID parse(CommandSender sender, String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            send(sender, "<red>[FAIL] id must be a UUID</red>");
            return null;
        }
    }

    private static String playerLabel(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private static void sendResult(CommandSender sender, boolean successful, String detail) {
        String color = successful ? "green" : "red";
        send(sender, "<" + color + ">[" + (successful ? "PASS" : "FAIL") + "] " + detail
                + "</" + color + ">");
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(MiniMessageUtils.miniMessage(message));
    }

    @FunctionalInterface
    private interface Transition {
        PlayerLifecycleService.TransitionResult apply(UUID instanceId, UUID playerId);
    }
}
