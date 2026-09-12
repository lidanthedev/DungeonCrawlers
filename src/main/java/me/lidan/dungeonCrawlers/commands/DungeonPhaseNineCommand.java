package me.lidan.dungeonCrawlers.commands;

import me.lidan.dungeonCrawlers.core.portal.PortalEncounterService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.integration.DungeonMessages;
import org.bukkit.command.CommandSender;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.Locale;
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
            DungeonMessages.send(sender, String.join("\n",
                    "<aqua><bold>Boss encounter</bold></aqua>",
                    "<gray>Status: <white>" + displayName(result.snapshot().status().name()) + "</white></gray>",
                    "<gray>Reward chest: <white>" + point(result.snapshot().rewardChest()) + "</white></gray>"));
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
            DungeonMessages.send(sender, DungeonMessages.success("Boss and portal encounter cleaned."));
        } else {
            DungeonMessages.send(sender, DungeonMessages.error("No portal encounter is registered for instance <white>"
                    + id + "</white>."));
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
            successful = false; detail = "unsupported portal operation result";
        }
        String readable = readableDetail(detail);
        DungeonMessages.send(sender, successful ? DungeonMessages.success(readable) : DungeonMessages.error(readable));
    }

    private static String displayName(String value) {
        String readable = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(readable.charAt(0)) + readable.substring(1);
    }

    private static String readableDetail(String detail) {
        String readable = detail
                .replace("boss encounter preparing encounter=", "Boss encounter is preparing. Encounter: ")
                .replace("boss encounter active encounter=", "Boss encounter is active. Encounter: ")
                .replace("boss defeated; reward location=", "Boss defeated. Reward chest: ")
                .replace("portal countdown started by ", "Boss countdown started by ")
                .replace("portal countdown aborted by ", "Boss countdown cancelled by ")
                .replace("portal countdown already active", "The boss countdown is already active.")
                .replace("portal countdown is not active", "The boss countdown is not active.")
                .replace("boss encounter already active", "The boss encounter is already active.")
                .replace("boss already defeated", "The boss has already been defeated.")
                .replace("unknown portal instance ", "Unknown dungeon: ")
                .replace("central update is not registered for this instance", "The boss portal is temporarily unavailable.")
                .replace("run is not running", "The dungeon is not currently running.")
                .replace("; ", " · ")
                .replace("=", ": ");
        if (readable.isEmpty()) return readable;
        return Character.toUpperCase(readable.charAt(0)) + readable.substring(1);
    }

    private static String point(me.lidan.dungeonCrawlers.core.template.TemplateModels.Point point) {
        return point.x() + ", " + point.y() + ", " + point.z();
    }
}
