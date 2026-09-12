package me.lidan.dungeonCrawlers.commands;

import me.lidan.dungeonCrawlers.core.combat.CombatRoomService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.integration.DungeonMessages;
import org.bukkit.command.CommandSender;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Administrative room and required-mob controls for Phase 6 diagnostics. */
@Command("dungeon")
public final class DungeonPhaseSixCommand {
    private final CombatRoomService combat;
    private final RunPreparationService runs;
    private final BooleanSupplier debugEnabled;

    public DungeonPhaseSixCommand(CombatRoomService combat, RunPreparationService runs) {
        this(combat, runs, () -> false);
    }

    public DungeonPhaseSixCommand(CombatRoomService combat, RunPreparationService runs,
                                  BooleanSupplier debugEnabled) {
        this.combat = combat;
        this.runs = runs;
        this.debugEnabled = debugEnabled;
    }

    @Subcommand("room activate")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void roomActivate(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                             int roomIndex) {
        if (!requireDebug(sender)) return;
        UUID id = parse(sender, instanceId);
        if (id != null) send(sender, combat.activate(id, roomIndex));
    }

    @Subcommand("room clear")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void roomClear(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                          int roomIndex) {
        if (!requireDebug(sender)) return;
        UUID id = parse(sender, instanceId);
        if (id != null) send(sender, combat.clear(id, roomIndex));
    }

    @Subcommand("mob list")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void mobList(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                        @Optional Integer roomIndex) {
        if (!requireDebug(sender)) return;
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        var rooms = combat.rooms(id);
        if (rooms.isEmpty()) {
            DungeonMessages.send(sender, DungeonMessages.error("No combat data is available for dungeon <white>"
                    + id + "</white>."));
            return;
        }
        rooms.stream().filter(room -> roomIndex == null || room.index() == roomIndex).forEach(room -> {
            if (roomIndex == null) {
                long alive = room.requiredMobs().stream().filter(mob -> mob.state() == CombatRoomService.MobState.ALIVE).count();
                long dead = room.requiredMobs().stream().filter(mob -> mob.state() == CombatRoomService.MobState.DEAD).count();
                long missing = room.requiredMobs().stream().filter(mob -> mob.state() == CombatRoomService.MobState.MISSING).count();
                long failed = room.requiredMobs().stream().filter(mob -> mob.state() == CombatRoomService.MobState.FAILED).count();
                sendBlock(sender,
                        "<aqua><bold>Room " + room.index() + "</bold></aqua>",
                        "<gray>Status: <white>" + displayName(room.state().name()) + "</white></gray>",
                        "<gray>Encounter: <white>" + (room.encounter() == null ? "None"
                                : displayName(room.encounter().name())) + "</white></gray>",
                        "<gray>Required mobs: <white>" + room.requiredMobs().size() + "</white></gray>",
                        "<gray>Alive: <white>" + alive + "</white> · Defeated: <white>" + dead
                                + "</white> · Missing: <white>" + missing + "</white> · Failed: <white>" + failed + "</white></gray>",
                        "<gray>Details: <white>" + readableDetail(room.detail()) + "</white></gray>");
            } else {
                List<String> lines = new ArrayList<>();
                lines.add("<aqua><bold>Room " + room.index() + "</bold></aqua>");
                lines.add("<gray>Status: <white>" + displayName(room.state().name()) + "</white></gray>");
                lines.add("<gray>Encounter: <white>" + (room.encounter() == null ? "None"
                        : displayName(room.encounter().name())) + "</white></gray>");
                lines.add("<gray>Details: <white>" + readableDetail(room.detail()) + "</white></gray>");
                room.requiredMobs().forEach(mob -> lines.add("<gray>Mob <white>" + mob.mobId()
                        + "</white>: <white>" + displayName(mob.state().name()) + "</white> · Entity: <white>"
                        + (mob.entityId() == null ? "None" : mob.entityId()) + "</white>"
                        + (mob.adminSuppressed() ? " · <yellow>Progression suppressed</yellow>" : "")
                        + "</gray>"));
                DungeonMessages.send(sender, String.join("\n", lines));
            }
        });
        if (roomIndex != null && rooms.stream().noneMatch(room -> room.index() == roomIndex)) {
            DungeonMessages.send(sender, DungeonMessages.error("Combat room <white>" + roomIndex
                    + "</white> was not found."));
        }
    }

    @Subcommand("mob spawn")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void mobSpawn(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                          int roomIndex, String mobId) {
        if (!requireDebug(sender)) return;
        UUID id = parse(sender, instanceId);
        if (id != null) send(sender, combat.spawn(id, roomIndex, mobId));
    }

    @Subcommand("mob kill")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void mobKill(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                         int roomIndex, UUID entityId) {
        if (!requireDebug(sender)) return;
        UUID id = parse(sender, instanceId);
        if (id != null) send(sender, combat.kill(id, roomIndex, entityId));
    }

    @Subcommand("mob remove")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void mobRemove(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                           int roomIndex, UUID entityId) {
        if (!requireDebug(sender)) return;
        UUID id = parse(sender, instanceId);
        if (id != null) send(sender, combat.remove(id, roomIndex, entityId));
    }

    @Subcommand("mob reconcile")
    @CommandPermission("dungeoncrawlers.admin.debug")
    public void mobReconcile(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        if (!requireDebug(sender)) return;
        UUID id = parse(sender, instanceId);
        if (id != null) send(sender, combat.reconcile(id));
    }

    private UUID parse(CommandSender sender, String value) {
        return DungeonInstanceResolver.resolveOrNotify(sender, value, runs);
    }

    private static void send(CommandSender sender, Object result) {
        boolean successful = switch (result) {
            case CombatRoomService.ActivationResult value -> value.successful();
            case CombatRoomService.ClearResult value -> value.successful();
            case CombatRoomService.AdminResult value -> value.successful();
            case CombatRoomService.ReconcileResult value -> value.successful();
            default -> false;
        };
        String detail = switch (result) {
            case CombatRoomService.ActivationResult value -> value.detail();
            case CombatRoomService.ClearResult value -> value.detail();
            case CombatRoomService.AdminResult value -> value.detail();
            case CombatRoomService.ReconcileResult value -> value.detail();
            default -> "The combat command returned an unsupported result.";
        };
        DungeonMessages.send(sender, successful ? DungeonMessages.success(readableDetail(detail))
                : DungeonMessages.error(readableDetail(detail)));
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

    private boolean requireDebug(CommandSender sender) {
        if (debugEnabled.getAsBoolean()) return true;
        DungeonMessages.send(sender, DungeonMessages.warning(
                "This administrative test command is unavailable while debug mode is disabled."));
        return false;
    }
}
