package me.lidan.dungeonCrawlers.commands;

import me.lidan.dungeonCrawlers.core.combat.CombatRoomService;
import org.bukkit.command.CommandSender;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.UUID;

/** Administrative room and required-mob controls for Phase 6 diagnostics. */
@Command("dungeon")
public final class DungeonPhaseSixCommand {
    private final CombatRoomService combat;

    public DungeonPhaseSixCommand(CombatRoomService combat) {
        this.combat = combat;
    }

    @Subcommand("room activate")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void roomActivate(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                             int roomIndex) {
        UUID id = parse(sender, instanceId);
        if (id != null) send(sender, combat.activate(id, roomIndex));
    }

    @Subcommand("room clear")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void roomClear(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                          int roomIndex) {
        UUID id = parse(sender, instanceId);
        if (id != null) send(sender, combat.clear(id, roomIndex));
    }

    @Subcommand("mob list")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void mobList(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                        @Optional Integer roomIndex) {
        UUID id = parse(sender, instanceId);
        if (id == null) return;
        var rooms = combat.rooms(id);
        if (rooms.isEmpty()) {
            sender.sendMessage("[FAIL] unknown combat instance " + id);
            return;
        }
        rooms.stream().filter(room -> roomIndex == null || room.index() == roomIndex).forEach(room -> {
            if (roomIndex == null) {
                long alive = room.requiredMobs().stream().filter(mob -> mob.state() == CombatRoomService.MobState.ALIVE).count();
                long dead = room.requiredMobs().stream().filter(mob -> mob.state() == CombatRoomService.MobState.DEAD).count();
                long missing = room.requiredMobs().stream().filter(mob -> mob.state() == CombatRoomService.MobState.MISSING).count();
                long failed = room.requiredMobs().stream().filter(mob -> mob.state() == CombatRoomService.MobState.FAILED).count();
                sender.sendMessage("[PASS] room=" + room.index() + " state=" + room.state()
                        + " encounter=" + (room.encounter() == null ? "none" : room.encounter())
                        + " required=" + room.requiredMobs().size() + " alive=" + alive + " dead=" + dead
                        + " missing=" + missing + " failed=" + failed + " detail=" + room.detail());
            } else {
                sender.sendMessage("[PASS] room=" + room.index() + " state=" + room.state()
                        + " encounter=" + (room.encounter() == null ? "none" : room.encounter())
                        + " detail=" + room.detail());
                room.requiredMobs().forEach(mob -> sender.sendMessage("  mob=" + mob.mobId()
                        + " state=" + mob.state() + " entity=" + mob.entityId()
                        + (mob.adminSuppressed() ? " suppressed" : "")));
            }
        });
        if (roomIndex != null && rooms.stream().noneMatch(room -> room.index() == roomIndex)) {
            sender.sendMessage("[FAIL] unknown combat room " + roomIndex);
        }
    }

    @Subcommand("mob spawn")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void mobSpawn(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                          int roomIndex, String mobId) {
        UUID id = parse(sender, instanceId);
        if (id != null) send(sender, combat.spawn(id, roomIndex, mobId));
    }

    @Subcommand("mob kill")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void mobKill(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                         int roomIndex, UUID entityId) {
        UUID id = parse(sender, instanceId);
        if (id != null) send(sender, combat.kill(id, roomIndex, entityId));
    }

    @Subcommand("mob remove")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void mobRemove(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId,
                           int roomIndex, UUID entityId) {
        UUID id = parse(sender, instanceId);
        if (id != null) send(sender, combat.remove(id, roomIndex, entityId));
    }

    @Subcommand("mob reconcile")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void mobReconcile(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        UUID id = parse(sender, instanceId);
        if (id != null) send(sender, combat.reconcile(id));
    }

    private static UUID parse(CommandSender sender, String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage("[FAIL] instance id must be a UUID");
            return null;
        }
    }

    private static void send(CommandSender sender, Object result) {
        if (result == null) {
            sender.sendMessage("[FAIL] instance id must be a UUID");
            return;
        }
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
            default -> result.toString();
        };
        sender.sendMessage("[" + (successful ? "PASS" : "FAIL") + "] " + detail);
    }
}
