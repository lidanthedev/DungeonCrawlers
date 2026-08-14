package me.lidan.dungeonCrawlers.authoring;

import me.lidan.cavecrawlers.utils.BoostedCustomConfig;
import me.lidan.dungeonCrawlers.config.BoostedConfigFactory;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ConfigSnapshot;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.EncounterCapability;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.FloorDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomType;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class TemplateAuthoringService {
    private static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,63}$");
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneOffset.UTC);
    private final Path dataDirectory;
    private final Path roomsFile;
    private final Path templateDirectory;
    private final Path backupDirectory;
    private final BoostedConfigFactory configs;
    private final Supplier<ConfigSnapshot> snapshot;
    private final Clock clock;
    private final int backupRetention;
    private final FailureInjector failures;

    public TemplateAuthoringService(Path dataDirectory, BoostedConfigFactory configs,
                                    Supplier<ConfigSnapshot> snapshot, int backupRetention) {
        this(dataDirectory, configs, snapshot, Clock.systemUTC(), backupRetention, stage -> { });
    }

    TemplateAuthoringService(Path dataDirectory, BoostedConfigFactory configs, Supplier<ConfigSnapshot> snapshot,
                             Clock clock, int backupRetention, FailureInjector failures) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory).toAbsolutePath().normalize();
        this.roomsFile = this.dataDirectory.resolve("rooms.yml");
        this.templateDirectory = this.dataDirectory.resolve("templates");
        this.backupDirectory = this.dataDirectory.resolve("backups/authoring");
        this.configs = Objects.requireNonNull(configs);
        this.snapshot = Objects.requireNonNull(snapshot);
        this.clock = Objects.requireNonNull(clock);
        if (backupRetention < 1 || backupRetention > 1_000) {
            throw new IllegalArgumentException("backup retention must be in 1..1000");
        }
        this.backupRetention = backupRetention;
        this.failures = Objects.requireNonNull(failures);
    }

    public synchronized OperationResult create(String id, RoomType type, Set<EncounterCapability> capabilities,
                                               byte[] schematic) {
        String invalid = validateInput(id, type, capabilities, schematic);
        if (invalid != null) return OperationResult.failure(invalid);
        ConfigSnapshot current = snapshot.get();
        Path target = schematicPath(id);
        if (current.rooms().containsKey(id) || Files.exists(target)) {
            return OperationResult.failure("room already exists: " + id);
        }
        Path staging = stagingDirectory();
        byte[] originalRooms;
        try {
            originalRooms = Files.readAllBytes(roomsFile);
            Files.createDirectories(staging);
            Path stagedRooms = staging.resolve("rooms.yml");
            Path stagedSchematic = staging.resolve(id + ".schem");
            Files.write(stagedRooms, originalRooms, StandardOpenOption.CREATE_NEW);
            BoostedCustomConfig config = configs.open(stagedRooms);
            config.set("rooms." + id, roomValues(type, capabilities));
            if (!config.save()) throw new IOException("failed to save staged rooms.yml");
            Path commitRooms = staging.resolve("rooms-commit.yml");
            Files.write(commitRooms, Files.readAllBytes(stagedRooms), StandardOpenOption.CREATE_NEW);
            Files.write(stagedSchematic, schematic, StandardOpenOption.CREATE_NEW);
            failures.before(FailureStage.BEFORE_COMMIT);
            Files.createDirectories(templateDirectory);
            atomicReplace(commitRooms, roomsFile);
            try {
                atomicCreate(stagedSchematic, target);
            } catch (Exception exception) {
                restoreRooms(originalRooms, staging);
                throw exception;
            }
            return OperationResult.success("created room " + id);
        } catch (Exception exception) {
            return OperationResult.failure("create failed: " + message(exception));
        } finally {
            configs.release(staging.resolve("rooms.yml"));
            cleanupStaging(staging);
        }
    }

    public synchronized OperationResult update(String id, byte[] schematic) {
        String invalid = validateIdAndPayload(id, schematic);
        if (invalid != null) return OperationResult.failure(invalid);
        if (!snapshot.get().rooms().containsKey(id)) return OperationResult.failure("unknown room: " + id);
        Path target = schematicPath(id);
        if (Files.exists(target) && !Files.isRegularFile(target)) {
            return OperationResult.failure("room schematic path is not a file: " + id);
        }
        boolean replacing = Files.isRegularFile(target);
        Path staging = stagingDirectory();
        try {
            Files.createDirectories(staging);
            Path staged = staging.resolve(id + ".schem");
            Files.write(staged, schematic, StandardOpenOption.CREATE_NEW);
            Path backup = null;
            if (replacing) {
                backup = createBackup(id);
                Files.copy(target, backup.resolve(id + ".schem"), StandardCopyOption.COPY_ATTRIBUTES);
                Files.copy(roomsFile, backup.resolve("rooms.yml"), StandardCopyOption.COPY_ATTRIBUTES);
            }
            failures.before(FailureStage.BEFORE_COMMIT);
            Files.createDirectories(templateDirectory);
            if (replacing) atomicReplace(staged, target);
            else atomicCreate(staged, target);
            pruneBackups();
            return OperationResult.success("updated room " + id + "; generation metadata preserved"
                    + (backup == null ? "; initial schematic created" : "; backup=" + dataDirectory.relativize(backup)));
        } catch (Exception exception) {
            return OperationResult.failure("update failed: " + message(exception));
        } finally {
            cleanupStaging(staging);
        }
    }

    public synchronized OperationResult delete(String id, Set<String> activeTemplateIds) {
        if (!validId(id)) return OperationResult.failure("invalid room id: " + id);
        Objects.requireNonNull(activeTemplateIds, "activeTemplateIds");
        ConfigSnapshot current = snapshot.get();
        RoomDefinition room = current.rooms().get(id);
        if (room == null) return OperationResult.failure("unknown room: " + id);
        if (activeTemplateIds.contains(id)) return OperationResult.failure("room is active: " + id);
        List<String> references = references(current, id);
        if (!references.isEmpty()) return OperationResult.failure("room is referenced: " + String.join(", ", references));

        Path target = schematicPath(id);
        if (!Files.isRegularFile(target)) return OperationResult.failure("room schematic is missing: " + id);
        Path staging = stagingDirectory();
        Path backup = null;
        try {
            byte[] originalRooms = Files.readAllBytes(roomsFile);
            Files.createDirectories(staging);
            Path stagedRooms = staging.resolve("rooms.yml");
            Files.write(stagedRooms, originalRooms, StandardOpenOption.CREATE_NEW);
            BoostedCustomConfig config = configs.open(stagedRooms);
            if (!config.remove("rooms." + id)) throw new IOException("room entry disappeared during delete");
            if (!config.save()) throw new IOException("failed to save staged rooms.yml");
            Path commitRooms = staging.resolve("rooms-commit.yml");
            Files.write(commitRooms, Files.readAllBytes(stagedRooms), StandardOpenOption.CREATE_NEW);
            backup = createBackup(id);
            Path archived = backup.resolve(id + ".schem");
            Files.copy(roomsFile, backup.resolve("rooms.yml"), StandardCopyOption.COPY_ATTRIBUTES);
            failures.before(FailureStage.BEFORE_COMMIT);
            atomicCreate(target, archived);
            try {
                atomicReplace(commitRooms, roomsFile);
            } catch (Exception exception) {
                atomicCreate(archived, target);
                throw exception;
            }
            pruneBackups();
            return OperationResult.success("deleted room " + id + "; recoverable backup="
                    + dataDirectory.relativize(backup));
        } catch (Exception exception) {
            return OperationResult.failure("delete failed: " + message(exception));
        } finally {
            configs.release(staging.resolve("rooms.yml"));
            cleanupStaging(staging);
        }
    }

    public byte[] schematic(String id) throws IOException {
        if (!validId(id)) throw new IllegalArgumentException("invalid room id: " + id);
        return Files.readAllBytes(schematicPath(id));
    }

    public Path schematicPath(String id) {
        if (!validId(id)) throw new IllegalArgumentException("invalid room id: " + id);
        Path path = templateDirectory.resolve(id + ".schem").normalize();
        if (!path.startsWith(templateDirectory)) throw new IllegalArgumentException("room path escaped template directory");
        return path;
    }

    private static String validateInput(String id, RoomType type, Set<EncounterCapability> capabilities,
                                        byte[] schematic) {
        String invalid = validateIdAndPayload(id, schematic);
        if (invalid != null) return invalid;
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(capabilities, "capabilities");
        if (type == RoomType.NORMAL && capabilities.isEmpty()) return "NORMAL room requires encounter capabilities";
        if (type != RoomType.NORMAL && !capabilities.isEmpty()) return type + " room must not declare capabilities";
        return null;
    }

    private static String validateIdAndPayload(String id, byte[] schematic) {
        if (!validId(id)) return "invalid room id: " + id;
        if (schematic == null || schematic.length == 0) return "schematic must not be empty";
        return null;
    }

    private static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    private static Map<String, Object> roomValues(RoomType type, Set<EncounterCapability> capabilities) {
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("type", type.name().toLowerCase(Locale.ROOT));
        if (type == RoomType.NORMAL) {
            values.put("encounters", capabilities.stream().sorted().map(value -> value.name().toLowerCase(Locale.ROOT)).toList());
            values.put("min-floor", 1);
            values.put("weight", 1);
        }
        return values;
    }

    private static List<String> references(ConfigSnapshot snapshot, String removedId) {
        List<String> references = new ArrayList<>();
        for (FloorDefinition floor : snapshot.floors().values()) {
            if (floor.templates().start().equals(removedId)) references.add(floor.id() + ".templates.start");
            if (floor.templates().portal().equals(removedId)) references.add(floor.id() + ".templates.portal");
            if (floor.templates().boss().equals(removedId)) references.add(floor.id() + ".templates.boss");
            long normalCandidates = candidates(snapshot, removedId, floor, EncounterCapability.NORMAL);
            long minibossCandidates = candidates(snapshot, removedId, floor, EncounterCapability.MINIBOSS);
            if (floor.generation().rooms() - floor.generation().minibosses() > 0 && normalCandidates == 0) {
                references.add(floor.id() + ".generation normal composition");
            }
            if ((floor.generation().minibosses() > 0 || floor.generation().finalMiniboss())
                    && minibossCandidates == 0) {
                references.add(floor.id() + ".generation miniboss composition");
            }
        }
        return List.copyOf(references);
    }

    private static long candidates(ConfigSnapshot snapshot, String removedId, FloorDefinition floor,
                                   EncounterCapability capability) {
        return snapshot.rooms().values().stream().filter(room -> !room.id().equals(removedId))
                .filter(room -> room.type() == RoomType.NORMAL && room.capabilities().contains(capability))
                .filter(room -> room.minFloor() <= floor.number()
                        && (room.maxFloor() == null || room.maxFloor() >= floor.number())).count();
    }

    private Path stagingDirectory() {
        return dataDirectory.resolve(".authoring-" + UUID.randomUUID()).normalize();
    }

    private Path createBackup(String id) throws IOException {
        Files.createDirectories(backupDirectory);
        String base = BACKUP_TIME.format(clock.instant()) + "-" + id;
        Path candidate = backupDirectory.resolve(base);
        int suffix = 1;
        while (Files.exists(candidate)) candidate = backupDirectory.resolve(base + "-" + suffix++);
        Files.createDirectory(candidate);
        return candidate;
    }

    private void pruneBackups() throws IOException {
        if (!Files.isDirectory(backupDirectory)) return;
        List<Path> backups;
        try (Stream<Path> stream = Files.list(backupDirectory)) {
            backups = stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed()).toList();
        }
        for (Path expired : backups.stream().skip(backupRetention).toList()) deleteTree(backupDirectory, expired);
    }

    private void restoreRooms(byte[] original, Path staging) throws IOException {
        Path restore = staging.resolve("rooms-restore.yml");
        Files.write(restore, original, StandardOpenOption.CREATE_NEW);
        atomicReplace(restore, roomsFile);
    }

    private static void atomicCreate(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("atomic move is not supported for " + target, exception);
        }
    }

    private static void atomicReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("atomic replace is not supported for " + target, exception);
        }
    }

    private static void cleanupStaging(Path staging) {
        if (!Files.isDirectory(staging)) return;
        try {
            deleteTree(staging.getParent(), staging);
        } catch (IOException ignored) {
            // A failed cleanup is harmless and remains visibly scoped to .authoring-* under the data directory.
        }
    }

    private static void deleteTree(Path allowedParent, Path target) throws IOException {
        Path parent = allowedParent.toAbsolutePath().normalize();
        Path normalized = target.toAbsolutePath().normalize();
        if (!normalized.startsWith(parent) || Objects.equals(normalized, parent)) {
            throw new IOException("refusing to delete unsafe authoring path " + normalized);
        }
        try (Stream<Path> stream = Files.walk(normalized)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static String message(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    public record OperationResult(boolean successful, String detail) {
        public OperationResult { Objects.requireNonNull(detail); }
        static OperationResult success(String detail) { return new OperationResult(true, detail); }
        static OperationResult failure(String detail) { return new OperationResult(false, detail); }
    }

    enum FailureStage { BEFORE_COMMIT }

    @FunctionalInterface
    interface FailureInjector {
        void before(FailureStage stage) throws Exception;
    }
}
