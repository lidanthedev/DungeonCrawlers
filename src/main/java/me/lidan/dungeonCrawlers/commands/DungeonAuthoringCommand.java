package me.lidan.dungeonCrawlers.commands;

import me.lidan.cavecrawlers.utils.BoostedCustomConfig;
import me.lidan.dungeonCrawlers.authoring.TemplateAuthoringService;
import me.lidan.dungeonCrawlers.authoring.TemplateCatalogLoader;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.EncounterCapability;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomType;
import me.lidan.dungeonCrawlers.config.registry.ConfigRegistryService;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner;
import me.lidan.dungeonCrawlers.core.reservation.PlayerReservationService;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.EmeraldPolicy;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Rotation;
import me.lidan.dungeonCrawlers.core.template.TemplateValidator;
import me.lidan.dungeonCrawlers.integration.WorldEditGateway;
import me.lidan.dungeonCrawlers.integration.worldedit.WorldEditAdapter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Command("dungeon")
public final class DungeonAuthoringCommand {
    private final ConfigRegistryService configRegistry;
    private final PlayerReservationService reservations;
    private final TemplateAuthoringService authoring;
    private final WorldEditGateway worldEdit = new WorldEditAdapter();
    private final TemplateValidator templateValidator = new TemplateValidator();
    private final TemplateCatalogLoader templateCatalog;
    private final LayoutPlanner layoutPlanner = new LayoutPlanner();
    private final Map<UUID, List<String>> generationTraces = new ConcurrentHashMap<>();
    private final EmeraldPolicy emeraldPolicy;
    private final Supplier<Set<String>> activeTemplates;

    public DungeonAuthoringCommand(BoostedCustomConfig mainConfig, ConfigRegistryService configRegistry,
                                   PlayerReservationService reservations, TemplateAuthoringService authoring) {
        this(mainConfig, configRegistry, reservations, authoring, Set::of);
    }

    public DungeonAuthoringCommand(BoostedCustomConfig mainConfig, ConfigRegistryService configRegistry,
                                   PlayerReservationService reservations, TemplateAuthoringService authoring,
                                   Supplier<Set<String>> activeTemplates) {
        this.configRegistry = configRegistry;
        this.reservations = reservations;
        this.authoring = authoring;
        this.activeTemplates = activeTemplates;
        this.emeraldPolicy = configuredEmeraldPolicy(mainConfig);
        this.templateCatalog = new TemplateCatalogLoader(authoring, worldEdit, templateValidator, emeraldPolicy);
    }

    @Subcommand("selection validate")
    @CommandPermission("dungeoncrawlers.admin.authoring")
    public void selectionValidate(Player player, String roomType, String encounters) {
        sendMarkerLegend(player);
        RoomType type;
        Set<EncounterCapability> capabilities;
        try {
            type = RoomType.valueOf(roomType.toUpperCase(Locale.ROOT));
            capabilities = parseCapabilities(encounters);
        } catch (IllegalArgumentException exception) {
            player.sendMessage("[FAIL] type must be one of " + List.of(RoomType.values())
                    + "; encounters must be none, normal, miniboss, or normal,miniboss");
            return;
        }
        WorldEditGateway.ScanResult scan = scanSelection(player);
        if (!scan.successful()) {
            player.sendMessage("[FAIL] " + scan.detail());
            return;
        }
        var result = templateValidator.validate("selection", type, capabilities,
                scan.selection().orElseThrow(), emeraldPolicy);
        result.errors().forEach(error -> player.sendMessage("[FAIL] " + error));
        if (result.successful()) {
            var template = result.template().orElseThrow();
            player.sendMessage("[PASS] " + scan.detail() + ", contentHash=" + template.contentHash()
                    + ", secrets=" + template.secrets().size() + ", portalBlocks=" + template.portalBlocks().size());
        }
    }

