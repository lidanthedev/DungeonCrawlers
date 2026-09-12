package me.lidan.dungeonCrawlers.commands;

import me.lidan.dungeonCrawlers.core.lifecycle.PlayerLifecycleService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.integration.DungeonMessages;
import org.bukkit.command.CommandSender;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Phase 8 death, ghost, rejoin, escape, and wipe diagnostics. */
@Command("dungeon")
public final class DungeonPhaseEightCommand {
    private final PlayerLifecycleService lifecycle;
    private final RunPreparationService runs;
    private final DungeonPhaseFiveCommand phaseFive;
    private final BooleanSupplier debugEnabled;

    public DungeonPhaseEightCommand(PlayerLifecycleService lifecycle, RunPreparationService runs,
                                    DungeonPhaseFiveCommand phaseFive) {
        this(lifecycle, runs, phaseFive, () -> false);
    }

    public DungeonPhaseEightCommand(PlayerLifecycleService lifecycle, RunPreparationService runs,
                                    DungeonPhaseFiveCommand phaseFive, BooleanSupplier debugEnabled) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.phaseFive = Objects.requireNonNull(phaseFive, "phaseFive");
        this.debugEnabled = Objects.requireNonNull(debugEnabled, "debugEnabled");
    }

    @Subcommand("escape")
    @CommandPermission("dungeoncrawlers.use")
    public void escape(Player player) {
        if (runs.instanceFor(player.getUniqueId()).isEmpty()) {
            send(player, "<red>You are not in a running dungeon.</red>");
            return;
        }
        phaseFive.leaveFromDungeon(player);
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
        lifecycle.player(id, player.getUniqueId()).ifPresentOrElse(value -> send(sender, "<gray>instance=<white>" + id
                        + "</white> player=<white>" + playerLabel(player) + "</white> state=<white>"
                        + value.state().name().toLowerCase() + "</white>"
                        + " deaths=" + value.deaths() + " reviveAt="
                        + (value.reviveAt() == null ? "none" : value.reviveAt()) + "</white></gray>"),
                () -> send(sender, "<red>Unknown lifecycle player.</red>"));
    }

    @Subcommand("player death")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void death(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                      @SuggestWith(OfflinePlayerSuggestionProvider.class) OfflinePlayer player) {
        if (!requireDebug(sender)) return;
        transition(sender, instanceId, player, lifecycle::lethal);
    }

    @Subcommand("player ghost")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void ghost(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                      @SuggestWith(OfflinePlayerSuggestionProvider.class) OfflinePlayer player) {
        if (!requireDebug(sender)) return;
        death(sender, instanceId, player);
    }

    @Subcommand("player revive")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void revive(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                       @SuggestWith(OfflinePlayerSuggestionProvider.class) OfflinePlayer player) {
        if (!requireDebug(sender)) return;
        transition(sender, instanceId, player, lifecycle::scheduleAdminRevive);
    }

    @Subcommand("player remove")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void remove(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                       @SuggestWith(OfflinePlayerSuggestionProvider.class) OfflinePlayer player) {
        if (!requireDebug(sender)) return;
        transition(sender, instanceId, player, lifecycle::remove);
    }

    @Subcommand("instance wipe")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void wipe(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        if (!requireDebug(sender)) return;
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

    private UUID parse(CommandSender sender, String value) {
        try {
            return DungeonInstanceResolver.require(sender, value, runs);
        } catch (IllegalArgumentException exception) {
            send(sender, "<red>" + exception.getMessage() + "</red>");
            return null;
        }
    }

    private static String playerLabel(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private static void sendResult(CommandSender sender, boolean successful, String detail) {
        String color = successful ? "green" : "red";
        send(sender, "<" + color + ">" + detail + "</" + color + ">");
    }

    private static void send(CommandSender sender, String message) {
        DungeonMessages.send(sender, message);
    }

    private boolean requireDebug(CommandSender sender) {
        if (debugEnabled.getAsBoolean()) return true;
        DungeonMessages.send(sender, DungeonMessages.warning(
                "This is a debug-only command and is disabled while config.yml debug is false."));
        return false;
    }

    @FunctionalInterface
    private interface Transition {
        PlayerLifecycleService.TransitionResult apply(UUID instanceId, UUID playerId);
    }
}
