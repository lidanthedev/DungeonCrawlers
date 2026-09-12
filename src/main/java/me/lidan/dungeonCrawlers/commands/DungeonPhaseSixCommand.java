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
            DungeonMessages.send(sender, DungeonMessages.error("Unknown combat instance: <white>" + id + "</white>"));
            return;
        }
        rooms.stream().filter(room -> roomIndex == null || room.index() == roomIndex).forEach(room -> {
            if (roomIndex == null) {
                long alive = room.requiredMobs().stream().filter(mob -> mob.state() == CombatRoomService.MobState.ALIVE).count();
                long dead = room.requiredMobs().stream().filter(mob -> mob.state() == CombatRoomService.MobState.DEAD).count();
                long missing = room.requiredMobs().stream().filter(mob -> mob.state() == CombatRoomService.MobState.MISSING).count();
                long failed = room.requiredMobs().stream().filter(mob -> mob.state() == CombatRoomService.MobState.FAILED).count();
                DungeonMessages.send(sender, "<gray>room=<white>" + room.index() + "</white> state=<white>"
                        + room.state().name().toLowerCase() + "</white>"
                        + " encounter=<white>" + (room.encounter() == null ? "none" : room.encounter())
                        + " required=<white>" + room.requiredMobs().size() + "</white> alive=<white>" + alive
                        + "</white> dead=<white>" + dead + "</white> missing=<white>" + missing
                        + "</white> failed=<white>" + failed + "</white> detail=<white>" + room.detail()
                        + "</white></gray>");
            } else {
                DungeonMessages.send(sender, "<gray>room=<white>" + room.index() + "</white> state=<white>"
                        + room.state().name().toLowerCase() + "</white>"
                        + " encounter=<white>" + (room.encounter() == null ? "none" : room.encounter())
                        + " detail=<white>" + room.detail() + "</white></gray>");
                room.requiredMobs().forEach(mob -> DungeonMessages.send(sender, "<gray>  mob=<white>"
                        + mob.mobId() + "</white> state=<white>" + mob.state().name().toLowerCase()
                        + "</white> entity=<white>" + (mob.entityId() == null ? "none" : mob.entityId())
                        + "</white>" + (mob.adminSuppressed() ? " <yellow>suppressed</yellow>" : "")
                        + "</gray>"));
            }
        });
        if (roomIndex != null && rooms.stream().noneMatch(room -> room.index() == roomIndex)) {
            DungeonMessages.send(sender, DungeonMessages.error("Unknown combat room: <white>" + roomIndex + "</white>"));
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
            default -> "unsupported combat result";
        };
        DungeonMessages.send(sender, successful ? DungeonMessages.success(detail) : DungeonMessages.error(detail));
    }

    private boolean requireDebug(CommandSender sender) {
        if (debugEnabled.getAsBoolean()) return true;
        DungeonMessages.send(sender, DungeonMessages.warning(
                "This is a debug-only command and is disabled while config.yml debug is false."));
        return false;
    }
}