    @Subcommand("selection markers")
    @CommandPermission("dungeoncrawlers.admin.authoring")
    public void selectionMarkers(Player player) {
        sendMarkerLegend(player);
        WorldEditGateway.ScanResult scan = scanSelection(player);
        if (!scan.successful()) {
            player.sendMessage("[FAIL] " + scan.detail());
            return;
        }
        var markers = scan.selection().orElseThrow().blocks().entrySet().stream()
                .filter(entry -> isMarker(entry.getValue().type()))
                .sorted(Map.Entry.comparingByKey()).toList();
        if (markers.isEmpty()) {
            player.sendMessage("[FAIL] selection contains no DungeonCrawlers markers");
            return;
        }
        player.sendMessage("[PASS] markers=" + markers.size() + ", " + scan.detail());
        markers.forEach(entry -> player.sendMessage(entry.getKey() + " " + entry.getValue().type()
                + (entry.getValue().is("jigsaw") ? " states=" + entry.getValue().states()
                + " nbt=" + entry.getValue().nbt() : "")));
    }

    static List<String> markerLegend() {
        return List.of(
                "[INFO] DungeonCrawlers marker blocks:",
                "- Entrance: JIGSAW named dungeoncrawlers:entrance",
                "- Exit/door: JIGSAW named dungeoncrawlers:exit",
                "- Normal mob: GRAY_CONCRETE_POWDER",
                "- Miniboss mob: YELLOW_CONCRETE_POWDER",
                "- Player spawn/teleport: EMERALD_BLOCK",
                "- Boss spawn: RED_CONCRETE_POWDER",
                "- Reward chest: LIME_CONCRETE_POWDER",
                "- Secret: CHEST; blessing secret: TRAPPED_CHEST",
                "- Portal trigger: connected NETHER_PORTAL blocks",
                "- Jigsaw target=dungeoncrawlers:connector, pool=minecraft:empty, final_state=minecraft:air,"
                        + " orientation=north_up/east_up/south_up/west_up"
        );
    }

    private static void sendMarkerLegend(Player player) {
        markerLegend().forEach(player::sendMessage);
    }

    @Subcommand("room create")
    @CommandPermission("dungeoncrawlers.admin.authoring")
    public void roomCreate(Player player, String id, String roomType, String encounters) {
        RoomType type;
        Set<EncounterCapability> capabilities;
        try {
            type = RoomType.valueOf(roomType.toUpperCase(Locale.ROOT));
            capabilities = parseCapabilities(encounters);
        } catch (IllegalArgumentException exception) {
            player.sendMessage("[FAIL] invalid room type or encounters");
            return;
        }
        WorldEditGateway.CaptureResult capture = captureSelection(player);
        if (!capture.successful()) {
            player.sendMessage("[FAIL] " + capture.detail());
            return;
        }
        var validation = templateValidator.validate(id, type, capabilities, capture.selection().orElseThrow(),
                emeraldPolicy);
        if (!validation.successful()) {
            validation.errors().forEach(error -> player.sendMessage("[FAIL] " + error));
            return;
        }
        TemplateAuthoringService.OperationResult result = authoring.create(id, type, capabilities, capture.schematic());
        player.sendMessage("[" + (result.successful() ? "PASS" : "FAIL") + "] " + result.detail());
        if (result.successful()) reportAuthoringReload(player);
    }

    @Subcommand("room update")
    @CommandPermission("dungeoncrawlers.admin.authoring")
    public void roomUpdate(Player player, String id) {
        var definition = configRegistry.snapshot().rooms().get(id);
        if (definition == null) {
            player.sendMessage("[FAIL] unknown room " + id);
            return;
        }
        WorldEditGateway.CaptureResult capture = captureSelection(player);
        if (!capture.successful()) {
            player.sendMessage("[FAIL] " + capture.detail());
            return;
        }
        var validation = templateValidator.validate(id, definition.type(), definition.capabilities(),
                capture.selection().orElseThrow(), emeraldPolicy);
        if (!validation.successful()) {
            validation.errors().forEach(error -> player.sendMessage("[FAIL] " + error));
            return;
        }
        TemplateAuthoringService.OperationResult result = authoring.update(id, capture.schematic());
        player.sendMessage("[" + (result.successful() ? "PASS" : "FAIL") + "] " + result.detail());
    }

