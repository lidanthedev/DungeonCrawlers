package me.lidan.dungeonCrawlers.authoring;

import me.lidan.cavecrawlers.utils.BoostedCustomConfig;
import me.lidan.dungeonCrawlers.config.BoostedConfigFactory;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.EncounterCapability;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomDefinition;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Bounds;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Connector;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.ConnectorKind;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Facing;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Secret;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.SecretKind;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Template;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The small, immutable part of a schematic needed by generation. It is written
 * when a room is authored so normal generation never has to scan the schematic.
 */
public record TemplateMetadata(long schematicSize, long schematicModifiedMillis, Bounds bounds,
                               Optional<Connector> entrance, Optional<Connector> exit,
                               List<Point> normalMobs, List<Point> minibossMobs, List<Point> playerSpawns,
                               Optional<Point> bossSpawn, Optional<Point> rewardChest, List<Secret> secrets,
                               Set<Point> portalBlocks, String contentHash) {
    /** Version 2 records the updated CHEST blessing / TRAPPED_CHEST standard semantics. */
    public static final int SCHEMA_VERSION = 2;

    public TemplateMetadata {
        if (schematicSize < 0 || schematicModifiedMillis < 0) {
            throw new IllegalArgumentException("schematic fingerprint must not be negative");
        }
        Objects.requireNonNull(bounds);
        Objects.requireNonNull(entrance);
        Objects.requireNonNull(exit);
        normalMobs = List.copyOf(normalMobs);
        minibossMobs = List.copyOf(minibossMobs);
        playerSpawns = List.copyOf(playerSpawns);
        Objects.requireNonNull(bossSpawn);
        Objects.requireNonNull(rewardChest);
        secrets = List.copyOf(secrets);
        portalBlocks = Set.copyOf(portalBlocks);
        Objects.requireNonNull(contentHash);
    }

    public static TemplateMetadata fromTemplate(Template template, Path schematic) throws IOException {
        Objects.requireNonNull(template);
        Objects.requireNonNull(schematic);
        return new TemplateMetadata(Files.size(schematic), Files.getLastModifiedTime(schematic).toMillis(),
                template.bounds(), template.entrance(), template.exit(), template.normalMobs(),
                template.minibossMobs(), template.playerSpawns(), template.bossSpawn(), template.rewardChest(),
                template.secrets(), template.portalBlocks(), template.contentHash());
    }

    public Template toTemplate(String id, RoomDefinition definition) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(definition);
        return new Template(id, definition.type(), definition.capabilities(), bounds, entrance, exit,
                normalMobs, minibossMobs, playerSpawns, bossSpawn, rewardChest, secrets, portalBlocks,
                Set.of(), contentHash);
    }

    public Map<String, Object> values() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("schematic-size", schematicSize);
        values.put("schematic-modified", schematicModifiedMillis);
        values.put("bounds", Map.of("minimum", point(bounds.minimum()), "maximum", point(bounds.maximum())));
        entrance.ifPresent(value -> values.put("entrance", connector(value)));
        exit.ifPresent(value -> values.put("exit", connector(value)));
        values.put("normal-mobs", points(normalMobs));
        values.put("miniboss-mobs", points(minibossMobs));
        values.put("player-spawns", points(playerSpawns));
        bossSpawn.ifPresent(value -> values.put("boss-spawn", point(value)));
        rewardChest.ifPresent(value -> values.put("reward-chest", point(value)));
        values.put("secrets", secrets.stream().map(secret -> Map.of(
                "point", point(secret.point()), "kind", secret.kind().name().toLowerCase(Locale.ROOT))).toList());
        values.put("portal-blocks", points(portalBlocks));
        values.put("content-hash", contentHash);
        return values;
    }

    public static void write(BoostedConfigFactory factory, Path path, TemplateMetadata metadata) throws IOException {
        Objects.requireNonNull(factory);
        Objects.requireNonNull(path);
        Objects.requireNonNull(metadata);
        Files.createDirectories(path.toAbsolutePath().normalize().getParent());
        try {
            if (!Files.exists(path)) Files.writeString(path, "schema-version: " + SCHEMA_VERSION + "\n");
            BoostedCustomConfig config = factory.open(path);
            config.set("schema-version", SCHEMA_VERSION);
            config.set("template", metadata.values());
            if (!config.save()) throw new IOException("failed to save template metadata " + path.getFileName());
        } finally {
            factory.release(path);
        }
    }

    public static TemplateMetadata read(BoostedConfigFactory factory, Path path) throws IOException {
        Objects.requireNonNull(factory);
        Objects.requireNonNull(path);
        if (!Files.isRegularFile(path)) throw new IOException("template metadata is missing");
        try {
            Map<String, Object> root = BoostedConfigFactory.toPlainValues(factory.open(path));
            int schema = exactInt(root.get("schema-version"));
            if (schema != SCHEMA_VERSION) throw new IOException("unsupported template metadata schema " + schema);
            Map<String, Object> template = map(root.get("template"), "template");
            Map<String, Object> bounds = map(template.get("bounds"), "template.bounds");
            return new TemplateMetadata(exactLong(template.get("schematic-size")),
                    exactLong(template.get("schematic-modified")),
                    new Bounds(point(bounds.get("minimum")), point(bounds.get("maximum"))),
                    optionalConnector(template.get("entrance")), optionalConnector(template.get("exit")),
                    points(template.get("normal-mobs")), points(template.get("miniboss-mobs")),
                    points(template.get("player-spawns")), optionalPoint(template.get("boss-spawn")),
                    optionalPoint(template.get("reward-chest")), secrets(template.get("secrets")),
                    Set.copyOf(points(template.get("portal-blocks"))), string(template.get("content-hash"),
                            "template.content-hash"));
        } catch (IllegalArgumentException exception) {
            throw new IOException("invalid template metadata " + path.getFileName() + ": "
                    + exception.getMessage(), exception);
        } finally {
            factory.release(path);
        }
    }

    private static Map<String, Object> connector(Connector value) {
        return Map.of("kind", value.kind().name().toLowerCase(Locale.ROOT), "point", point(value.point()),
                "outward", value.outward().name().toLowerCase(Locale.ROOT));
    }

    private static Optional<Connector> optionalConnector(Object value) {
        if (value == null) return Optional.empty();
        Map<String, Object> map = map(value, "connector");
        ConnectorKind kind = ConnectorKind.valueOf(string(map.get("kind"), "connector.kind").toUpperCase(Locale.ROOT));
        return Optional.of(new Connector(kind, point(map.get("point")),
                Facing.valueOf(string(map.get("outward"), "connector.outward").toUpperCase(Locale.ROOT))));
    }

    private static List<Map<String, Integer>> points(Iterable<Point> points) {
        List<Map<String, Integer>> result = new ArrayList<>();
        for (Point point : points) result.add(point(point));
        return List.copyOf(result);
    }

    private static List<Point> points(Object value) {
        if (value == null) return List.of();
        List<?> values = list(value, "points");
        List<Point> result = new ArrayList<>(values.size());
        for (Object item : values) result.add(point(item));
        return List.copyOf(result);
    }

    private static List<Secret> secrets(Object value) {
        if (value == null) return List.of();
        List<?> values = list(value, "template.secrets");
        List<Secret> result = new ArrayList<>(values.size());
        for (Object item : values) {
            Map<String, Object> map = map(item, "template.secrets[]");
            result.add(new Secret(point(map.get("point")),
                    SecretKind.valueOf(string(map.get("kind"), "template.secrets[].kind").toUpperCase(Locale.ROOT))));
        }
        return List.copyOf(result);
    }

    private static Optional<Point> optionalPoint(Object value) {
        return value == null ? Optional.empty() : Optional.of(point(value));
    }

    private static Map<String, Integer> point(Point point) {
        return Map.of("x", point.x(), "y", point.y(), "z", point.z());
    }

    private static Point point(Object value) {
        Map<String, Object> map = map(value, "point");
        return new Point(exactInt(map.get("x")), exactInt(map.get("y")), exactInt(map.get("z")));
    }

    private static Map<String, Object> map(Object value, String route) {
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalArgumentException(route + " must be a map");
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static List<?> list(Object value, String route) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(route + " must be a list");
        return list;
    }

    private static String string(Object value, String route) {
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalArgumentException(route + " must be a non-empty string");
        }
        return string;
    }

    private static int exactInt(Object value) {
        if (value instanceof Number number) {
            try {
                return new java.math.BigDecimal(number.toString()).intValueExact();
            } catch (NumberFormatException | ArithmeticException ignored) {
                throw new IllegalArgumentException("value must be an exact integer");
            }
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
                throw new IllegalArgumentException("value must be an exact integer");
            }
        }
        throw new IllegalArgumentException("value must be an integer");
    }

    private static long exactLong(Object value) {
        if (value instanceof Number number) {
            try {
                return new java.math.BigDecimal(number.toString()).longValueExact();
            } catch (NumberFormatException | ArithmeticException ignored) {
                throw new IllegalArgumentException("value must be an exact long");
            }
        }
        if (value instanceof String string) {
            try {
                return Long.parseLong(string);
            } catch (NumberFormatException ignored) {
                throw new IllegalArgumentException("value must be an exact long");
            }
        }
        throw new IllegalArgumentException("value must be a long");
    }
}
