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
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Template;
import me.lidan.dungeonCrawlers.core.template.TemplateValidator;
import me.lidan.dungeonCrawlers.integration.WorldEditGateway;
import me.lidan.dungeonCrawlers.integration.DungeonMessages;
import me.lidan.dungeonCrawlers.integration.ProgressBarService;
import me.lidan.dungeonCrawlers.integration.worldedit.WorldEditAdapter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.function.BiFunction;
import java.util.logging.Logger;

@Command("dungeon")
public final class DungeonAuthoringCommand {
    private final ConfigRegistryService configRegistry;
    private final PlayerReservationService reservations;
    private final TemplateAuthoringService authoring;
    private final WorldEditGateway worldEdit;
    private final TemplateValidator templateValidator = new TemplateValidator();
    private final TemplateCatalogLoader templateCatalog;
    private final LayoutPlanner layoutPlanner = new LayoutPlanner();
    private final Map<UUID, List<String>> generationTraces = new ConcurrentHashMap<>();
    private final EmeraldPolicy emeraldPolicy;
    private final Supplier<Set<String>> activeTemplates;
    private final Plugin plugin;
    private final ProgressBarService progressBars;

    public DungeonAuthoringCommand(BoostedCustomConfig mainConfig, ConfigRegistryService configRegistry,
                                   PlayerReservationService reservations, TemplateAuthoringService authoring) {
        this(null, mainConfig, configRegistry, reservations, authoring, Set::of);
    }

    public DungeonAuthoringCommand(BoostedCustomConfig mainConfig, ConfigRegistryService configRegistry,
                                   PlayerReservationService reservations, TemplateAuthoringService authoring,
                                   Supplier<Set<String>> activeTemplates) {
        this(null, mainConfig, configRegistry, reservations, authoring, activeTemplates);
    }

    public DungeonAuthoringCommand(Plugin plugin, BoostedCustomConfig mainConfig,
                                   ConfigRegistryService configRegistry,
                                   PlayerReservationService reservations, TemplateAuthoringService authoring,
                                   Supplier<Set<String>> activeTemplates) {
        this(plugin, mainConfig, configRegistry, reservations, authoring, activeTemplates, null);
    }

