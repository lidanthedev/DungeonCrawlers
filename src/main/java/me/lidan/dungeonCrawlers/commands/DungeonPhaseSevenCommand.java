package me.lidan.dungeonCrawlers.commands;

import me.lidan.dungeonCrawlers.core.location.LocationContextService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.secret.SecretDiscoveryService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.integration.DungeonMessages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.Locale;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Phase 7 location, secret, and transient blessing diagnostics. */
@Command("dungeon")
public final class DungeonPhaseSevenCommand {
    private final SecretDiscoveryService phaseSeven;
    private final RunPreparationService runs;
    private final BooleanSupplier debugEnabled;

    public DungeonPhaseSevenCommand(SecretDiscoveryService phaseSeven, RunPreparationService runs) {
        this(phaseSeven, runs, () -> false);
    }

    public DungeonPhaseSevenCommand(SecretDiscoveryService phaseSeven, RunPreparationService runs,
                                    BooleanSupplier debugEnabled) {
        this.phaseSeven = phaseSeven;
        this.runs = runs;
        this.debugEnabled = debugEnabled;
    }

    @Subcommand("whereami")
    @CommandPermission("dungeoncrawlers.use")
    public void whereami(Player player) {
        UUID instanceId = runs.instanceFor(player.getUniqueId()).orElse(null);
        var context = instanceId == null ? java.util.Optional.<LocationContextService.RoomContext>empty()
                : phaseSeven.locate(instanceId, new Point(player.getLocation().getBlockX(),
                player.getLocation().getBlockY(), player.getLocation().getBlockZ()));
        if (context.isEmpty()) {
            DungeonMessages.send(player, DungeonMessages.error("You are not standing inside a dungeon room."));
            return;
        }
        LocationContextService.RoomContext room = context.orElseThrow();
        instanceId = room.instanceId();
        sendBlock(player,
                "<aqua><bold>Dungeon location</bold></aqua>",
                "<gray>Instance: <white>" + instanceId + "</white></gray>",
                "<gray>Room: <white>" + room.index() + "</white></gray>",
                "<gray>Template: <white>" + room.templateId() + "</white></gray>",
                "<gray>Room type: <white>" + displayName(room.type().name()) + "</white></gray>",
                "<gray>Encounter: <white>" + (room.encounter() == null ? "None"
                        : displayName(room.encounter().name())) + "</white></gray>",
                "<gray>Miniboss: <white>" + (room.miniboss() ? "Yes" : "No") + "</white></gray>");
    }

    @Subcommand("secret list")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void secretList(CommandSender sender,
                           @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        if (!requireDebug(sender)) return;
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        var secrets = phaseSeven.secrets(id);
        if (phaseSeven.info(id).isEmpty()) {
            DungeonMessages.send(sender, DungeonMessages.error("No secret data is available for dungeon <white>"
                    + id + "</white>."));
            return;
        }
        if (secrets.isEmpty()) {
            DungeonMessages.send(sender, DungeonMessages.info("Dungeon <white>" + id + "</white> has no secrets."));
            return;
        }
        secrets.forEach(secret -> {
            String blessing = secret.blessingId() == null ? "None" : secret.blessingId();
            sendBlock(sender,
                    "<aqua><bold>Secret " + secret.id() + "</bold></aqua>",
                    "<gray>Type: <white>" + displayName(secret.kind().name()) + "</white></gray>",
                    "<gray>Location: <white>" + point(secret.worldPoint()) + "</white></gray>",
                    "<gray>Found by: <white>" + (secret.foundBy() == null ? "None" : secret.foundBy()) + "</white></gray>",
                    "<gray>Blessing: <white>" + blessing + "</white></gray>");
        });
    }

    @Subcommand("secret discover")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void secretDiscover(CommandSender sender,
                               @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                               int x, int y, int z) {
        if (!requireDebug(sender)) return;
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        UUID operator = sender instanceof Player player ? player.getUniqueId() : new UUID(0, 0);
        var result = phaseSeven.adminDiscover(id, operator, new Point(x, y, z));
        String detail = readableDetail(result.detail())
                + (result.blessingId() == null ? "" : " <gray>(Blessing: <white>"
                + result.blessingId() + "</white>)</gray>");
        DungeonMessages.send(sender, result.successful()
                ? DungeonMessages.success(detail) : DungeonMessages.error(detail));
    }