    @Subcommand("room delete")
    @CommandPermission("dungeoncrawlers.admin.authoring")
    public void roomDelete(CommandSender sender, String id) {
        TemplateAuthoringService.OperationResult result = authoring.delete(id, activeTemplates.get());
        sender.sendMessage("[" + (result.successful() ? "PASS" : "FAIL") + "] " + result.detail());
        if (result.successful()) reportAuthoringReload(sender);
    }

    @Subcommand("room paste")
    @CommandPermission("dungeoncrawlers.admin.authoring")
    public void roomPaste(Player player, String id, String rotationValue) {
        try {
            Rotation rotation = parseRotation(rotationValue);
            byte[] schematic = authoring.schematic(id);
            Point origin = playerPoint(player);
            WorldEditGateway.OperationResult result = worldEdit.paste(player, schematic, origin, rotation);
            player.sendMessage("[" + (result.successful() ? "PASS" : "FAIL") + "] " + result.detail());
        } catch (Exception exception) {
            player.sendMessage("[FAIL] " + (exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage()));
        }
    }

    @Subcommand("connect-test")
    @CommandPermission("dungeoncrawlers.admin.authoring")
    public void connectTest(Player player, String fromId, String toId, String fromRotation) {
        TemplateCatalogLoader.LoadResult loaded = templateCatalog.load(configRegistry.snapshot());
        if (!loaded.successful()) {
            loaded.errors().forEach(error -> player.sendMessage("[FAIL] " + error));
            return;
        }
        var catalog = loaded.catalog().orElseThrow();
        var from = catalog.get(fromId);
        var to = catalog.get(toId);
        if (from == null || to == null) {
            player.sendMessage("[FAIL] unknown authored room; from=" + fromId + ", to=" + toId);
            return;
        }
        try {
            var result = layoutPlanner.connectTest(from.template(), parseRotation(fromRotation), playerPoint(player),
                    to.template());
            if (!result.successful()) {
                player.sendMessage("[FAIL] " + result.detail());
                return;
            }
            var placement = result.placement().orElseThrow();
            var connection = result.connection().orElseThrow();
            player.sendMessage("[PASS] origin=" + placement.origin() + ", rotation=" + placement.rotation()
                    + ", DoorBounds=" + connection.doorBounds().size() + ", EntranceBounds="
                    + connection.entranceBounds().size() + ", ConnectionBounds=" + connection.bounds().size());
        } catch (IllegalArgumentException exception) {
            player.sendMessage("[FAIL] " + exception.getMessage());
        }
    }

    @Subcommand("generation plan")
    @CommandPermission("dungeoncrawlers.admin.authoring")
    public void generationPlan(Player player, String floorId, long seed) {
        var snapshot = configRegistry.snapshot();
        var floor = snapshot.floors().get(floorId);
        if (floor == null) {
            player.sendMessage("[FAIL] unknown floor " + floorId);
            return;
        }
        TemplateCatalogLoader.LoadResult loaded = templateCatalog.load(snapshot);
        if (!loaded.successful()) {
            loaded.errors().forEach(error -> player.sendMessage("[FAIL] " + error));
            return;
        }
        Point origin = playerPoint(player);
        Bounds slot = new Bounds(origin.add(new Point(-4_500, -128, -4_500)),
                origin.add(new Point(4_500, 512, 4_500)));
        UUID instanceId = UUID.nameUUIDFromBytes((floorId + ":" + seed + ":" + snapshot.hash())
                .getBytes(StandardCharsets.UTF_8));
        var result = layoutPlanner.plan(new LayoutPlanner.PlanRequest(instanceId, seed, floor,
                loaded.catalog().orElseThrow(), origin, slot, snapshot.hash()));
        generationTraces.put(player.getUniqueId(), result.trace());
        if (!result.successful()) {
            result.errors().forEach(error -> player.sendMessage("[FAIL] " + error));
            return;
        }
        var plan = result.plan().orElseThrow();
        player.sendMessage("[PASS] version=" + plan.algorithmVersion() + ", seed=" + seed + ", config="
                + plan.configHash() + ", content=" + plan.contentHash() + ", placements="
                + plan.placements().size() + ", connections=" + plan.connections().size());
        plan.placements().forEach(placement -> player.sendMessage("[" + placement.index() + "] "
                + placement.type() + " " + placement.templateId() + " origin=" + placement.origin()
                + " rotation=" + placement.rotation() + " encounter=" + placement.encounter()));
    }

