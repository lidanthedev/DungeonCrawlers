package me.lidan.dungeonCrawlers.integration.worldedit;

import com.fastasyncworldedit.core.FaweAPI;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import me.lidan.dungeonCrawlers.integration.WorldEditGateway;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Block;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Selection;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Rotation;
import org.enginehub.linbus.tree.LinStringTag;
import org.enginehub.linbus.tree.LinTagType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public final class WorldEditAdapter implements WorldEditGateway {
    private final Logger logger;

    public WorldEditAdapter() {
        this(Logger.getLogger(WorldEditAdapter.class.getName()));
    }

    public WorldEditAdapter(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public SelectionResult selection(org.bukkit.entity.Player player) {
        Player actor = BukkitAdapter.adapt(player);
        try {
            Region region = WorldEdit.getInstance().getSessionManager().get(actor).getSelection(actor.getWorld());
            return new SelectionResult(true, "selection=" + region.getMinimumPoint() + ".." + region.getMaximumPoint()
                    + ", volume=" + region.getVolume());
        } catch (IncompleteRegionException exception) {
            return new SelectionResult(false, "WorldEdit selection is incomplete");
        }
    }

    @Override
    public ScanResult scan(org.bukkit.entity.Player player, int maximumDimension, long maximumVolume) {
        if (maximumDimension < 1 || maximumVolume < 1) {
            throw new IllegalArgumentException("selection limits must be positive");
        }
        Player actor = BukkitAdapter.adapt(player);
        try {
            Region region = WorldEdit.getInstance().getSessionManager().get(actor).getSelection(actor.getWorld());
            return scan(region, actor.getWorld(), maximumDimension, maximumVolume);
        } catch (IncompleteRegionException exception) {
            return new ScanResult(false, "WorldEdit selection is incomplete", Optional.empty());
        }
    }

    @Override
    public CaptureResult capture(org.bukkit.entity.Player player, int maximumDimension, long maximumVolume) {
        Player actor = BukkitAdapter.adapt(player);
        try {
            Region region = WorldEdit.getInstance().getSessionManager().get(actor).getSelection(actor.getWorld());
            logger.info("[AuthoringCapture] begin size=" + region.getWidth() + "x" + region.getHeight()
                    + "x" + region.getLength() + " volume=" + region.getVolume()
                    + " maxDimension=" + maximumDimension + " maxVolume=" + maximumVolume);
            String limitError = limitError(region, maximumDimension, maximumVolume);
            if (limitError != null) {
                logger.info("[AuthoringCapture] rejected: " + limitError);
                return new CaptureResult(false, limitError, Optional.empty(), new byte[0]);
            }

            // Copy the world once while recording the validation data from each copied block.
            // Scanning the world first and copying it afterwards caused two complete traversals.
            long sessionStarted = System.nanoTime();
            logger.fine(() -> "[AuthoringCapture] creating FAWE edit session");
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(actor.getWorld())) {
                logger.fine(() -> "[AuthoringCapture] FAWE edit session ready ms="
                        + elapsedMillis(sessionStarted, System.nanoTime()));
                BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
                clipboard.setOrigin(region.getMinimumPoint());
                // The schematic retains every copied block; the validation model only needs
                // marker blocks and the small connector planes around jigsaws.
                Map<Point, Block> capturedBlocks = new LinkedHashMap<>();
                CapturingClipboardExtent destination = new CapturingClipboardExtent(clipboard,
                        region.getMinimumPoint(), capturedBlocks);
                ForwardExtentCopy copy = new ForwardExtentCopy(editSession, region, destination,
                        region.getMinimumPoint());
                copy.setCopyingEntities(false);
                copy.setCopyingBiomes(false);
                long copyStarted = System.nanoTime();
                logger.fine(() -> "[AuthoringCapture] copy operation started");
                Operations.completeLegacy(copy);
                long copiedAt = System.nanoTime();
                addConnectorPlaneBlocks(region, clipboard, capturedBlocks);
                logger.fine(() -> "[AuthoringCapture] copy operation finished ms="
                        + elapsedMillis(copyStarted, copiedAt) + " modelBlocks=" + capturedBlocks.size());
                ScanResult scanned = selection(region, capturedBlocks);
                long selectedAt = System.nanoTime();
                logger.fine(() -> "[AuthoringCapture] selection model ready ms="
                        + elapsedMillis(copiedAt, selectedAt));
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                logger.fine(() -> "[AuthoringCapture] schematic serialization started");
                try (var writer = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(output)) {
                    writer.write(clipboard);
                }
                long serializedAt = System.nanoTime();
                logger.info(() -> "[AuthoringCapture] size=" + region.getWidth() + "x" + region.getHeight()
                        + "x" + region.getLength() + " volume=" + region.getVolume()
                        + " blocks=" + capturedBlocks.size() + " copyAndMetadataMs=" + elapsedMillis(copyStarted, copiedAt)
                        + " selectionMs=" + elapsedMillis(copiedAt, selectedAt)
                        + " serializeMs=" + elapsedMillis(selectedAt, serializedAt)
                        + " schematicBytes=" + output.size());
                return new CaptureResult(true, scanned.detail(), scanned.selection(), output.toByteArray());
            }
        } catch (IncompleteRegionException exception) {
            return new CaptureResult(false, "WorldEdit selection is incomplete", Optional.empty(), new byte[0]);
        } catch (Exception exception) {
            return new CaptureResult(false, "WorldEdit capture failed: " + exception.getMessage(),
                    Optional.empty(), new byte[0]);
        }
    }

    @Override
    public CompletableFuture<CaptureResult> captureAsync(org.bukkit.entity.Player player,
                                                         int maximumDimension, long maximumVolume) {
        Objects.requireNonNull(player, "player");
        CompletableFuture<CaptureResult> result = new CompletableFuture<>();
        try {
            FaweAPI.getTaskManager().async(() -> {
                try {
                    result.complete(capture(player, maximumDimension, maximumVolume));
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (Throwable failure) {
            result.completeExceptionally(failure);
        }
        return result;
    }

    @Override
    public ScanResult read(byte[] schematic, int maximumDimension, long maximumVolume) {
        Objects.requireNonNull(schematic, "schematic");
        try (var reader = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC
                .getReader(new ByteArrayInputStream(schematic))) {
            Clipboard clipboard = reader.read();
            return scan(clipboard.getRegion(), clipboard, maximumDimension, maximumVolume);
        } catch (IOException | RuntimeException exception) {
            return new ScanResult(false, "schematic read failed: " + exception.getMessage(), Optional.empty());
        }
    }

    @Override
    public OperationResult paste(org.bukkit.entity.Player player, byte[] schematic, Point origin, Rotation rotation) {
        Objects.requireNonNull(schematic, "schematic");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(rotation, "rotation");
        Player actor = BukkitAdapter.adapt(player);
        try (var reader = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC
                .getReader(new ByteArrayInputStream(schematic));
             var editSession = WorldEdit.getInstance().newEditSession(actor.getWorld())) {
            Clipboard clipboard = reader.read();
            ClipboardHolder holder = new ClipboardHolder(clipboard);
            holder.setTransform(new AffineTransform().rotateY(rotationDegrees(rotation)));
            Operations.complete(holder.createPaste(editSession)
                    .to(BlockVector3.at(origin.x(), origin.y(), origin.z())).ignoreAirBlocks(false).build());
            return new OperationResult(true, "pasted " + schematic.length + " bytes at " + origin
                    + " rotation=" + rotation);
        } catch (Exception exception) {
            return new OperationResult(false, "WorldEdit paste failed: " + exception.getMessage());
        }
    }

    static ScanResult scan(Region region, Extent extent, int maximumDimension, long maximumVolume) {
        if (maximumDimension < 1 || maximumVolume < 1) {
            throw new IllegalArgumentException("selection limits must be positive");
        }
        String limitError = limitError(region, maximumDimension, maximumVolume);
        if (limitError != null) return new ScanResult(false, limitError, Optional.empty());
        int width = region.getWidth();
        int height = region.getHeight();
        int length = region.getLength();
        var minimum = region.getMinimumPoint();
        Bounds bounds = new Bounds(new Point(0, 0, 0), new Point(width - 1, height - 1, length - 1));
        Map<Point, Block> blocks = new LinkedHashMap<>();
        for (var worldPoint : region) {
            BaseBlock fullBlock = extent.getFullBlock(worldPoint);
            Point relative = new Point(worldPoint.x() - minimum.x(), worldPoint.y() - minimum.y(),
                    worldPoint.z() - minimum.z());
            if (isAuthoringMarker(fullBlock.getBlockType().id())) {
                blocks.put(relative, toCaptureModelBlock(fullBlock));
            }
        }
        addConnectorPlaneBlocks(region, extent, blocks);
        Selection selection = new Selection(bounds, blocks);
        return new ScanResult(true, "origin=" + minimum + ", size=" + width + "x" + height + "x" + length
                + ", volume=" + region.getVolume(), Optional.of(selection));
    }

    private static ScanResult selection(Region region, Map<Point, Block> blocks) {
        int width = region.getWidth();
        int height = region.getHeight();
        int length = region.getLength();
        var minimum = region.getMinimumPoint();
        Bounds bounds = new Bounds(new Point(0, 0, 0), new Point(width - 1, height - 1, length - 1));
        Selection selection = new Selection(bounds, blocks);
        return new ScanResult(true, "origin=" + minimum + ", size=" + width + "x" + height + "x" + length
                + ", volume=" + region.getVolume(), Optional.of(selection));
    }

    private static Block toModelBlock(BaseBlock fullBlock) {
        String type = fullBlock.getBlockType().id();
        Map<String, String> states = new LinkedHashMap<>();
        fullBlock.getStates().forEach((property, value) -> states.put(property.getName(), String.valueOf(value)));
        Map<String, String> nbt = new LinkedHashMap<>();
        var reference = fullBlock.getNbtReference();
        if (reference != null) {
            var compound = reference.getValue();
            for (String key : new String[]{"name", "target", "pool", "final_state"}) {
                LinStringTag value = compound.findTag(key, LinTagType.stringTag());
                if (value != null) nbt.put(key, value.value());
            }
        }
        return new Block(type, states, nbt);
    }

    private static Block toCaptureModelBlock(BaseBlock fullBlock) {
        String type = fullBlock.getBlockType().id();
        return type.equals("minecraft:jigsaw") ? toModelBlock(fullBlock) : new Block(type, Map.of(), Map.of());
    }

    private static void addConnectorPlaneBlocks(Region region, Extent extent, Map<Point, Block> blocks) {
        var minimum = region.getMinimumPoint();
        for (var entry : blocks.entrySet().stream().filter(entry -> entry.getValue().is("jigsaw")).toList()) {
            Point center = entry.getKey();
            Point outward = switch (entry.getValue().states().getOrDefault("orientation", "")) {
                case "north_up" -> new Point(0, 0, -1);
                case "east_up" -> new Point(1, 0, 0);
                case "south_up" -> new Point(0, 0, 1);
                case "west_up" -> new Point(-1, 0, 0);
                default -> null;
            };
            if (outward == null) continue;
            Point right = new Point(-outward.z(), 0, outward.x());
            for (int horizontal = -1; horizontal <= 1; horizontal++) {
                for (int vertical = -1; vertical <= 1; vertical++) {
                    Point offset = right.multiply(horizontal).add(new Point(0, vertical, 0));
                    if (offset.equals(new Point(0, 0, 0))) continue;
                    Point world = center.add(offset);
                    BlockVector3 worldPoint = BlockVector3.at(minimum.x() + world.x(),
                            minimum.y() + world.y(), minimum.z() + world.z());
                    if (!region.contains(worldPoint)) continue;
                    BaseBlock block = extent.getFullBlock(worldPoint);
                    if (!block.getBlockType().id().equals("minecraft:air")) {
                        blocks.put(world, toCaptureModelBlock(block));
                    }
                }
            }
        }
    }

    private static boolean isAuthoringMarker(String type) {
        return switch (type) {
            case "minecraft:jigsaw", "minecraft:gray_concrete_powder", "minecraft:yellow_concrete_powder",
                    "minecraft:emerald_block", "minecraft:red_concrete_powder", "minecraft:lime_concrete_powder",
                    "minecraft:chest", "minecraft:trapped_chest", "minecraft:nether_portal" -> true;
            default -> false;
        };
    }

    private static final class CapturingClipboardExtent extends AbstractDelegateExtent {
        private final BlockVector3 minimum;
        private final Map<Point, Block> blocks;

        private CapturingClipboardExtent(Extent delegate, BlockVector3 minimum, Map<Point, Block> blocks) {
            super(delegate);
            this.minimum = minimum;
            this.blocks = blocks;
        }

        @Override
        public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 position, T block)
                throws WorldEditException {
            boolean changed = super.setBlock(position, block);
            if (!changed) return false;
            Point relative = new Point(position.x() - minimum.x(), position.y() - minimum.y(),
                    position.z() - minimum.z());
            BaseBlock fullBlock = block instanceof BaseBlock baseBlock ? baseBlock : block.toBaseBlock();
            String type = fullBlock.getBlockType().id();
            if (type.equals("minecraft:air")) blocks.remove(relative);
            else if (isAuthoringMarker(type)) blocks.put(relative, toCaptureModelBlock(fullBlock));
            return true;
        }
    }

    private static String limitError(Region region, int maximumDimension, long maximumVolume) {
        int width = region.getWidth();
        int height = region.getHeight();
        int length = region.getLength();
        if (width > maximumDimension || height > maximumDimension || length > maximumDimension) {
            return "selection dimension " + width + "x" + height + "x" + length
                    + " exceeds configured maximum " + maximumDimension;
        }
        if (region.getVolume() > maximumVolume) {
            return "selection volume " + region.getVolume()
                    + " exceeds configured maximum " + maximumVolume;
        }
        return null;
    }

    private static long elapsedMillis(long started, long finished) {
        return (finished - started) / 1_000_000L;
    }

    private static double rotationDegrees(Rotation rotation) {
        return switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> -90;
            case CLOCKWISE_180 -> 180;
            case COUNTERCLOCKWISE_90 -> 90;
        };
    }
}
