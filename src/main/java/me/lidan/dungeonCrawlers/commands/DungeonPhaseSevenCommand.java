package me.lidan.dungeonCrawlers.commands;

import me.lidan.dungeonCrawlers.core.location.LocationContextService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.core.secret.SecretDiscoveryService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.UUID;

/** Phase 7 location, secret, and transient blessing diagnostics. */
@Command("dungeon")
public final class DungeonPhaseSevenCommand {
    private final SecretDiscoveryService phaseSeven;
    private final RunPreparationService runs;

    public DungeonPhaseSevenCommand(SecretDiscoveryService phaseSeven, RunPreparationService runs) {
        this.phaseSeven = phaseSeven;
        this.runs = runs;
    }

    @Subcommand("whereami")
    @CommandPermission("dungeoncrawlers.use")
    public void whereami(Player player) {
        UUID instanceId = runs.instanceFor(player.getUniqueId()).orElse(null);
        var context = instanceId == null ? java.util.Optional.<LocationContextService.RoomContext>empty()
                : phaseSeven.locate(instanceId, new Point(player.getLocation().getBlockX(),
                player.getLocation().getBlockY(), player.getLocation().getBlockZ()));
        if (context.isEmpty()) {
            player.sendMessage("[FAIL] you are not inside a generated dungeon room");
            return;
        }
        LocationContextService.RoomContext room = context.orElseThrow();
        instanceId = room.instanceId();
        player.sendMessage("[PASS] instance=" + instanceId + " room=" + room.index()
                + " template=" + room.templateId() + " type=" + room.type()
                + " encounter=" + (room.encounter() == null ? "none" : room.encounter())
                + " miniboss=" + room.miniboss());
    }

    @Subcommand("secret list")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void secretList(CommandSender sender,
                           @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        var secrets = phaseSeven.secrets(id);
        if (phaseSeven.info(id).isEmpty()) {
            sender.sendMessage("[FAIL] unknown secret instance " + id);
            return;
        }
        if (secrets.isEmpty()) {
            sender.sendMessage("[PASS] instance=" + id + " secrets=0");
            return;
        }
        secrets.forEach(secret -> sender.sendMessage("[PASS] secret=" + secret.id()
                + " kind=" + secret.kind() + " point=" + secret.worldPoint()
                + " foundBy=" + (secret.foundBy() == null ? "none" : secret.foundBy())
                + (secret.blessingId() == null ? "" : " blessing=" + secret.blessingId())));
    }

    @Subcommand("secret discover")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void secretDiscover(CommandSender sender,
                               @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                               int x, int y, int z) {
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        UUID operator = sender instanceof Player player ? player.getUniqueId() : new UUID(0, 0);
        var result = phaseSeven.adminDiscover(id, operator, new Point(x, y, z));
        sender.sendMessage("[" + (result.successful() ? "PASS" : "FAIL") + "] " + result.detail()
                + (result.blessingId() == null ? "" : " blessing=" + result.blessingId()));
    }

    @Subcommand("secret reset")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void secretReset(CommandSender sender,
                            @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        boolean reset = phaseSeven.reset(id);
        sender.sendMessage("[" + (reset ? "PASS" : "FAIL") + "] secret state "
                + (reset ? "reset" : "not found"));
    }

    @Subcommand("blessing list")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void blessingList(CommandSender sender,
                             @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        var info = phaseSeven.info(id);
        if (info.isEmpty()) {
            sender.sendMessage("[FAIL] unknown blessing instance " + id);
            return;
        }
        sender.sendMessage("[PASS] instance=" + id + " blessingLevels=" + info.orElseThrow().blessingLevels());
    }

    @Subcommand("blessing add")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void blessingAdd(CommandSender sender,
                            @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                            @SuggestWith(BlessingIdSuggestionProvider.class) String blessingId,
                            @Optional Integer discoveries) {
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        int count = discoveries == null ? 1 : discoveries;
        final java.util.Optional<me.lidan.dungeonCrawlers.core.stats.BlessingLevels.DiscoveryResult> result;
        try {
            result = phaseSeven.addBlessing(id, blessingId, count);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage("[FAIL] " + exception.getMessage());
            return;
        }
        sender.sendMessage(result.map(value -> "[PASS] blessing=" + blessingId + " level=" + value.currentLevel()
                        + " atCap=" + value.atCap())
                .orElse("[FAIL] unknown instance or blessing"));
    }

    @Subcommand("blessing remove")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void blessingRemove(CommandSender sender,
                               @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                               @SuggestWith(BlessingIdSuggestionProvider.class) String blessingId) {
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        boolean removed = phaseSeven.removeBlessing(id, blessingId);
        sender.sendMessage("[" + (removed ? "PASS" : "FAIL") + "] blessing=" + blessingId);
    }

    @Subcommand("blessing clear")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void blessingClear(CommandSender sender,
                              @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        boolean cleared = phaseSeven.clearBlessings(id);
        sender.sendMessage("[" + (cleared ? "PASS" : "FAIL") + "] blessings cleared");
    }

    private static UUID parse(CommandSender sender, String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage("[FAIL] instance id must be a UUID");
            return null;
        }
    }
}
