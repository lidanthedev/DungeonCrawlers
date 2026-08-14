package me.lidan.dungeonCrawlers.config.registry;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.*;
import me.lidan.dungeonCrawlers.config.BoostedConfigFactory;
import org.bukkit.Material;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ConfigLoader {
    private static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,63}$");
    private final EncounterRegistry encounters;
    private final Clock clock;
    private final BoostedConfigFactory configFactory;

    public ConfigLoader(EncounterRegistry encounters) {
        this(encounters, Clock.systemUTC(), new BoostedConfigFactory());
    }

    ConfigLoader(EncounterRegistry encounters, Clock clock) {
        this(encounters, clock, new BoostedConfigFactory());
    }

    ConfigLoader(EncounterRegistry encounters, Clock clock, BoostedConfigFactory configFactory) {
        this.encounters = encounters;
        this.clock = clock;
        this.configFactory = configFactory;
    }

    public ConfigLoadResult load(Path dataDirectory) {
        Parser parser = new Parser(configFactory);
        Path root = dataDirectory.toAbsolutePath().normalize();
        Map<String, ClassDefinition> classes = parseClasses(root.resolve("classes.yml"), parser);
        Map<String, BlessingDefinition> blessings = parseBlessings(root.resolve("blessings.yml"), parser);
        Map<String, RoomDefinition> rooms = parseRooms(root.resolve("rooms.yml"), parser);
        Map<String, FloorDefinition> floors = parseFloors(root.resolve("floors"), parser);
        crossValidate(floors, rooms, classes, blessings, parser);
        if (!parser.errors.isEmpty()) return new ConfigLoadResult(null, parser.errors, parser.warnings);
        try {
            String hash = hashFiles(root);
            return new ConfigLoadResult(new ConfigSnapshot(1, floors, rooms, classes, blessings,
                    encounters.snapshot(), hash, clock.instant()), parser.errors, parser.warnings);
        } catch (IOException exception) {
            parser.error("config hash failed: " + exception.getMessage());
            return new ConfigLoadResult(null, parser.errors, parser.warnings);
        }
    }

    private Map<String, ClassDefinition> parseClasses(Path file, Parser p) {
        Map<String, Object> root = p.file(file);
        p.schema(root, file);
        Map<String, Object> entries = p.map(root.get("classes"), "classes.yml:classes", true);
        Map<String, ClassDefinition> result = new LinkedHashMap<>();
        for (var pair : entries.entrySet()) {
            String id = p.id(pair.getKey(), "classes.yml");
            Map<String, Object> entry = p.map(pair.getValue(), "classes.yml:" + id, true);
            Material icon = p.material(entry.get("icon"), "classes.yml:" + id + ".icon");
            StatModifiers stats = p.stats(entry, "classes.yml:" + id);
            String display = p.string(entry.get("display-name"), "classes.yml:" + id + ".display-name");
            if (id != null && icon != null && display != null) result.put(id, new ClassDefinition(id, display, icon, stats));
        }
        if (result.isEmpty()) p.error("classes.yml:classes must not be empty");
        return result;
    }

    private Map<String, BlessingDefinition> parseBlessings(Path file, Parser p) {
        Map<String, Object> root = p.file(file);
        p.schema(root, file);
        Map<String, Object> entries = p.map(root.get("blessings"), "blessings.yml:blessings", true);
        Map<String, BlessingDefinition> result = new LinkedHashMap<>();
        for (var pair : entries.entrySet()) {
            String id = p.id(pair.getKey(), "blessings.yml");
            Map<String, Object> entry = p.map(pair.getValue(), "blessings.yml:" + id, true);
            Material icon = p.material(entry.get("icon"), "blessings.yml:" + id + ".icon");
            String display = p.string(entry.get("display-name"), "blessings.yml:" + id + ".display-name");
            String stackingRaw = p.string(entry.get("stacking"), "blessings.yml:" + id + ".stacking");
            BlessingStacking stacking = p.enumValue(BlessingStacking.class, stackingRaw,
                    "blessings.yml:" + id + ".stacking");
            int maxLevel = p.integer(entry.get("max-level"), "blessings.yml:" + id + ".max-level", 1, 1_000);
            Map<String, Object> perLevel = p.map(entry.get("per-level"), "blessings.yml:" + id + ".per-level", true);
            StatModifiers stats = p.stats(perLevel, "blessings.yml:" + id + ".per-level");
            if (id != null && icon != null && display != null && stacking != null && maxLevel > 0) {
                result.put(id, new BlessingDefinition(id, display, icon, stacking, maxLevel, stats));
            }
        }
        if (result.isEmpty()) p.error("blessings.yml:blessings must not be empty");
        return result;
    }

    private Map<String, RoomDefinition> parseRooms(Path file, Parser p) {
        Map<String, Object> root = p.file(file);
        p.schema(root, file);
        Map<String, Object> entries = p.map(root.get("rooms"), "rooms.yml:rooms", true);
        Map<String, RoomDefinition> result = new LinkedHashMap<>();
        for (var pair : entries.entrySet()) {
            String id = p.id(pair.getKey(), "rooms.yml");
            Map<String, Object> entry = p.map(pair.getValue(), "rooms.yml:" + id, true);
            RoomType type = p.enumValue(RoomType.class, p.string(entry.get("type"), "rooms.yml:" + id + ".type"),
                    "rooms.yml:" + id + ".type");
            Set<EncounterCapability> capabilities = new LinkedHashSet<>();
            if (entry.containsKey("encounters")) {
                for (String value : p.stringList(entry.get("encounters"), "rooms.yml:" + id + ".encounters")) {
                    EncounterCapability capability = p.enumValue(EncounterCapability.class, value,
                            "rooms.yml:" + id + ".encounters");
                    if (capability != null) capabilities.add(capability);
                }
            }
            if (type == RoomType.NORMAL && capabilities.isEmpty()) p.error("rooms.yml:" + id + " normal room requires encounters");
            if (type != null && type != RoomType.NORMAL && !capabilities.isEmpty()) {
                p.error("rooms.yml:" + id + " special room must not declare encounter capabilities");
            }
            int min = p.optionalInteger(entry.get("min-floor"), "rooms.yml:" + id + ".min-floor", 1, 1, 10_000);
            Integer max = entry.containsKey("max-floor")
                    ? p.integer(entry.get("max-floor"), "rooms.yml:" + id + ".max-floor", 1, 10_000) : null;
            if (max != null && max < min) p.error("rooms.yml:" + id + " max-floor must be >= min-floor");
            double weight = p.optionalDouble(entry.get("weight"), "rooms.yml:" + id + ".weight", 1, 0, Double.MAX_VALUE);
            if (id != null && type != null) result.put(id, new RoomDefinition(id, type, capabilities, min, max, weight));
        }
        if (result.isEmpty()) p.error("rooms.yml:rooms must not be empty");
        return result;
    }

    private Map<String, FloorDefinition> parseFloors(Path directory, Parser p) {
        Map<String, FloorDefinition> result = new LinkedHashMap<>();
        if (!Files.isDirectory(directory)) {
            p.error("floors directory is missing");
            return result;
        }
        List<Path> files;
        try (Stream<Path> stream = Files.list(directory)) {
            files = stream.filter(path -> path.getFileName().toString().endsWith(".yml")).sorted().toList();
        } catch (IOException exception) {
            p.error("cannot list floors: " + exception.getMessage());
            return result;
        }
        for (Path file : files) {
            Map<String, Object> root = p.file(file);
            p.schema(root, file);
            String prefix = "floors/" + file.getFileName() + ":";
            String id = p.id(p.string(root.get("id"), prefix + "id"), prefix);
            int number = p.integer(root.get("number"), prefix + "number", 1, 10_000);
            String display = p.string(root.get("display-name"), prefix + "display-name");
            Map<String, Object> templatesMap = p.map(root.get("templates"), prefix + "templates", true);
            TemplateRefs templates = new TemplateRefs(
                    p.id(p.string(templatesMap.get("start"), prefix + "templates.start"), prefix),
                    p.id(p.string(templatesMap.get("portal"), prefix + "templates.portal"), prefix),
                    p.id(p.string(templatesMap.get("boss"), prefix + "templates.boss"), prefix),
                    p.vector(templatesMap.getOrDefault("boss-offset", List.of(0, 0, 3000)), prefix + "templates.boss-offset"));
            Map<String, Object> generationMap = p.map(root.get("generation"), prefix + "generation", true);
            int rooms = p.integer(generationMap.get("rooms"), prefix + "generation.rooms", 0, 10_000);
            int minibosses = p.integer(generationMap.get("minibosses"), prefix + "generation.minibosses", 0, 10_000);
            if (minibosses > rooms) p.error(prefix + "generation.minibosses must be <= rooms");
            Generation generation = new Generation(rooms, minibosses,
                    p.optionalBoolean(generationMap.get("final-miniboss"), prefix + "generation.final-miniboss", false),
                    p.optionalInteger(generationMap.get("max-attempts-per-position"), prefix + "generation.max-attempts-per-position", 64, 1, 100_000),
                    p.optionalInteger(generationMap.get("collision-padding"), prefix + "generation.collision-padding", 1, 0, 1_000));
            Map<String, Object> mobs = p.map(root.get("mobs"), prefix + "mobs", true);
            List<String> normalMobs = p.stringList(mobs.get("normal"), prefix + "mobs.normal");
            List<String> minibossMobs = p.stringList(mobs.get("miniboss"), prefix + "mobs.miniboss");
            Map<String, Object> boss = p.map(root.get("boss"), prefix + "boss", true);
            String bossMob = p.string(boss.get("mob"), prefix + "boss.mob");
            String encounter = p.optionalString(boss.get("encounter"), "basic");
            if (!encounters.contains(encounter)) {
                p.warn(prefix + "unknown encounter " + encounter + "; using basic fallback");
                encounter = "basic";
            }
            Map<String, Object> classRoot = p.map(root.get("classes"), prefix + "classes", true);
            List<String> allowedClasses = p.idList(classRoot.get("allowed"), prefix + "classes.allowed");
            List<WeightedId> blessingRefs = p.weightedIds(root.get("blessings"), prefix + "blessings");
            Map<String, RewardDefinition> rewards = p.rewards(root.get("rewards"), prefix + "rewards");
            Limits limits = p.limits(root.get("limits"), prefix + "limits");
            if (id != null && display != null && templates.start() != null && templates.portal() != null
                    && templates.boss() != null && templates.bossOffset() != null && bossMob != null) {
                if (result.putIfAbsent(id, new FloorDefinition(id, number, display, templates, generation,
                        normalMobs, minibossMobs, bossMob, encounter, allowedClasses, blessingRefs, rewards, limits)) != null) {
                    p.error(prefix + "duplicate floor id " + id);
                }
            }
        }
        if (result.isEmpty()) p.error("floors must not be empty");
        return result;
    }

    private void crossValidate(Map<String, FloorDefinition> floors, Map<String, RoomDefinition> rooms,
                               Map<String, ClassDefinition> classes, Map<String, BlessingDefinition> blessings,
                               Parser p) {
        Set<Integer> floorNumbers = new LinkedHashSet<>();
        for (FloorDefinition floor : floors.values()) {
            if (!floorNumbers.add(floor.number())) {
                p.error("duplicate floor number " + floor.number());
            }
            requireRoomType(floor.id(), floor.templates().start(), RoomType.START, rooms, p);
            requireRoomType(floor.id(), floor.templates().portal(), RoomType.PORTAL, rooms, p);
            requireRoomType(floor.id(), floor.templates().boss(), RoomType.BOSS, rooms, p);
            floor.allowedClasses().stream().filter(id -> !classes.containsKey(id))
                    .forEach(id -> p.error("floor " + floor.id() + " references missing class " + id));
            floor.blessings().stream().map(WeightedId::id).filter(id -> !blessings.containsKey(id))
                    .forEach(id -> p.error("floor " + floor.id() + " references missing blessing " + id));
            boolean normalAvailable = rooms.values().stream().anyMatch(room -> room.type() == RoomType.NORMAL
                    && room.capabilities().contains(EncounterCapability.NORMAL) && supportsFloor(room, floor.number()));
            boolean minibossAvailable = rooms.values().stream().anyMatch(room -> room.type() == RoomType.NORMAL
                    && room.capabilities().contains(EncounterCapability.MINIBOSS) && supportsFloor(room, floor.number()));
            if (floor.generation().rooms() - floor.generation().minibosses() > 0 && !normalAvailable) {
                p.error("floor " + floor.id() + " has no compatible normal room");
            }
            if ((floor.generation().minibosses() > 0 || floor.generation().finalMiniboss()) && !minibossAvailable) {
                p.error("floor " + floor.id() + " has no compatible miniboss room");
            }
        }
    }

    private static boolean supportsFloor(RoomDefinition room, int floor) {
        return room.minFloor() <= floor && (room.maxFloor() == null || room.maxFloor() >= floor);
    }

    private static void requireRoomType(String floor, String roomId, RoomType expected,
                                        Map<String, RoomDefinition> rooms, Parser p) {
        RoomDefinition room = rooms.get(roomId);
        if (room == null) p.error("floor " + floor + " references missing room " + roomId);
        else if (room.type() != expected) p.error("floor " + floor + " requires " + expected + " room at " + roomId);
    }

    private static String hashFiles(Path root) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
        List<Path> files = new ArrayList<>(List.of(root.resolve("classes.yml"), root.resolve("blessings.yml"),
                root.resolve("rooms.yml")));
        if (Files.isDirectory(root.resolve("floors"))) {
            try (Stream<Path> stream = Files.list(root.resolve("floors"))) {
                files.addAll(stream.filter(Files::isRegularFile).sorted().toList());
            }
        }
        for (Path file : files.stream().sorted(Comparator.comparing(path -> root.relativize(path).toString())).toList()) {
            digest.update(root.relativize(file).toString().replace('\\', '/').getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Files.readAllBytes(file));
            digest.update((byte) 0xff);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static final class Parser {
        private final BoostedConfigFactory configFactory;
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        private Parser(BoostedConfigFactory configFactory) {
            this.configFactory = configFactory;
        }

        void error(String value) { errors.add(value); }
        void warn(String value) { warnings.add(value); }

        Map<String, Object> file(Path path) {
            if (!Files.isRegularFile(path)) { error(path.getFileName() + " is missing"); return Map.of(); }
            try {
                return configFactory.read(path);
            } catch (IOException | RuntimeException exception) {
                error(path.getFileName() + " cannot be loaded: " + exception.getMessage()); return Map.of();
            }
        }

        void schema(Map<String, Object> root, Path file) {
            if (!(root.get("schema-version") instanceof Number number) || number.intValue() != 1) {
                error(file.getFileName() + ":schema-version must be 1");
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> map(Object value, String path, boolean required) {
            if (value instanceof Map<?, ?> raw) {
                Map<String, Object> result = new LinkedHashMap<>();
                raw.forEach((key, item) -> result.put(String.valueOf(key), item));
                return result;
            }
            if (required || value != null) error(path + " must be a map");
            return Map.of();
        }

        String id(String value, String path) {
            if (value == null) return null;
            if (!ID.matcher(value).matches()) { error(path + " invalid id " + value); return null; }
            return value;
        }

        String string(Object value, String path) {
            if (!(value instanceof String text) || text.isBlank()) { error(path + " must be a non-blank string"); return null; }
            return text;
        }

        String optionalString(Object value, String fallback) {
            return value instanceof String text && !text.isBlank() ? text : fallback;
        }

        Material material(Object value, String path) {
            String name = string(value, path);
            Material material = name == null ? null : Material.matchMaterial(name);
            if (name != null && material == null) error(path + " has invalid material " + name);
            return material;
        }

        <E extends Enum<E>> E enumValue(Class<E> type, String value, String path) {
            if (value == null) return null;
            try { return Enum.valueOf(type, value.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException exception) { error(path + " has invalid value " + value); return null; }
        }

        int integer(Object value, String path, int minimum, int maximum) {
            if (!(value instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())
                    || number.longValue() < minimum || number.longValue() > maximum) {
                error(path + " must be an integer in " + minimum + ".." + maximum); return minimum - 1;
            }
            return number.intValue();
        }

        int optionalInteger(Object value, String path, int fallback, int minimum, int maximum) {
            return value == null ? fallback : integer(value, path, minimum, maximum);
        }

        long optionalLong(Object value, String path, long fallback, long minimum, long maximum) {
            if (value == null) return fallback;
            try {
                long parsed = value instanceof Number number
                        ? new BigDecimal(number.toString()).longValueExact() : minimum - 1;
                if (parsed < minimum || parsed > maximum) throw new ArithmeticException();
                return parsed;
            } catch (NumberFormatException | ArithmeticException exception) {
                error(path + " must be an integer in " + minimum + ".." + maximum);
                return fallback;
            }
        }

        double optionalDouble(Object value, String path, double fallback, double exclusiveMinimum, double maximum) {
            if (value == null) return fallback;
            if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                    || number.doubleValue() <= exclusiveMinimum || number.doubleValue() > maximum) {
                error(path + " must be finite and > " + exclusiveMinimum); return fallback;
            }
            return number.doubleValue();
        }

        boolean optionalBoolean(Object value, String path, boolean fallback) {
            if (value == null) return fallback;
            if (!(value instanceof Boolean flag)) { error(path + " must be true or false"); return fallback; }
            return flag;
        }

        List<String> stringList(Object value, String path) {
            if (!(value instanceof List<?> list)) { error(path + " must be a list"); return List.of(); }
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                String text = string(item, path + "[]");
                if (text != null) result.add(text);
            }
            return List.copyOf(result);
        }

        List<String> idList(Object value, String path) {
            return stringList(value, path).stream().map(item -> id(item, path)).filter(java.util.Objects::nonNull).toList();
        }

        Vector3i vector(Object value, String path) {
            if (!(value instanceof List<?> list) || list.size() != 3 || list.stream().anyMatch(item -> !(item instanceof Number))) {
                error(path + " must contain three integers"); return null;
            }
            int[] values = new int[3];
            for (int index = 0; index < 3; index++) {
                Number number = (Number) list.get(index);
                if (number.doubleValue() != Math.rint(number.doubleValue())) { error(path + " must contain integers"); return null; }
                values[index] = number.intValue();
            }
            return new Vector3i(values[0], values[1], values[2]);
        }

        StatModifiers stats(Map<String, Object> root, String path) {
            return new StatModifiers(statField(root, "stat-add", path, false),
                    statField(root, "stat-multiply", path, true));
        }

        Map<StatType, Double> statField(Map<String, Object> root, String key, String path, boolean multiplier) {
            if (!root.containsKey(key)) return Map.of();
            return statMap(root.get(key), path + "." + key, multiplier);
        }

        Map<StatType, Double> statMap(Object value, String path, boolean multiplier) {
            Map<String, Object> source = map(value, path, true);
            Map<StatType, Double> result = new LinkedHashMap<>();
            for (var pair : source.entrySet()) {
                StatType stat = enumValue(StatType.class, pair.getKey(), path);
                if (!(pair.getValue() instanceof Number number) || !Double.isFinite(number.doubleValue())) {
                    error(path + "." + pair.getKey() + " must be finite"); continue;
                }
                double amount = number.doubleValue();
                if (multiplier && (amount < 0.01 || amount > 100)) error(path + "." + pair.getKey() + " must be in 0.01..100");
                else if (!multiplier && (amount < -1_000_000 || amount > 1_000_000)) error(path + "." + pair.getKey() + " is outside supported range");
                else if (stat != null) result.put(stat, amount);
            }
            return result;
        }

        List<WeightedId> weightedIds(Object value, String path) {
            if (!(value instanceof List<?> list)) { error(path + " must be a list"); return List.of(); }
            List<WeightedId> result = new ArrayList<>();
            for (Object item : list) {
                Map<String, Object> entry = map(item, path + "[]", true);
                String id = id(string(entry.get("id"), path + "[].id"), path);
                double weight = optionalDouble(entry.get("weight"), path + "[].weight", 1, 0, Double.MAX_VALUE);
                if (id != null) result.add(new WeightedId(id, weight));
            }
            return result;
        }

        Map<String, RewardDefinition> rewards(Object value, String path) {
            Map<String, Object> entries = map(value, path, true);
            Map<String, RewardDefinition> result = new LinkedHashMap<>();
            for (var pair : entries.entrySet()) {
                String id = id(pair.getKey(), path);
                Map<String, Object> entry = map(pair.getValue(), path + "." + pair.getKey(), true);
                boolean enabled = optionalBoolean(entry.get("enabled"), path + "." + pair.getKey() + ".enabled", true);
                long price = optionalLong(entry.get("price"), path + "." + pair.getKey() + ".price", 0, 0, Long.MAX_VALUE);
                int minScore = optionalInteger(entry.get("min-score"), path + "." + pair.getKey() + ".min-score", 0, 0, Integer.MAX_VALUE);
                int rolls = optionalInteger(entry.get("rolls"), path + "." + pair.getKey() + ".rolls", 1, 1, 1_000);
                boolean unique = optionalBoolean(entry.get("unique"), path + "." + pair.getKey() + ".unique", false);
                List<RewardItem> items = rewardItems(entry.get("items"), path + "." + pair.getKey() + ".items");
                if (unique && rolls > items.size()) {
                    error(path + "." + pair.getKey() + " unique rolls cannot exceed item count");
                }
                if (id != null) result.put(id, new RewardDefinition(enabled, price, minScore, rolls, unique, items));
            }
            return result;
        }

        List<RewardItem> rewardItems(Object value, String path) {
            if (!(value instanceof List<?> list) || list.isEmpty()) { error(path + " must be a non-empty list"); return List.of(); }
            List<RewardItem> result = new ArrayList<>();
            for (Object item : list) {
                Map<String, Object> entry = map(item, path + "[]", true);
                String itemId = id(string(entry.get("item"), path + "[].item"), path);
                double weight = optionalDouble(entry.get("weight"), path + "[].weight", 1, 0, Double.MAX_VALUE);
                int[] amount = amount(entry.get("amount"), path + "[].amount");
                if (itemId != null) result.add(new RewardItem(itemId, weight, amount[0], amount[1]));
            }
            return result;
        }

        int[] amount(Object value, String path) {
            if (value == null) return new int[]{1, 1};
            if (value instanceof Number number) {
                int amount = integer(number, path, 1, Integer.MAX_VALUE); return new int[]{amount, amount};
            }
            if (value instanceof String text && text.matches("[1-9][0-9]*-[1-9][0-9]*")) {
                String[] parts = text.split("-", 2);
                int minimum = Integer.parseInt(parts[0]); int maximum = Integer.parseInt(parts[1]);
                if (minimum <= maximum) return new int[]{minimum, maximum};
            }
            error(path + " must be a positive integer or min-max range"); return new int[]{1, 1};
        }

        Limits limits(Object value, String path) {
            Map<String, Object> map = map(value, path, true);
            return new Limits(optionalInteger(map.get("max-party-size"), path + ".max-party-size", 5, 1, 100),
                    optionalInteger(map.get("max-template-dimension"), path + ".max-template-dimension", 512, 1, 10_000),
                    optionalLong(map.get("max-template-volume"), path + ".max-template-volume", 16_777_216, 1, Long.MAX_VALUE),
                    optionalInteger(map.get("max-loaded-chunks-per-instance"), path + ".max-loaded-chunks-per-instance", 256, 1, 100_000),
                    optionalInteger(map.get("mob-respawn-retries"), path + ".mob-respawn-retries", 2, 0, 100),
                    optionalInteger(map.get("repository-queue-capacity"), path + ".repository-queue-capacity", 1_000, 1, 1_000_000));
        }
    }
}