    public DungeonAuthoringCommand(Plugin plugin, BoostedCustomConfig mainConfig,
                                   ConfigRegistryService configRegistry,
                                   PlayerReservationService reservations, TemplateAuthoringService authoring,
                                   Supplier<Set<String>> activeTemplates, ProgressBarService progressBars) {
        this.plugin = plugin;
        this.progressBars = progressBars;
        this.worldEdit = new WorldEditAdapter(plugin == null
                ? Logger.getLogger(WorldEditAdapter.class.getName()) : plugin.getLogger());
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
            DungeonMessages.send(player, DungeonMessages.error("Room type must be normal, start, portal, or boss; "
                    + "encounters must be none, normal, miniboss, or normal,miniboss."));
            return;
        }
        WorldEditGateway.ScanResult scan = scanSelection(player);
        if (!scan.successful()) {
            DungeonMessages.send(player, DungeonMessages.error(cleanDetail(scan.detail())));
            return;
        }
        var result = templateValidator.validate("selection", type, capabilities,
                scan.selection().orElseThrow(), emeraldPolicy);
        result.errors().forEach(error -> DungeonMessages.send(player, DungeonMessages.error(cleanDetail(error))));
        if (result.successful()) {
            var template = result.template().orElseThrow();
            DungeonMessages.send(player, DungeonMessages.success(selectionSummary(scan.detail())
                    + ". Content hash: <white>" + template.contentHash() + "</white>; secrets: <white>"
                    + template.secrets().size() + "</white>; portal blocks: <white>"
                    + template.portalBlocks().size() + "</white>."));
        }
    }

    @Subcommand("selection markers")
    @CommandPermission("dungeoncrawlers.admin.authoring")
    public void selectionMarkers(Player player) {
        sendMarkerLegend(player);
        WorldEditGateway.ScanResult scan = scanSelection(player);
        if (!scan.successful()) {
            DungeonMessages.send(player, DungeonMessages.error(cleanDetail(scan.detail())));
            return;
        }
        var markers = scan.selection().orElseThrow().blocks().entrySet().stream()
                .filter(entry -> isMarker(entry.getValue().type()))
                .sorted(Map.Entry.comparingByKey()).toList();
        if (markers.isEmpty()) {
            DungeonMessages.send(player, DungeonMessages.error("The selection contains no DungeonCrawlers markers."));
            return;
        }
        DungeonMessages.send(player, DungeonMessages.success("Found <white>" + markers.size()
                + "</white> markers in " + selectionSummary(scan.detail()) + "."));
        markers.forEach(entry -> DungeonMessages.send(player, "<gray>Marker at <white>" + point(entry.getKey())
                + "</white>: <white>" + blockLabel(entry.getValue().type()) + "</white>"
                + (entry.getValue().is("jigsaw")
                ? "; states: <white>" + formatMap(entry.getValue().states()) + "</white>" : "") + "</gray>"));
    }

    static List<String> markerLegend() {
        return List.of(
                "DungeonCrawlers marker blocks:",
                "- Entrance: JIGSAW named dungeoncrawlers:entrance",
                "- Exit/door: JIGSAW named dungeoncrawlers:exit",
                "- Normal mob: GRAY_CONCRETE_POWDER",
                "- Miniboss mob: YELLOW_CONCRETE_POWDER",
                "- Player spawn/teleport: EMERALD_BLOCK",
                "- Boss spawn: RED_CONCRETE_POWDER",
                "- Reward chest: LIME_CONCRETE_POWDER",
                "- Secret/blessing: CHEST; standard secret: TRAPPED_CHEST",
                "- Portal trigger: connected NETHER_PORTAL blocks",
                "- Jigsaw settings: target dungeoncrawlers:connector; pool minecraft:empty; "
                        + "final state minecraft:air; orientation north_up/east_up/south_up/west_up"
        );
    }

    private static void sendMarkerLegend(Player player) {
        markerLegend().forEach(line -> DungeonMessages.send(player, "<gray>" + line + "</gray>"));
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
            DungeonMessages.send(player, DungeonMessages.error("Invalid room type or encounter list."));
            return;
        }
        runRoomWorkflow(player, "create", "Creating room", id, type, capabilities, true,
                (schematic, template) -> authoring.create(id, type, capabilities, schematic, template));
    }

    @Subcommand("room update")
    @CommandPermission("dungeoncrawlers.admin.authoring")
    public void roomUpdate(Player player, @SuggestWith(RoomIdSuggestionProvider.class) String id) {
        var definition = configRegistry.snapshot().rooms().get(id);
        if (definition == null) {
            DungeonMessages.send(player, DungeonMessages.error("Unknown room: <white>" + id + "</white>"));
            return;
        }
        runRoomWorkflow(player, "update", "Updating room", id, definition.type(), definition.capabilities(), false,
                (schematic, template) -> authoring.update(id, schematic, template));
    }

    @Subcommand("room delete")
    @CommandPermission("dungeoncrawlers.admin.authoring")
    public void roomDelete(CommandSender sender, @SuggestWith(RoomIdSuggestionProvider.class) String id) {
        TemplateAuthoringService.OperationResult result = authoring.delete(id, activeTemplates.get());
        DungeonMessages.send(sender, result.successful()
                ? DungeonMessages.success(operationSuccess("delete", id, result.detail()))
                : DungeonMessages.error(operationFailure("delete", id, result.detail())));
        if (result.successful()) reportAuthoringReload(sender);
    }

    @Subcommand("room paste")
    @CommandPermission("dungeoncrawlers.admin.authoring")
    public void roomPaste(Player player, @SuggestWith(RoomIdSuggestionProvider.class) String id, String rotationValue) {
        try {
            Rotation rotation = parseRotation(rotationValue);
            byte[] schematic = authoring.schematic(id);
            Point origin = playerPoint(player);
            WorldEditGateway.OperationResult result = worldEdit.paste(player, schematic, origin, rotation);
            DungeonMessages.send(player, result.successful()
                    ? DungeonMessages.success("Room schematic pasted at <white>" + point(origin)
                            + "</white> with rotation <white>" + rotationLabel(rotation) + "</white>.")
                    : DungeonMessages.error("Could not paste the room schematic: "
                            + cleanDetail(result.detail())));
        } catch (Exception exception) {
            DungeonMessages.send(player, DungeonMessages.error(
                    "Could not paste the room schematic. Check the server log for details."));
        }
    }

    @Subcommand("connect-test")
    @CommandPermission("dungeoncrawlers.admin.authoring")
    public void connectTest(Player player,
                            @SuggestWith(RoomIdSuggestionProvider.class) String fromId,
                            @SuggestWith(RoomIdSuggestionProvider.class) String toId,
                            String fromRotation) {
        TemplateCatalogLoader.LoadResult loaded = templateCatalog.load(configRegistry.snapshot());
        if (!loaded.successful()) {
            loaded.errors().forEach(error -> DungeonMessages.send(player, DungeonMessages.error(cleanDetail(error))));
            return;
        }
        var catalog = loaded.catalog().orElseThrow();
        var from = catalog.get(fromId);
        var to = catalog.get(toId);
        if (from == null || to == null) {
            DungeonMessages.send(player, DungeonMessages.error("Unknown authored room. Source: <white>" + fromId
                    + "</white> · Target: <white>" + toId + "</white>."));
            return;
        }
        try {
            var result = layoutPlanner.connectTest(from.template(), parseRotation(fromRotation), playerPoint(player),
                    to.template());
            if (!result.successful()) {
                DungeonMessages.send(player, DungeonMessages.error(cleanDetail(result.detail())));
                return;
            }
            var placement = result.placement().orElseThrow();
            var connection = result.connection().orElseThrow();
            DungeonMessages.send(player, DungeonMessages.success("Connection test passed. Origin: <white>"
                    + point(placement.origin()) + "</white>; rotation: <white>" + rotationLabel(placement.rotation())
                    + "</white>; door blocks: <white>" + connection.doorBounds().size()
                    + "</white>; entrance blocks: <white>" + connection.entranceBounds().size()
                    + "</white>; connection blocks: <white>" + connection.bounds().size() + "</white>."));
        } catch (IllegalArgumentException exception) {
            DungeonMessages.send(player, DungeonMessages.error(exception.getMessage()));
        }
    }

    @Subcommand("generation plan")
    @CommandPermission("dungeoncrawlers.admin.authoring")
    public void generationPlan(Player player, @SuggestWith(FloorIdSuggestionProvider.class) String floorId, long seed) {
        var snapshot = configRegistry.snapshot();
        var floor = snapshot.floors().get(floorId);
        if (floor == null) {
            DungeonMessages.send(player, DungeonMessages.error("Unknown floor: <white>" + floorId + "</white>"));
            return;
        }
        TemplateCatalogLoader.LoadResult loaded = templateCatalog.load(snapshot);
        if (!loaded.successful()) {
            loaded.errors().forEach(error -> DungeonMessages.send(player, DungeonMessages.error(cleanDetail(error))));
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
            result.errors().forEach(error -> DungeonMessages.send(player, DungeonMessages.error(cleanDetail(error))));
            return;
        }
        var plan = result.plan().orElseThrow();
        DungeonMessages.send(player, DungeonMessages.success("Generation plan ready. Algorithm version: <white>"
                + plan.algorithmVersion() + "</white>; seed: <white>" + seed + "</white>; placements: <white>"
                + plan.placements().size() + "</white>; connections: <white>" + plan.connections().size() + "</white>."));
        plan.placements().forEach(placement -> DungeonMessages.send(player, "<gray>Placement <white>#"
                + placement.index() + "</white>: <white>" + placement.type().name().toLowerCase(Locale.ROOT)
                + "</white> room <white>" + placement.templateId() + "</white> at <white>"
                + point(placement.origin()) + "</white>, rotation <white>" + rotationLabel(placement.rotation())
                + "</white>, encounter <white>" + placement.encounter() + "</white>.</gray>"));
    }

    @Subcommand("generation trace")
    @CommandPermission("dungeoncrawlers.admin.authoring")
    public void generationTrace(Player player) {
        List<String> trace = generationTraces.get(player.getUniqueId());
        if (trace == null) {
            DungeonMessages.send(player, DungeonMessages.error("No generation plan trace exists in this plugin session."));
            return;
        }
        DungeonMessages.send(player, DungeonMessages.info("Generation trace: <white>" + trace.size()
                + "</white> entries."));
        trace.forEach(line -> DungeonMessages.send(player, "<gray>" + line + "</gray>"));
    }

    private void runRoomWorkflow(Player player, String action, String progressTitle, String id, RoomType type,
                                 Set<EncounterCapability> capabilities, boolean reloadAfterSave,
                                 BiFunction<byte[], Template, TemplateAuthoringService.OperationResult> persistence) {
        long startedAt = System.nanoTime();
        logAuthoring("room " + action + " started id=" + id + " player=" + player.getName()
                + " type=" + type + " " + (action.equals("create") ? "encounters=" : "capabilities=")
                + capabilities);
        UUID progressId = beginRoomProgress(player, progressTitle);
        captureSelectionAsync(player, progressId).thenAccept(capture -> {
            if (!capture.successful()) {
                logAuthoring("room " + action + " capture failed id=" + id + " elapsedMs="
                        + elapsedMillis(startedAt) + " detail=" + capture.detail());
                failRoomProgress(progressId, "Could not capture the room selection");
                runOnMain(player, () -> {
                    if (player.isOnline()) DungeonMessages.send(player, DungeonMessages.error(
                            "Could not capture the room selection: " + cleanDetail(capture.detail())));
                });
                return;
            }
            updateRoomProgress(progressId, 0.78, "Checking room markers");
            long validationStarted = System.nanoTime();
            var validation = templateValidator.validate(id, type, capabilities,
                    capture.selection().orElseThrow(), emeraldPolicy);
            logAuthoring("room " + action + " validation finished id=" + id + " elapsedMs="
                    + elapsedMillis(validationStarted) + " blocks="
                    + capture.selection().orElseThrow().blocks().size() + " successful=" + validation.successful()
                    + " errors=" + validation.errors().size());
            runOnMain(player, () -> {
                if (!validation.successful()) {
                    logAuthoring("room " + action + " rejected id=" + id + " totalMs=" + elapsedMillis(startedAt));
                    failRoomProgress(progressId, "room validation failed");
                    if (player.isOnline()) validation.errors().forEach(error -> DungeonMessages.send(player,
                            DungeonMessages.error(cleanDetail(error))));
                    return;
                }
                updateRoomProgress(progressId, 0.92, "Saving room");
                long saveStarted = System.nanoTime();
                TemplateAuthoringService.OperationResult result = persistence.apply(capture.schematic(),
                        validation.template().orElseThrow());
                logAuthoring("room " + action + " save finished id=" + id + " elapsedMs="
                        + elapsedMillis(saveStarted) + " totalMs=" + elapsedMillis(startedAt)
                        + " successful=" + result.successful());
                if (player.isOnline()) {
                    DungeonMessages.send(player, result.successful()
                            ? DungeonMessages.success(operationSuccess(action, id, result.detail()))
                            : DungeonMessages.error(operationFailure(action, id, result.detail())));
                    if (result.successful() && reloadAfterSave) reportAuthoringReload(player);
                }
                if (result.successful()) completeRoomProgress(progressId,
                        "Room " + (action.equals("create") ? "created" : "updated"));
                else failRoomProgress(progressId, "Could not " + action + " the room");
            });
        }).exceptionally(error -> {
            logAuthoring("room " + action + " failed id=" + id + " totalMs=" + elapsedMillis(startedAt)
                    + " error=" + message(error));
            failRoomProgress(progressId, message(error));
            runOnMain(player, () -> {
                if (player.isOnline()) DungeonMessages.send(player, DungeonMessages.error(
                        "Room capture failed. Check the server log for details."));
            });
            return null;
        });
    }

    private WorldEditGateway.ScanResult scanSelection(Player player) {
        int maximumDimension = configRegistry.snapshot().floors().values().stream()
                .mapToInt(floor -> floor.limits().maxTemplateDimension()).max().orElse(512);
        long maximumVolume = configRegistry.snapshot().floors().values().stream()
                .mapToLong(floor -> floor.limits().maxTemplateVolume()).max().orElse(16_777_216L);
        return worldEdit.scan(player, maximumDimension, maximumVolume);
    }

    private CompletableFuture<WorldEditGateway.CaptureResult> captureSelectionAsync(Player player, UUID progressId) {
        int maximumDimension = configRegistry.snapshot().floors().values().stream()
                .mapToInt(floor -> floor.limits().maxTemplateDimension()).max().orElse(512);
        long maximumVolume = configRegistry.snapshot().floors().values().stream()
                .mapToLong(floor -> floor.limits().maxTemplateVolume()).max().orElse(16_777_216L);
        updateRoomProgress(progressId, 0.10, "Capturing your selection");
        if (plugin != null && progressBars == null) {
            DungeonMessages.send(player, DungeonMessages.info(
                    "Capturing your selection in the background; you can keep playing."));
        }
        long startedAt = System.nanoTime();
        logAuthoring("capture started player=" + player.getName() + " maxDimension=" + maximumDimension
                + " maxVolume=" + maximumVolume);
        return worldEdit.captureAsync(player, maximumDimension, maximumVolume).whenComplete((capture, error) -> {
            if (error != null) {
                logAuthoring("capture failed player=" + player.getName() + " elapsedMs=" + elapsedMillis(startedAt)
                        + " error=" + message(error));
            } else {
                logAuthoring("capture finished player=" + player.getName() + " elapsedMs=" + elapsedMillis(startedAt)
                        + " successful=" + capture.successful() + " detail=" + capture.detail()
                        + " blocks=" + capture.selection().map(selection -> selection.blocks().size()).orElse(0));
            }
        });
    }

    private void logAuthoring(String message) {
        if (plugin != null) plugin.getLogger().fine("[Authoring] " + message);
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private UUID beginRoomProgress(Player player, String title) {
        if (progressBars == null) return null;
        UUID taskId = UUID.randomUUID();
        progressBars.begin(taskId, List.of(player), title, "Starting", 0.02);
        return taskId;
    }

    private void updateRoomProgress(UUID taskId, double progress, String detail) {
        if (taskId != null) progressBars.update(taskId, progress, detail);
    }

    private void completeRoomProgress(UUID taskId, String detail) {
        if (taskId != null) progressBars.complete(taskId, detail);
    }

    private void failRoomProgress(UUID taskId, String detail) {
        if (taskId != null) progressBars.fail(taskId, detail);
    }

    private void runOnMain(Player player, Runnable callback) {
        if (plugin == null) {
            callback.run();
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, callback);
    }

    private static String message(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private void reportAuthoringReload(CommandSender sender) {
        ConfigRegistryService.ReloadResult reload = reservations.withAdmissionPaused(configRegistry::reload);
        reload.warnings().forEach(warning -> DungeonMessages.send(sender, DungeonMessages.warning(warning)));
        reload.errors().forEach(error -> DungeonMessages.send(sender, DungeonMessages.error("Reload failed: " + error)));
        if (reload.swapped()) DungeonMessages.send(sender, DungeonMessages.success("Registry reloaded. Hash: <white>"
                + reload.snapshot().hash() + "</white>"));
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

    private static String point(Point point) {
        return point.x() + ", " + point.y() + ", " + point.z();
    }

    private static String selectionSummary(String detail) {
        return detail.replace("origin=", "Origin: ")
                .replace(", size=", " · Size: ")
                .replace(", volume=", " · Volume: ");
    }

    private static String operationSuccess(String action, String id, String detail) {
        String message = "Room <white>" + id + "</white> "
                + (action.equals("create") ? "created" : action.equals("update") ? "updated" : "deleted")
                + " successfully.";
        if (detail.contains("generation metadata preserved")) {
            message += " Generation metadata preserved.";
        }
        int backup = detail.indexOf("backup=");
        if (backup >= 0) message += " Recoverable backup: <white>" + detail.substring(backup + 7) + "</white>.";
        return message;
    }

    private static String operationFailure(String action, String id, String detail) {
        return "Could not " + action + " room <white>" + id + "</white>: " + cleanDetail(detail);
    }

    private static String cleanDetail(String detail) {
        return detail.replace("create failed: ", "")
                .replace("update failed: ", "")
                .replace("delete failed: ", "")
                .replace("backup=", "Backup: ")
                .replace("=", ": ")
                .replace("; ", " · ");
    }

    private static String blockLabel(String type) {
        String value = type.startsWith("minecraft:") ? type.substring("minecraft:".length()) : type;
        return value.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    private static String rotationLabel(Rotation rotation) {
        return switch (rotation) {
            case NONE -> "none";
            case CLOCKWISE_90 -> "90° clockwise";
            case CLOCKWISE_180 -> "180°";
            case COUNTERCLOCKWISE_90 -> "90° counterclockwise";
        };
    }

    private static String formatMap(Map<String, String> values) {
        if (values.isEmpty()) return "none";
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(java.util.stream.Collectors.joining(", "));
    }
}
