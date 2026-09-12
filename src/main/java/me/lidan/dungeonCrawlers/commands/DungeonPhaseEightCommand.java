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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Phase 8 death, ghost, rejoin, escape, and wipe diagnostics. */
@Command("dungeon")
public final class DungeonPhaseEightCommand {
    private static final DateTimeFormatter ADMIN_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.systemDefault());

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
            send(player, DungeonMessages.error("You are not currently in a dungeon."));
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
        lifecycle.player(id, player.getUniqueId()).ifPresentOrElse(value -> send(sender, String.join("\n",
                        "<aqua><bold>Player status</bold></aqua>",
                        "<gray>Instance: <white>" + id + "</white></gray>",
                        "<gray>Player: <white>" + playerLabel(player) + "</white></gray>",
                        "<gray>Connection: <white>" + (value.online() ? "Online" : "Offline") + "</white></gray>",
                        "<gray>State: <white>" + displayName(value.state().name()) + "</white></gray>",
                        "<gray>Deaths: <white>" + value.deaths() + "</white></gray>",
                        "<gray>Revive: <white>" + (value.reviveAt() == null ? "Not scheduled"
                                : formatInstant(value.reviveAt())) + "</white></gray>")),
                () -> send(sender, DungeonMessages.error("No lifecycle record was found for that player in this dungeon.")));
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
        sendResult(sender, result.successful(), readableDetail(result.detail()));
    }

    private void transition(CommandSender sender, String instanceId, OfflinePlayer player,
                            Transition transition) {
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        var result = transition.apply(id, player.getUniqueId());
        sendResult(sender, result.successful(), "Player <white>" + playerLabel(player) + "</white>: "
                + readableDetail(result.detail()));
    }

    private UUID parse(CommandSender sender, String value) {
        try {
            return DungeonInstanceResolver.require(sender, value, runs);
        } catch (IllegalArgumentException exception) {
            send(sender, DungeonMessages.error("Could not find that dungeon: <white>"
                    + exception.getMessage() + "</white>"));
            return null;
        }
    }

    private static String playerLabel(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private static void sendResult(CommandSender sender, boolean successful, String detail) {
        send(sender, successful ? DungeonMessages.success(detail) : DungeonMessages.error(detail));
    }

    private static void send(CommandSender sender, String message) {
        DungeonMessages.send(sender, message);
    }

    private boolean requireDebug(CommandSender sender) {
        if (debugEnabled.getAsBoolean()) return true;
        DungeonMessages.send(sender, DungeonMessages.warning(
                "This administrative test command is unavailable while debug mode is disabled."));
        return false;
    }

    private static String displayName(String value) {
        String readable = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(readable.charAt(0)) + readable.substring(1);
    }

    private static String formatInstant(Instant instant) {
        return ADMIN_TIME_FORMAT.format(instant);
    }

    private static String readableDetail(String detail) {
        String readable = detail.replace("=", ": ").replace("; ", " · ");
        return readable.isEmpty() ? readable : Character.toUpperCase(readable.charAt(0)) + readable.substring(1);
    }

    @FunctionalInterface
    private interface Transition {
        PlayerLifecycleService.TransitionResult apply(UUID instanceId, UUID playerId);
    }
}
