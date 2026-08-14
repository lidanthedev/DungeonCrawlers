package me.lidan.dungeonCrawlers.commands;

import me.lidan.dungeonCrawlers.config.registry.ConfigRegistryService;
import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.party.PartySnapshotPolicy;
import me.lidan.dungeonCrawlers.integration.PartyProvider;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.UUID;

@Command("dungeon")
public final class DungeonGenerationCommand {
    private final ConfigRegistryService configRegistry;
    private final PartyProvider parties;
    private final PartySnapshotPolicy partyPolicy = new PartySnapshotPolicy();
    private final GenerationService generation;

    public DungeonGenerationCommand(ConfigRegistryService configRegistry, PartyProvider parties,
                                    GenerationService generation) {
        this.configRegistry = configRegistry;
        this.parties = parties;
        this.generation = generation;
    }

    @Subcommand("instance generate-debug")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void generateDebug(Player player, String floorId, long seed) {
        generate(player, floorId, seed, 0);
    }

    @Subcommand("instance generate-debug-slow")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void generateDebugSlow(Player player, String floorId, long seed, long delayMillis) {
        generate(player, floorId, seed, delayMillis);
    }

    @Subcommand("instance cancel")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void cancel(CommandSender sender, String instanceId) {
        report(sender, generation.cancel(uuid(instanceId)));
    }

    @Subcommand("instance cleanup")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void cleanup(CommandSender sender, String instanceId) {
        report(sender, generation.cleanup(uuid(instanceId)));
    }

    @Subcommand("instance info")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void info(CommandSender sender, String instanceId) {
        generation.info(uuid(instanceId)).ifPresentOrElse(value -> sender.sendMessage("[PASS] " + value),
                () -> sender.sendMessage("[FAIL] unknown instance " + instanceId));
    }

    @Subcommand("instance list")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void instances(CommandSender sender) {
        var instances = generation.instances();
        sender.sendMessage("[PASS] instances=" + instances.size());
        instances.forEach(instance -> sender.sendMessage(instance.toString()));
    }

    @Subcommand("slots")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void slots(CommandSender sender) {
        var slots = generation.slots();
        long free = slots.stream().filter(slot -> slot.state() == me.lidan.dungeonCrawlers.core.generation
                .SlotAllocator.SlotState.FREE).count();
        sender.sendMessage("[PASS] free=" + free + "/" + slots.size());
        slots.forEach(slot -> sender.sendMessage("slot=" + slot.id() + " state=" + slot.state()
                + " owner=" + slot.instanceId() + " origin=" + slot.origin() + " usable=" + slot.usableBounds()));
    }

    @Subcommand("recovery status")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void recoveryStatus(CommandSender sender) {
        sender.sendMessage("[PASS] " + generation.recoveryStatus());
    }

    @Subcommand("recovery run")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void recoveryRun(CommandSender sender) {
        report(sender, generation.recover());
    }

    private void generate(Player player, String floorId, long seed, long delayMillis) {
        var snapshot = configRegistry.snapshot();
        var floor = snapshot.floors().get(floorId);
        if (floor == null) {
            player.sendMessage("[FAIL] unknown floor " + floorId);
            return;
        }
        PartySnapshotPolicy.SnapshotResult party = partyPolicy.snapshot(player.getUniqueId(),
                parties.lookup(player.getUniqueId()), floor.limits().maxPartySize());
        if (!party.successful()) {
            player.sendMessage("[FAIL] " + party.error());
            return;
        }
        GenerationService.StartResult result = generation.start(new GenerationService.StartRequest(snapshot, floor,
                party.snapshot(), seed, delayMillis));
        if (!result.accepted()) {
            player.sendMessage("[FAIL] " + result.detail());
            return;
        }
        player.sendMessage("[PASS] instance=" + result.instanceId() + " slot=" + result.slotId()
                + " admitted; use /dungeon instance info " + result.instanceId());
    }

    private static void report(CommandSender sender, GenerationService.ActionResult result) {
        sender.sendMessage((result.successful() ? "[PASS] " : "[FAIL] ") + result.detail());
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("instance id must be a UUID");
        }
    }
}
