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
            DungeonMessages.send(player, DungeonMessages.error("You are not inside a generated dungeon room."));
            return;
        }
        LocationContextService.RoomContext room = context.orElseThrow();
        instanceId = room.instanceId();
        DungeonMessages.send(player, "<gray>Instance=<white>" + instanceId + "</white>, room=<white>"
                + room.index() + "</white>, template=<white>" + room.templateId() + "</white>, type=<white>"
                + room.type().name().toLowerCase() + "</white>, encounter=<white>"
                + (room.encounter() == null ? "none" : room.encounter()) + "</white>, miniboss=<white>"
                + room.miniboss() + "</white></gray>");
    }

    @Subcommand("secret list")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void secretList(CommandSender sender,
                           @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        if (!requireDebug(sender)) return;
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        var secrets = phaseSeven.secrets(id);
        if (phaseSeven.info(id).isEmpty()) {
            DungeonMessages.send(sender, DungeonMessages.error("Unknown secret instance: <white>" + id + "</white>"));
            return;
        }
        if (secrets.isEmpty()) {
            DungeonMessages.send(sender, DungeonMessages.info("Instance <white>" + id + "</white> has no secrets."));
            return;
        }
        secrets.forEach(secret -> DungeonMessages.send(sender, "<gray>secret=<white>" + secret.id()
                + "</white> kind=<white>" + secret.kind().name().toLowerCase() + "</white> point=<white>"
                + point(secret.worldPoint()) + "</white> found by=<white>"
                + (secret.foundBy() == null ? "none" : secret.foundBy()) + "</white>"
                + (secret.blessingId() == null ? "" : " blessing=<white>" + secret.blessingId() + "</white>")
                + "</gray>"));
    }

    @Subcommand("secret discover")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void secretDiscover(CommandSender sender,
                               @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                               int x, int y, int z) {
        if (!requireDebug(sender)) return;
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        UUID operator = sender instanceof Player player ? player.getUniqueId() : new UUID(0, 0);
        var result = phaseSeven.adminDiscover(id, operator, new Point(x, y, z));
        DungeonMessages.send(sender, result.successful()
                ? DungeonMessages.success(result.detail() + (result.blessingId() == null ? "" : " blessing=<white>"
                + result.blessingId() + "</white>")) : DungeonMessages.error(result.detail()));
    }

    @Subcommand("secret reset")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void secretReset(CommandSender sender,
                            @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        if (!requireDebug(sender)) return;
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        boolean reset = phaseSeven.reset(id);
        DungeonMessages.send(sender, reset ? DungeonMessages.success("Secret state reset.")
                : DungeonMessages.error("Secret state was not found."));
    }

    @Subcommand("blessing list")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void blessingList(CommandSender sender,
                             @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        if (!requireDebug(sender)) return;
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        var info = phaseSeven.info(id);
        if (info.isEmpty()) {
            DungeonMessages.send(sender, DungeonMessages.error("Unknown blessing instance: <white>" + id + "</white>"));
            return;
        }
        DungeonMessages.send(sender, "<gray>Instance=<white>" + id + "</white>, blessing levels=<white>"
                + formatMap(info.orElseThrow().blessingLevels()) + "</white></gray>");
    }

    @Subcommand("blessing add")
    @CommandPermission("dungeoncrawlers.admin.generation")
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
                        + "</white> level=<white>" + value.currentLevel() + "</white>, at cap=<white>"
                        + value.atCap() + "</white>"))
                .orElseGet(() -> DungeonMessages.error("Unknown instance or blessing.")));
    }

    @Subcommand("blessing remove")
    @CommandPermission("dungeoncrawlers.admin.generation")
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
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void blessingClear(CommandSender sender,
                              @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        if (!requireDebug(sender)) return;
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        boolean cleared = phaseSeven.clearBlessings(id);
        DungeonMessages.send(sender, cleared ? DungeonMessages.success("Blessings cleared.")
                : DungeonMessages.error("Blessing instance was not found."));
    }

    private UUID parse(CommandSender sender, String value) {
        return DungeonInstanceResolver.resolveOrNotify(sender, value, runs);
    }

    private boolean requireDebug(CommandSender sender) {
        if (debugEnabled.getAsBoolean()) return true;
        DungeonMessages.send(sender, DungeonMessages.warning(
                "This is a debug-only command and is disabled while config.yml debug is false."));
        return false;
    }

    private static String point(Point point) {
        return point.x() + ", " + point.y() + ", " + point.z();
    }

    private static String formatMap(java.util.Map<?, ?> values) {
        if (values.isEmpty()) return "none";
        return values.entrySet().stream().sorted(java.util.Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(", "));
    }
}
