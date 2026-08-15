package me.lidan.dungeonCrawlers.commands;

import me.lidan.cavecrawlers.utils.MiniMessageUtils;
import me.lidan.dungeonCrawlers.config.registry.ConfigRegistryService;
import me.lidan.dungeonCrawlers.core.generation.GenerationService;
import me.lidan.dungeonCrawlers.core.generation.SlotAllocator;
import me.lidan.dungeonCrawlers.core.party.PartySnapshotPolicy;
import me.lidan.dungeonCrawlers.core.protection.TeleportPermitService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.integration.PartyProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

@Command("dungeon")
public final class DungeonGenerationCommand {
    private final ConfigRegistryService configRegistry;
    private final PartyProvider parties;
    private final PartySnapshotPolicy partyPolicy = new PartySnapshotPolicy();
    private final GenerationService generation;
    private final Server server;
    private final String generationWorldName;
    private final TeleportPermitService teleportPermits;
    private final Clock clock;

    public DungeonGenerationCommand(ConfigRegistryService configRegistry, PartyProvider parties,
                                    GenerationService generation, Server server, String generationWorldName,
                                    TeleportPermitService teleportPermits, Clock clock) {
        this.configRegistry = configRegistry;
        this.parties = parties;
        this.generation = generation;
        this.server = server;
        this.generationWorldName = generationWorldName;
        this.teleportPermits = teleportPermits;
        this.clock = clock;
        InstanceIdSuggestionProvider.setSource(() -> this.generation.instances().stream()
                .map(GenerationService.InstanceSnapshot::instanceId)
                .map(UUID::toString)
                .toList());
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
    public void cancel(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        report(sender, generation.cancel(uuid(instanceId)));
    }

    @Subcommand("instance cleanup")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void cleanup(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        report(sender, generation.cleanup(uuid(instanceId)));
    }

    @Subcommand("instance info")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void info(CommandSender sender, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        UUID id = uuid(instanceId);
        generation.info(id).ifPresentOrElse(value -> {
            SlotAllocator.SlotLease slot = slotFor(id);
            GenerationService.PlayerSpawn spawn = generation.playerSpawn(id).orElse(null);
            if (slot == null) {
                sender.sendMessage("[PASS] " + value);
                return;
            }
            suggest(sender, "<green>[PASS]</green> " + value + " origin=" + slot.origin()
                    + " usable=" + slot.usableBounds() + (spawn == null ? "" : " spawn=" + spawn.point()
                    + " facingYaw=" + spawn.yaw())
                    + " <gray>(click to teleport)</gray>",
                    "/dungeon instance tp " + id);
        }, () -> sender.sendMessage("[FAIL] unknown instance " + instanceId));
    }

    @Subcommand("instance tp")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void teleport(Player player, @SuggestWith(InstanceIdSuggestionProvider.class) String instanceId) {
        try {
            UUID id = uuid(instanceId);
            generation.info(id)
                    .orElseThrow(() -> new IllegalArgumentException("unknown instance " + instanceId));
            SlotAllocator.SlotLease slot = slotFor(id);
            if (slot == null) throw new IllegalStateException("instance slot is no longer available");
            GenerationService.PlayerSpawn spawn = generation.playerSpawn(id)
                    .orElseThrow(() -> new IllegalStateException("instance has no generated EMERALD_BLOCK player spawn"));
            World world = server.getWorld(generationWorldName);
            if (world == null) throw new IllegalStateException("generation world is not loaded: " + generationWorldName);

            Point destinationPoint = new Point(spawn.point().x(), spawn.point().y() + 1, spawn.point().z());
            teleportPermits.authorize(player.getUniqueId(), Set.of(
                    new TeleportPermitService.Destination(generationWorldName, destinationPoint)),
                    clock.instant().plusSeconds(5));
            Location destination = new Location(world, spawn.point().x() + 0.5,
                    spawn.point().y() + 1.0, spawn.point().z() + 0.5, spawn.yaw(), 0.0f);
            if (!player.teleport(destination)) {
                player.sendMessage("[FAIL] teleport to instance " + id + " was rejected");
                return;
            }
            suggest(player, "<green>[PASS]</green> teleported to instance " + id
                    + " <gray>(click for instance info)</gray>", "/dungeon instance info " + id);
        } catch (RuntimeException exception) {
            player.sendMessage("[FAIL] " + message(exception));
        }
    }

    @Subcommand("instance list")
    @CommandPermission("dungeoncrawlers.admin.generation")
    public void instances(CommandSender sender) {
        var instances = generation.instances();
        sender.sendMessage("[PASS] instances=" + instances.size());
        instances.forEach(instance -> {
            SlotAllocator.SlotLease slot = slotFor(instance.instanceId());
            GenerationService.PlayerSpawn spawn = generation.playerSpawn(instance.instanceId()).orElse(null);
            String suffix = slot == null ? "" : " origin=" + slot.origin() + " usable=" + slot.usableBounds();
            if (spawn != null) suffix += " spawn=" + spawn.point() + " facingYaw=" + spawn.yaw();
            suggest(sender, instance + suffix, "/dungeon instance info " + instance.instanceId());
        });
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
        suggest(player, "<green>[PASS]</green> instance=" + result.instanceId() + " slot=" + result.slotId()
                + " admitted; <gray>(click for instance info)</gray>",
                "/dungeon instance info " + result.instanceId());
    }

    private SlotAllocator.SlotLease slotFor(UUID instanceId) {
        return generation.slots().stream()
                .filter(slot -> instanceId.equals(slot.instanceId()))
                .findFirst()
                .orElse(null);
    }

    static void suggest(CommandSender sender, String message, String command) {
        Component component = MiniMessageUtils.miniMessage(message)
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(MiniMessageUtils.miniMessage("<gray>Suggest <white>" + command
                        + "</white></gray>")));
        sender.sendMessage(component);
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

    private static String message(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }
}