    @Subcommand("generation trace")
    @CommandPermission("dungeoncrawlers.admin.authoring")
    public void generationTrace(Player player) {
        List<String> trace = generationTraces.get(player.getUniqueId());
        if (trace == null) {
            player.sendMessage("[FAIL] no generation plan trace in this plugin session");
            return;
        }
        player.sendMessage("[PASS] trace lines=" + trace.size());
        trace.forEach(player::sendMessage);
    }

    private WorldEditGateway.ScanResult scanSelection(Player player) {
        int maximumDimension = configRegistry.snapshot().floors().values().stream()
                .mapToInt(floor -> floor.limits().maxTemplateDimension()).max().orElse(512);
        long maximumVolume = configRegistry.snapshot().floors().values().stream()
                .mapToLong(floor -> floor.limits().maxTemplateVolume()).max().orElse(16_777_216L);
        return worldEdit.scan(player, maximumDimension, maximumVolume);
    }

    private WorldEditGateway.CaptureResult captureSelection(Player player) {
        int maximumDimension = configRegistry.snapshot().floors().values().stream()
                .mapToInt(floor -> floor.limits().maxTemplateDimension()).max().orElse(512);
        long maximumVolume = configRegistry.snapshot().floors().values().stream()
                .mapToLong(floor -> floor.limits().maxTemplateVolume()).max().orElse(16_777_216L);
        return worldEdit.capture(player, maximumDimension, maximumVolume);
    }

    private void reportAuthoringReload(CommandSender sender) {
        ConfigRegistryService.ReloadResult reload = reservations.withAdmissionPaused(configRegistry::reload);
        reload.warnings().forEach(warning -> sender.sendMessage("[WARN] " + warning));
        reload.errors().forEach(error -> sender.sendMessage("[FAIL] reload: " + error));
        if (reload.swapped()) sender.sendMessage("[PASS] registry hash=" + reload.snapshot().hash());
    }

    private static Point playerPoint(Player player) {
        return new Point(player.getLocation().getBlockX(), player.getLocation().getBlockY(),
                player.getLocation().getBlockZ());
    }

    private static Rotation parseRotation(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "0", "none" -> Rotation.NONE;
            case "90", "clockwise_90", "cw90" -> Rotation.CLOCKWISE_90;
            case "180", "clockwise_180" -> Rotation.CLOCKWISE_180;
            case "270", "counterclockwise_90", "ccw90" -> Rotation.COUNTERCLOCKWISE_90;
            default -> throw new IllegalArgumentException("rotation must be 0, 90, 180, or 270");
        };
    }

    private static EmeraldPolicy configuredEmeraldPolicy(BoostedCustomConfig config) {
        String value = config.getString("authoring.emerald-marker-policy", "replace").trim();
        try {
            return EmeraldPolicy.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("config.yml authoring.emerald-marker-policy must be replace or retain");
        }
    }

    private static Set<EncounterCapability> parseCapabilities(String value) {
        if (value.equalsIgnoreCase("none")) return Set.of();
        java.util.EnumSet<EncounterCapability> result = java.util.EnumSet.noneOf(EncounterCapability.class);
        for (String part : value.split(",")) {
            if (part.isBlank()) throw new IllegalArgumentException("blank capability");
            result.add(EncounterCapability.valueOf(part.trim().toUpperCase(Locale.ROOT)));
        }
        return Set.copyOf(result);
    }

    private static boolean isMarker(String type) {
        return Set.of("minecraft:jigsaw", "minecraft:gray_concrete_powder", "minecraft:yellow_concrete_powder",
                "minecraft:emerald_block", "minecraft:red_concrete_powder", "minecraft:lime_concrete_powder",
                "minecraft:chest", "minecraft:trapped_chest", "minecraft:nether_portal").contains(type);
    }
}