    @Subcommand("secret reset")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void secretReset(CommandSender sender,
                            @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        if (!requireDebug(sender)) return;
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        boolean reset = phaseSeven.reset(id);
        DungeonMessages.send(sender, reset ? DungeonMessages.success("Secret state reset.")
                : DungeonMessages.error("No secret state was found for that dungeon."));
    }

    @Subcommand("blessing list")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void blessingList(CommandSender sender,
                             @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        if (!requireDebug(sender)) return;
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        var info = phaseSeven.info(id);
        if (info.isEmpty()) {
            DungeonMessages.send(sender, DungeonMessages.error("No blessing data is available for dungeon <white>"
                    + id + "</white>."));
            return;
        }
        DungeonMessages.send(sender, "<aqua><bold>Blessing levels</bold></aqua> <gray>for instance <white>"
                + id + "</white></gray>");
        if (info.orElseThrow().blessingLevels().isEmpty()) {
            DungeonMessages.send(sender, DungeonMessages.info("No blessings have been discovered."));
            return;
        }
        info.orElseThrow().blessingLevels().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> DungeonMessages.send(sender, "<gray>" + entry.getKey()
                        + ": <white>level " + entry.getValue() + "</white></gray>"));
    }

    @Subcommand("blessing add")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void blessingAdd(CommandSender sender,
                            @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                            @SuggestWith(BlessingIdSuggestionProvider.class) String blessingId,
                            @Optional Integer discoveries) {
        if (!requireDebug(sender)) return;
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        int count = discoveries == null ? 1 : discoveries;
        final java.util.Optional<me.lidan.dungeonCrawlers.core.stats.BlessingLevels.DiscoveryResult> result;
        try {
            result = phaseSeven.addBlessing(id, blessingId, count);
        } catch (IllegalArgumentException exception) {
            DungeonMessages.send(sender, DungeonMessages.error(exception.getMessage()));
            return;
        }
        DungeonMessages.send(sender, result.map(value -> DungeonMessages.success("Blessing <white>" + blessingId
                        + "</white> advanced to level <white>" + value.currentLevel() + "</white>."
                        + (value.atCap() ? " It is at its maximum level." : "")))
                .orElseGet(() -> DungeonMessages.error("The dungeon or blessing was not found.")));
    }

    @Subcommand("blessing remove")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void blessingRemove(CommandSender sender,
                               @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                               @SuggestWith(BlessingIdSuggestionProvider.class) String blessingId) {
        if (!requireDebug(sender)) return;
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        boolean removed = phaseSeven.removeBlessing(id, blessingId);
        DungeonMessages.send(sender, removed ? DungeonMessages.success("Blessing removed: <white>" + blessingId
                + "</white>.") : DungeonMessages.error("Blessing was not present: <white>" + blessingId + "</white>."));
    }

    @Subcommand("blessing clear")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void blessingClear(CommandSender sender,
                              @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        if (!requireDebug(sender)) return;
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        boolean cleared = phaseSeven.clearBlessings(id);
        DungeonMessages.send(sender, cleared ? DungeonMessages.success("Blessings cleared.")
                : DungeonMessages.error("No blessing state was found for that dungeon."));
    }

    private UUID parse(CommandSender sender, String value) {
        return DungeonInstanceResolver.resolveOrNotify(sender, value, runs);
    }

    private boolean requireDebug(CommandSender sender) {
        if (debugEnabled.getAsBoolean()) return true;
        DungeonMessages.send(sender, DungeonMessages.warning(
                "This administrative test command is unavailable while debug mode is disabled."));
        return false;
    }

    private static void sendBlock(CommandSender sender, String... lines) {
        DungeonMessages.send(sender, String.join("\n", lines));
    }

    private static String displayName(String value) {
        String readable = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(readable.charAt(0)) + readable.substring(1);
    }

    private static String readableDetail(String detail) {
        String readable = detail.replace("=", ": ").replace("; ", " · ");
        return readable.isEmpty() ? readable : Character.toUpperCase(readable.charAt(0)) + readable.substring(1);
    }

    private static String point(Point point) {
        return point.x() + ", " + point.y() + ", " + point.z();
    }

}
