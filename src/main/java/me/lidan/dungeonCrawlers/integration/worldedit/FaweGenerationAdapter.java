package me.lidan.dungeonCrawlers.integration.worldedit;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;
import me.lidan.dungeonCrawlers.core.layout.LayoutPlanner.Connection;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Rotation;
import me.lidan.dungeonCrawlers.integration.GenerationWorldGateway;
import me.lidan.dungeonCrawlers.persistence.model.GenerationJournal.PlannedBounds;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class FaweGenerationAdapter implements GenerationWorldGateway {
    private final Server server;
    private final Executor executor;
    private final NamespacedKey worldMarker;

    public FaweGenerationAdapter(Plugin plugin, Executor executor) {
        Objects.requireNonNull(plugin, "plugin");
        this.server = plugin.getServer();
        this.executor = Objects.requireNonNull(executor, "executor");
        this.worldMarker = new NamespacedKey(plugin, "instance_world");
    }

    @Override
    public WorldCheck ensureDedicatedVoidWorld(String worldName) {
        if (!server.isPrimaryThread()) {
            return new WorldCheck(false, "void-world validation must run on the Paper thread", 0, 0);
        }
        if (worldName == null || !worldName.matches("^[A-Za-z0-9_-]{1,64}$")) {
            return new WorldCheck(false, "generation.world must be a safe world name", 0, 0);
        }
        World world = server.getWorld(worldName);
        boolean created = false;
        if (world == null) {
            world = new WorldCreator(worldName).environment(World.Environment.NORMAL)
                    .generator(new VoidChunkGenerator()).createWorld();
            created = true;
        }
        if (world == null) return new WorldCheck(false, "could not create dedicated void world " + worldName, 0, 0);
        if (created) {
            world.getPersistentDataContainer().set(worldMarker, PersistentDataType.BYTE, (byte) 1);
            world.setAutoSave(true);
        }
        Byte marker = world.getPersistentDataContainer().get(worldMarker, PersistentDataType.BYTE);
        if (marker == null || marker != 1) {
            return new WorldCheck(false, "world " + worldName + " is not owned as a DungeonCrawlers void world",
                    world.getMinHeight(), world.getMaxHeight() - 1);
        }
        return new WorldCheck(true, "dedicated void world " + worldName + " is ready",
                world.getMinHeight(), world.getMaxHeight() - 1);
    }

    @Override
    public CompletableFuture<Void> paste(String worldName, byte[] schematic, Point origin, Rotation rotation) {
        Objects.requireNonNull(schematic); Objects.requireNonNull(origin); Objects.requireNonNull(rotation);
        com.sk89q.worldedit.world.World world = requireWorld(worldName);
        byte[] copy = schematic.clone();
        return CompletableFuture.runAsync(() -> {
            try (var reader = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC
                    .getReader(new ByteArrayInputStream(copy));
                 var editSession = WorldEdit.getInstance().newEditSession(world)) {
                Clipboard clipboard = reader.read();
                ClipboardHolder holder = new ClipboardHolder(clipboard);
                holder.setTransform(new AffineTransform().rotateY(rotationDegrees(rotation)));
                Operations.complete(holder.createPaste(editSession)
                        .to(BlockVector3.at(origin.x(), origin.y(), origin.z())).ignoreAirBlocks(false).build());
            } catch (Exception exception) {
                throw new IllegalStateException("FAWE paste failed", exception);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> setupConnections(String worldName, List<Connection> connections) {
        com.sk89q.worldedit.world.World world = requireWorld(worldName);
        List<Connection> copy = List.copyOf(connections);
        return CompletableFuture.runAsync(() -> {
            try (var editSession = WorldEdit.getInstance().newEditSession(world)) {
                for (Connection connection : copy) {
                    for (Point point : connection.entranceBounds()) {
                        editSession.setBlock(vector(point), BlockTypes.AIR.getDefaultState());
                    }
                    for (Point point : connection.doorBounds()) {
                        editSession.setBlock(vector(point), BlockTypes.COAL_BLOCK.getDefaultState());
                    }
                }
            } catch (Exception exception) {
                throw new IllegalStateException("FAWE connection setup failed", exception);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> clear(String worldName, List<PlannedBounds> bounds) {
        com.sk89q.worldedit.world.World world = requireWorld(worldName);
        List<PlannedBounds> copy = List.copyOf(bounds);
        return CompletableFuture.runAsync(() -> {
            try (var editSession = WorldEdit.getInstance().newEditSession(world)) {
                for (PlannedBounds bound : copy) {
                    CuboidRegion region = new CuboidRegion(world,
                            BlockVector3.at(bound.minX(), bound.minY(), bound.minZ()),
                            BlockVector3.at(bound.maxX(), bound.maxY(), bound.maxZ()));
                    editSession.setBlocks(region, BlockTypes.AIR.getDefaultState());
                }
            } catch (Exception exception) {
                throw new IllegalStateException("FAWE clear failed", exception);
            }
        }, executor);
    }

    private com.sk89q.worldedit.world.World requireWorld(String worldName) {
        World world = server.getWorld(worldName);
        if (world == null) throw new IllegalStateException("generation world is not loaded: " + worldName);
        Byte marker = world.getPersistentDataContainer().get(worldMarker, PersistentDataType.BYTE);
        if (marker == null || marker != 1) throw new IllegalStateException("generation world is not dedicated");
        return BukkitAdapter.adapt(world);
    }

    private static BlockVector3 vector(Point point) {
        return BlockVector3.at(point.x(), point.y(), point.z());
    }

    private static double rotationDegrees(Rotation rotation) {
        return switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> -90;
            case CLOCKWISE_180 -> 180;
            case COUNTERCLOCKWISE_90 -> 90;
        };
    }

    private static final class VoidChunkGenerator extends ChunkGenerator {
        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, Math.clamp(64, world.getMinHeight(), world.getMaxHeight() - 1), 0.5);
        }
    }
}
