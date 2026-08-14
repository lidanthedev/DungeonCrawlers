package me.lidan.dungeonCrawlers.core.template;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.EncounterCapability;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class TemplateModels {
    private TemplateModels() {
    }

    public record Point(int x, int y, int z) implements Comparable<Point> {
        public Point add(Point other) {
            return new Point(Math.addExact(x, other.x), Math.addExact(y, other.y), Math.addExact(z, other.z));
        }

        public Point subtract(Point other) {
            return new Point(Math.subtractExact(x, other.x), Math.subtractExact(y, other.y),
                    Math.subtractExact(z, other.z));
        }

        public Point multiply(int factor) {
            return new Point(Math.multiplyExact(x, factor), Math.multiplyExact(y, factor),
                    Math.multiplyExact(z, factor));
        }

        @Override
        public int compareTo(Point other) {
            return Comparator.comparingInt(Point::x).thenComparingInt(Point::y).thenComparingInt(Point::z)
                    .compare(this, other);
        }
    }

    public enum Facing {
        NORTH(0, 0, -1), EAST(1, 0, 0), SOUTH(0, 0, 1), WEST(-1, 0, 0);

        private final Point vector;

        Facing(int x, int y, int z) {
            vector = new Point(x, y, z);
        }

        public Point vector() {
            return vector;
        }

        public Facing opposite() {
            return values()[(ordinal() + 2) % 4];
        }
    }

    public enum Rotation {
        NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90;

        public Point apply(Point point) {
            return switch (this) {
                case NONE -> point;
                case CLOCKWISE_90 -> new Point(Math.negateExact(point.z), point.y, point.x);
                case CLOCKWISE_180 -> new Point(Math.negateExact(point.x), point.y, Math.negateExact(point.z));
                case COUNTERCLOCKWISE_90 -> new Point(point.z, point.y, Math.negateExact(point.x));
            };
        }

        public Facing apply(Facing facing) {
            Point rotated = apply(facing.vector());
            for (Facing candidate : Facing.values()) {
                if (candidate.vector().equals(rotated)) return candidate;
            }
            throw new IllegalStateException("rotation produced a non-horizontal facing");
        }

        public static Rotation mapping(Facing source, Facing target) {
            for (Rotation rotation : values()) {
                if (rotation.apply(source) == target) return rotation;
            }
            throw new IllegalArgumentException("no horizontal rotation maps " + source + " to " + target);
        }
    }

    public record Bounds(Point minimum, Point maximum) {
        public Bounds {
            Objects.requireNonNull(minimum, "minimum");
            Objects.requireNonNull(maximum, "maximum");
            if (minimum.x > maximum.x || minimum.y > maximum.y || minimum.z > maximum.z) {
                throw new IllegalArgumentException("minimum must not exceed maximum");
            }
        }

        public boolean contains(Point point) {
            return point.x >= minimum.x && point.x <= maximum.x
                    && point.y >= minimum.y && point.y <= maximum.y
                    && point.z >= minimum.z && point.z <= maximum.z;
        }

        public Bounds translate(Point offset) {
            return new Bounds(minimum.add(offset), maximum.add(offset));
        }

        public Bounds rotate(Rotation rotation) {
            List<Point> corners = new ArrayList<>(8);
            for (int x : new int[]{minimum.x, maximum.x}) {
                for (int y : new int[]{minimum.y, maximum.y}) {
                    for (int z : new int[]{minimum.z, maximum.z}) {
                        corners.add(rotation.apply(new Point(x, y, z)));
                    }
                }
            }
            return enclosing(corners);
        }

        public boolean intersects(Bounds other) {
            return minimum.x <= other.maximum.x && maximum.x >= other.minimum.x
                    && minimum.y <= other.maximum.y && maximum.y >= other.minimum.y
                    && minimum.z <= other.maximum.z && maximum.z >= other.minimum.z;
        }

        public Bounds expand(int amount) {
            if (amount < 0) throw new IllegalArgumentException("amount must not be negative");
            Point delta = new Point(amount, amount, amount);
            return new Bounds(minimum.subtract(delta), maximum.add(delta));
        }

        public long volume() {
            return Math.multiplyExact(Math.multiplyExact((long) maximum.x - minimum.x + 1,
                    (long) maximum.y - minimum.y + 1), (long) maximum.z - minimum.z + 1);
        }

        public static Bounds enclosing(Collection<Point> points) {
            if (points.isEmpty()) throw new IllegalArgumentException("points must not be empty");
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (Point point : points) {
                minX = Math.min(minX, point.x); minY = Math.min(minY, point.y); minZ = Math.min(minZ, point.z);
                maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y); maxZ = Math.max(maxZ, point.z);
            }
            return new Bounds(new Point(minX, minY, minZ), new Point(maxX, maxY, maxZ));
        }
    }

    public record Block(String type, Map<String, String> states, Map<String, String> nbt) {
        public Block {
            type = canonicalType(type);
            states = Map.copyOf(states);
            nbt = Map.copyOf(nbt);
        }

        public boolean is(String expected) {
            return type.equals(canonicalType(expected));
        }

        private static String canonicalType(String value) {
            Objects.requireNonNull(value, "type");
            String normalized = value.toLowerCase(java.util.Locale.ROOT);
            return normalized.contains(":") ? normalized : "minecraft:" + normalized;
        }
    }

    public record Selection(Bounds bounds, Map<Point, Block> blocks) {
        public Selection {
            Objects.requireNonNull(bounds, "bounds");
            blocks = Map.copyOf(blocks);
            for (Point point : blocks.keySet()) {
                if (!bounds.contains(point)) throw new IllegalArgumentException("block outside selection: " + point);
            }
        }

        public Block block(Point point) {
            return blocks.getOrDefault(point, new Block("minecraft:air", Map.of(), Map.of()));
        }
    }

    public enum ConnectorKind { ENTRANCE, EXIT }
    public enum SecretKind { STANDARD, BLESSING }
    public enum EmeraldPolicy { REPLACE, RETAIN }

    public record Connector(ConnectorKind kind, Point point, Facing outward) {
        public Connector {
            Objects.requireNonNull(kind); Objects.requireNonNull(point); Objects.requireNonNull(outward);
        }

        public Connector transform(Rotation rotation, Point origin) {
            return new Connector(kind, rotation.apply(point).add(origin), rotation.apply(outward));
        }
    }

    public record Secret(Point point, SecretKind kind) {
        public Secret {
            Objects.requireNonNull(point); Objects.requireNonNull(kind);
        }
    }

    public record Template(String id, RoomType type, Set<EncounterCapability> capabilities, Bounds bounds,
                           Optional<Connector> entrance, Optional<Connector> exit, List<Point> normalMobs,
                           List<Point> minibossMobs, List<Point> playerSpawns, Optional<Point> bossSpawn,
                           Optional<Point> rewardChest, List<Secret> secrets, Set<Point> portalBlocks,
                           Set<Point> solidBlocks, String contentHash) {
        public Template {
            Objects.requireNonNull(id); Objects.requireNonNull(type); Objects.requireNonNull(bounds);
            capabilities = Set.copyOf(capabilities);
            Objects.requireNonNull(entrance); Objects.requireNonNull(exit);
            normalMobs = sorted(normalMobs); minibossMobs = sorted(minibossMobs); playerSpawns = sorted(playerSpawns);
            Objects.requireNonNull(bossSpawn); Objects.requireNonNull(rewardChest);
            secrets = List.copyOf(secrets.stream().sorted(Comparator.comparing(Secret::point)).toList());
            portalBlocks = Set.copyOf(new LinkedHashSet<>(portalBlocks.stream().sorted().toList()));
            solidBlocks = Set.copyOf(new LinkedHashSet<>(solidBlocks.stream().sorted().toList()));
            Objects.requireNonNull(contentHash);
        }

        private static List<Point> sorted(List<Point> points) {
            return List.copyOf(points.stream().sorted().toList());
        }
    }

    public record SecretId(java.util.UUID instanceId, int generatedRoomIndex, Point templateRelativeBlockVector) {
        public SecretId {
            Objects.requireNonNull(instanceId); Objects.requireNonNull(templateRelativeBlockVector);
            if (generatedRoomIndex < 0) throw new IllegalArgumentException("generated room index must not be negative");
        }
    }

    public static Set<Point> plane(Point center, Facing outward) {
        Point up = new Point(0, 1, 0);
        Point right = new Point(-outward.vector().z, 0, outward.vector().x);
        Set<Point> result = new LinkedHashSet<>();
        for (int horizontal = -1; horizontal <= 1; horizontal++) {
            for (int vertical = -1; vertical <= 1; vertical++) {
                result.add(center.add(right.multiply(horizontal)).add(up.multiply(vertical)));
            }
        }
        return Set.copyOf(result);
    }
}
