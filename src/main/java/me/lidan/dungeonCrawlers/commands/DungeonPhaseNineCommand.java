package me.lidan.dungeonCrawlers.commands;

import me.lidan.cavecrawlers.utils.MiniMessageUtils;
import me.lidan.dungeonCrawlers.core.portal.PortalEncounterService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import org.bukkit.command.CommandSender;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.UUID;
import java.util.function.Consumer;

/** Administrative portal and boss encounter diagnostics for Phase 9. */
@Command("dungeon")
public final class DungeonPhaseNineCommand {
    private final PortalEncounterService encounters;
    private final RunPreparationService runs;
    private final Consumer<UUID> cleanupRun;

    public DungeonPhaseNineCommand(PortalEncounterService encounters, RunPreparationService runs) {
        this(encounters, runs, ignored -> { });
    }

    public DungeonPhaseNineCommand(PortalEncounterService encounters, RunPreparationService runs,
                                   Consumer<UUID> cleanupRun) {
        this.encounters = java.util.Objects.requireNonNull(encounters, "encounters");
        this.runs = java.util.Objects.requireNonNull(runs, "runs");
        this.cleanupRun = java.util.Objects.requireNonNull(cleanupRun, "cleanupRun");
    }

    @Subcommand("portal start")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void portalStart(CommandSender sender,
                            @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        UUID id = parse(sender, instanceId);
        if (id != null) send(sender, encounters.startPortal(id));
    }

    @Subcommand("portal abort")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void portalAbort(CommandSender sender,
                            @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        UUID id = parse(sender, instanceId);
        if (id != null) send(sender, encounters.abortPortal(id));
    }

    @Subcommand("portal status")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void portalStatus(CommandSender sender,
                             @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        UUID id = parse(sender, instanceId);
        if (id != null) send(sender, encounters.status(id));
    }

    @Subcommand("boss info")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void bossInfo(CommandSender sender,
                         @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        PortalEncounterService.BossResult result = encounters.status(id);
        send(sender, result);
        if (result.successful() && result.snapshot() != null) {
            sender.sendMessage(MiniMessageUtils.miniMessage("<gray>status=<white>"
                    + result.snapshot().status() + "</white> reward=<white>"
                    + result.snapshot().rewardChest() + "</white></gray>"));
        }
    }

    @Subcommand("boss start")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void bossStart(CommandSender sender,
                          @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        UUID id = parse(sender, instanceId);
        if (id != null) send(sender, encounters.startBoss(id));
    }

    @Subcommand("boss kill")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void bossKill(CommandSender sender,
                         @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        UUID id = parse(sender, instanceId);
        if (id != null) send(sender, encounters.killBoss(id));
    }

    @Subcommand("boss cleanup")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void bossCleanup(CommandSender sender,
                            @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        if (encounters.cleanup(id)) {
            cleanupRun.accept(id);
            sender.sendMessage(MiniMessageUtils.miniMessage("<green>[PASS] boss and portal encounter cleaned</green>"));
        } else {
            sender.sendMessage(MiniMessageUtils.miniMessage("<red>[FAIL] no portal encounter registered for instance " + id + "</red>"));
        }
    }

    private UUID parse(CommandSender sender, String value) {
        return DungeonInstanceResolver.resolveOrNotify(sender, value, runs);
    }

    private static void send(CommandSender sender, Object result) {
        boolean successful;
        String detail;
        if (result instanceof PortalEncounterService.PortalResult value) {
            successful = value.successful(); detail = value.detail();
        } else if (result instanceof PortalEncounterService.BossResult value) {
            successful = value.successful(); detail = value.detail();
        } else {
            successful = false; detail = String.valueOf(result);
        }
        sender.sendMessage(MiniMessageUtils.miniMessage("<" + (successful ? "green" : "red") + ">["
                + (successful ? "PASS" : "FAIL") + "] " + detail + "</" + (successful ? "green" : "red") + ">"));
    }
}
