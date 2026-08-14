package me.lidan.dungeonCrawlers.core.template;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.EncounterCapability;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomType;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Block;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Connector;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.ConnectorKind;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.EmeraldPolicy;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Facing;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Secret;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.SecretKind;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Selection;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Template;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class TemplateValidator {
    private static final Set<String> AIR = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");
    private static final String ENTRANCE = "dungeoncrawlers:entrance";
    private static final String EXIT = "dungeoncrawlers:exit";

    public ValidationResult validate(String id, RoomType type, Set<EncounterCapability> capabilities,
                                     Selection selection, EmeraldPolicy emeraldPolicy) {
        List<String> errors = new ArrayList<>();
        List<Connector> entrances = new ArrayList<>();
        List<Connector> exits = new ArrayList<>();
        List<Point> normalMobs = new ArrayList<>();
        List<Point> minibossMobs = new ArrayList<>();
        List<Point> playerSpawns = new ArrayList<>();
        List<Point> bossSpawns = new ArrayList<>();
        List<Point> rewards = new ArrayList<>();
        List<Secret> secrets = new ArrayList<>();
        Set<Point> portals = new LinkedHashSet<>();
        Set<Point> solids = new LinkedHashSet<>();

        selection.blocks().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
            Point point = entry.getKey();
            Block block = entry.getValue();
            if (!AIR.contains(block.type())) solids.add(point);
            if (block.is("jigsaw")) {
                Connector connector = connector(point, block, selection, errors);
                if (connector != null) {
                    (connector.kind() == ConnectorKind.ENTRANCE ? entrances : exits).add(connector);
                    if (connector.kind() == ConnectorKind.ENTRANCE) solids.remove(point);
                }
            } else if (block.is("gray_concrete_powder")) {
                normalMobs.add(point); solids.remove(point);
            } else if (block.is("yellow_concrete_powder")) {
                minibossMobs.add(point); solids.remove(point);
            } else if (block.is("emerald_block")) {
                playerSpawns.add(point);
                if (emeraldPolicy == EmeraldPolicy.REPLACE) solids.remove(point);
            } else if (block.is("red_concrete_powder")) {
                bossSpawns.add(point); solids.remove(point);
            } else if (block.is("lime_concrete_powder")) {
                rewards.add(point); solids.remove(point);
            } else if (block.is("chest")) {
                secrets.add(new Secret(point, SecretKind.STANDARD));
            } else if (block.is("trapped_chest")) {
                secrets.add(new Secret(point, SecretKind.BLESSING));
            } else if (block.is("nether_portal")) {
                portals.add(point);
            }
        });

        validateType(type, capabilities, entrances, exits, normalMobs, minibossMobs, playerSpawns,
                bossSpawns, rewards, portals, errors);
        int components = connectedComponents(portals);
        if (type == RoomType.PORTAL && components != 1) {
            errors.add("PORTAL requires exactly one connected Nether Portal component; found " + components);
        }

        if (!errors.isEmpty()) return new ValidationResult(Optional.empty(), errors);
        Template template = new Template(id, type, capabilities, selection.bounds(), optionalOne(entrances),
                optionalOne(exits), normalMobs, minibossMobs, playerSpawns, optionalPoint(bossSpawns),
                optionalPoint(rewards), secrets, portals, solids,
                contentHash(id, type, capabilities, emeraldPolicy, selection));
        return new ValidationResult(Optional.of(template), List.of());
    }

    private static Connector connector(Point point, Block block, Selection selection, List<String> errors) {
        String prefix = "jigsaw " + point + "; ";
        String name = block.nbt().get("name");
        ConnectorKind kind = switch (name == null ? "" : name) {
            case ENTRANCE -> ConnectorKind.ENTRANCE;
            case EXIT -> ConnectorKind.EXIT;
            default -> null;
        };
        if (kind == null) errors.add(prefix + "name must be " + ENTRANCE + " or " + EXIT);
        exact(block.nbt(), "target", "dungeoncrawlers:connector", prefix, errors);
        exact(block.nbt(), "pool", "minecraft:empty", prefix, errors);
        exact(block.nbt(), "final_state", "minecraft:air", prefix, errors);
        String orientation = block.states().get("orientation");
        Facing facing = switch (orientation == null ? "" : orientation.toLowerCase(Locale.ROOT)) {
            case "north_up" -> Facing.NORTH;
            case "east_up" -> Facing.EAST;
            case "south_up" -> Facing.SOUTH;
            case "west_up" -> Facing.WEST;
            default -> null;
        };
        if (facing == null) errors.add(prefix + "orientation must be north_up, east_up, south_up, or west_up");
        if (facing != null) validateDoorPlane(point, facing, selection, prefix, errors);
        return kind == null || facing == null ? null : new Connector(kind, point, facing);
    }

    private static void exact(java.util.Map<String, String> nbt, String key, String expected,
                              String prefix, List<String> errors) {
        if (!expected.equals(nbt.get(key))) errors.add(prefix + key + " must be " + expected);
    }

    private static void validateDoorPlane(Point center, Facing facing, Selection selection,
                                          String prefix, List<String> errors) {
        boolean face = switch (facing) {
            case NORTH -> center.z() == selection.bounds().minimum().z();
            case EAST -> center.x() == selection.bounds().maximum().x();
            case SOUTH -> center.z() == selection.bounds().maximum().z();
            case WEST -> center.x() == selection.bounds().minimum().x();
        };
        if (!face) errors.add(prefix + "connector must lie on its outward selection face");
        Set<Point> plane = TemplateModels.plane(center, facing);
        if (!plane.stream().allMatch(selection.bounds()::contains)) {
            errors.add(prefix + "centered 3x3 connector plane must be inside the selection");
        }
        for (Point point : plane.stream().sorted().toList()) {
            if (point.equals(center) || !selection.bounds().contains(point)) continue;
            if (!AIR.contains(selection.block(point).type())) {
                errors.add(prefix + "door plane block " + point + " must be air, cave_air, or void_air");
            }
        }
        Point outwardCenter = center.add(facing.vector());
        if (TemplateModels.plane(outwardCenter, facing).stream().anyMatch(selection.bounds()::contains)) {
            errors.add(prefix + "outward connector clearance must lie outside the selection");
        }
    }

    private static void validateType(RoomType type, Set<EncounterCapability> capabilities,
                                     List<Connector> entrances, List<Connector> exits,
                                     List<Point> normalMobs, List<Point> minibossMobs, List<Point> playerSpawns,
                                     List<Point> bossSpawns, List<Point> rewards, Set<Point> portals,
                                     List<String> errors) {
        requireCount(type, "entrance JIGSAW (dungeoncrawlers:entrance)", entrances.size(),
                type == RoomType.NORMAL || type == RoomType.PORTAL ? 1 : 0, errors);
        requireCount(type, "exit JIGSAW (dungeoncrawlers:exit)", exits.size(),
                type == RoomType.NORMAL || type == RoomType.START ? 1 : 0, errors);
        requireAllowed(type, "normal mob (GRAY_CONCRETE_POWDER)", normalMobs.size(), type == RoomType.NORMAL, errors);
        requireAllowed(type, "miniboss mob (YELLOW_CONCRETE_POWDER)", minibossMobs.size(), type == RoomType.NORMAL, errors);
        requireAllowed(type, "player spawn (EMERALD_BLOCK)", playerSpawns.size(),
                type == RoomType.START || type == RoomType.BOSS, errors);
        requireAllowed(type, "boss spawn (RED_CONCRETE_POWDER)", bossSpawns.size(), type == RoomType.BOSS, errors);
        requireAllowed(type, "reward chest (LIME_CONCRETE_POWDER)", rewards.size(), type == RoomType.BOSS, errors);
        requireAllowed(type, "portal trigger (NETHER_PORTAL)", portals.size(), type == RoomType.PORTAL, errors);

        if ((type == RoomType.START || type == RoomType.BOSS) && playerSpawns.isEmpty()) {
            errors.add(type + " requires at least one player spawn marker (EMERALD_BLOCK)");
        }
        if (type == RoomType.BOSS) {
            requireCount(type, "boss spawn (RED_CONCRETE_POWDER)", bossSpawns.size(), 1, errors);
            requireCount(type, "reward chest (LIME_CONCRETE_POWDER)", rewards.size(), 1, errors);
        }
        if (type == RoomType.NORMAL) {
            boolean supportsNormal = capabilities.contains(EncounterCapability.NORMAL);
            boolean supportsMiniboss = capabilities.contains(EncounterCapability.MINIBOSS);
            if (supportsNormal && supportsMiniboss && normalMobs.isEmpty() && minibossMobs.isEmpty()) {
                errors.add("NORMAL or MINIBOSS capability requires at least one matching mob marker "
                        + "(GRAY_CONCRETE_POWDER or YELLOW_CONCRETE_POWDER)");
            } else if (supportsNormal && !supportsMiniboss && normalMobs.isEmpty()) {
                errors.add("NORMAL capability requires at least one normal mob marker (GRAY_CONCRETE_POWDER)");
            }
            if (supportsMiniboss && !supportsNormal && minibossMobs.isEmpty()) {
                errors.add("MINIBOSS capability requires at least one miniboss mob marker (YELLOW_CONCRETE_POWDER)");
            }
            if (!supportsNormal && !normalMobs.isEmpty()) {
                errors.add("normal mob markers require NORMAL capability; block=GRAY_CONCRETE_POWDER");
            }
            if (!supportsMiniboss && !minibossMobs.isEmpty()) {
                errors.add("miniboss mob markers require MINIBOSS capability; block=YELLOW_CONCRETE_POWDER");
            }
        } else if (!capabilities.isEmpty()) {
            errors.add(type + " must not declare encounter capabilities");
        }
    }

    private static void requireCount(RoomType type, String label, int actual, int expected, List<String> errors) {
        if (actual != expected) errors.add(type + " requires exactly " + expected + " " + label + " marker(s); found " + actual);
    }

    private static void requireAllowed(RoomType type, String label, int count, boolean allowed, List<String> errors) {
        if (!allowed && count > 0) errors.add(type + " must not contain " + label + " markers");
    }

    private static int connectedComponents(Set<Point> points) {
        Set<Point> remaining = new LinkedHashSet<>(points);
        int components = 0;
        while (!remaining.isEmpty()) {
            components++;
            ArrayDeque<Point> queue = new ArrayDeque<>();
            queue.add(remaining.iterator().next());
            while (!queue.isEmpty()) {
                Point current = queue.remove();
                if (!remaining.remove(current)) continue;
                for (Point direction : List.of(new Point(1, 0, 0), new Point(-1, 0, 0), new Point(0, 1, 0),
                        new Point(0, -1, 0), new Point(0, 0, 1), new Point(0, 0, -1))) {
                    Point neighbor = current.add(direction);
                    if (remaining.contains(neighbor)) queue.add(neighbor);
                }
            }
        }
        return components;
    }

    private static <T> Optional<T> optionalOne(List<T> values) {
        return values.size() == 1 ? Optional.of(values.getFirst()) : Optional.empty();
    }

    private static Optional<Point> optionalPoint(List<Point> values) {
        return optionalOne(values);
    }

    private static String contentHash(String id, RoomType type, Set<EncounterCapability> capabilities,
                                      EmeraldPolicy emeraldPolicy, Selection selection) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((id + "|" + type + "|" + capabilities.stream().sorted().toList() + "|"
                    + emeraldPolicy + "\n").getBytes(StandardCharsets.UTF_8));
            selection.blocks().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
                String canonical = entry.getKey() + "|" + entry.getValue().type() + "|"
                        + sorted(entry.getValue().states()) + "|" + sorted(entry.getValue().nbt()) + "\n";
                digest.update(canonical.getBytes(StandardCharsets.UTF_8));
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String sorted(java.util.Map<String, String> values) {
        return values.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(java.util.stream.Collectors.joining(","));
    }

    public record ValidationResult(Optional<Template> template, List<String> errors) {
        public ValidationResult {
            errors = List.copyOf(errors);
            if (template.isPresent() == !errors.isEmpty()) {
                throw new IllegalArgumentException("a validation result must contain either a template or errors");
            }
        }

        public boolean successful() {
            return template.isPresent();
        }
    }
}
