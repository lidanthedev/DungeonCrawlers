package me.lidan.dungeonCrawlers.integration.worldedit;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.entity.Player;
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

public final class WorldEditAdapter implements WorldEditGateway {
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
            ScanResult scanned = scan(region, actor.getWorld(), maximumDimension, maximumVolume);
            if (!scanned.successful()) {
                return new CaptureResult(false, scanned.detail(), Optional.empty(), new byte[0]);
            }
            BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
            clipboard.setOrigin(region.getMinimumPoint());
            Operations.complete(new ForwardExtentCopy(actor.getWorld(), region, clipboard, region.getMinimumPoint()));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (var writer = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(output)) {
                writer.write(clipboard);
            }
            return new CaptureResult(true, scanned.detail(), scanned.selection(), output.toByteArray());
        } catch (IncompleteRegionException exception) {
            return new CaptureResult(false, "WorldEdit selection is incomplete", Optional.empty(), new byte[0]);
        } catch (Exception exception) {
            return new CaptureResult(false, "WorldEdit capture failed: " + exception.getMessage(),
                    Optional.empty(), new byte[0]);
        }
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
        int width = region.getWidth();
        int height = region.getHeight();
        int length = region.getLength();
        if (width > maximumDimension || height > maximumDimension || length > maximumDimension) {
            return new ScanResult(false, "selection dimension " + width + "x" + height + "x" + length
                    + " exceeds configured maximum " + maximumDimension, Optional.empty());
        }
        if (region.getVolume() > maximumVolume) {
            return new ScanResult(false, "selection volume " + region.getVolume()
                    + " exceeds configured maximum " + maximumVolume, Optional.empty());
        }
        var minimum = region.getMinimumPoint();
        Bounds bounds = new Bounds(new Point(0, 0, 0), new Point(width - 1, height - 1, length - 1));
        Map<Point, Block> blocks = new LinkedHashMap<>();
        for (var worldPoint : region) {
            BaseBlock fullBlock = extent.getFullBlock(worldPoint);
            String type = fullBlock.getBlockType().id();
            if (type.equals("minecraft:air")) continue;
            Point relative = new Point(worldPoint.x() - minimum.x(), worldPoint.y() - minimum.y(),
                    worldPoint.z() - minimum.z());
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
            blocks.put(relative, new Block(type, states, nbt));
        }
        Selection selection = new Selection(bounds, blocks);
        return new ScanResult(true, "origin=" + minimum + ", size=" + width + "x" + height + "x" + length
                + ", volume=" + region.getVolume(), Optional.of(selection));
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
