package me.lidan.dungeonCrawlers.config.registry;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ConfigSnapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.nio.file.LinkOption;
import java.util.regex.Pattern;

public final class ConfigRegistryService {
    public static final int DEFAULT_BACKUP_RETENTION = 10;
    private static final Pattern RELOAD_BACKUP = Pattern.compile("config-\\d{8}-\\d{6}-\\d{3}(?:-\\d+)?");
    private final Path dataDirectory;
    private final ConfigLoader loader;
    private final Clock clock;
    private final int backupRetention;
    private final AtomicReference<ConfigSnapshot> current = new AtomicReference<>();
    private Map<String, byte[]> activeSourceFiles = Map.of();

    public ConfigRegistryService(Path dataDirectory, EncounterRegistry encounters) {
        this(dataDirectory, encounters, DEFAULT_BACKUP_RETENTION);
    }

    public ConfigRegistryService(Path dataDirectory, EncounterRegistry encounters, int backupRetention) {
        this(dataDirectory, new ConfigLoader(encounters), Clock.systemUTC(), backupRetention);
    }

    ConfigRegistryService(Path dataDirectory, ConfigLoader loader, Clock clock) {
        this(dataDirectory, loader, clock, DEFAULT_BACKUP_RETENTION);
    }

    ConfigRegistryService(Path dataDirectory, ConfigLoader loader, Clock clock, int backupRetention) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory).toAbsolutePath().normalize();
        this.loader = Objects.requireNonNull(loader);
        this.clock = Objects.requireNonNull(clock);
        if (backupRetention < 1 || backupRetention > 1_000) {
            throw new IllegalArgumentException("backup retention must be in 1..1000");
        }
        this.backupRetention = backupRetention;
    }

    public ReloadResult initialize() {
        if (current.get() != null) return new ReloadResult(false, current.get(), List.of("already initialized"), List.of());
        return loadAndSwap(false);
    }

    public ReloadResult reload(int activeReservations) {
        if (activeReservations > 0) {
            return new ReloadResult(false, current.get(),
                    List.of("reload refused while " + activeReservations + " reservation(s) are active"), List.of());
        }
        return loadAndSwap(true);
    }

    public ConfigLoadResult validate() {
        return loader.load(dataDirectory);
    }

    public ConfigSnapshot snapshot() {
        ConfigSnapshot snapshot = current.get();
        if (snapshot == null) throw new IllegalStateException("configuration is not initialized");
        return snapshot;
    }

    private synchronized ReloadResult loadAndSwap(boolean backup) {
        ConfigLoadResult candidate = loader.load(dataDirectory);
        if (!candidate.successful()) {
            return new ReloadResult(false, current.get(), candidate.errors(), candidate.warnings());
        }
        Map<String, byte[]> candidateFiles;
        try {
            candidateFiles = captureSourceFiles();
            if (backup) backupActiveFiles();
        } catch (IOException exception) {
            return new ReloadResult(false, current.get(),
                    List.of("configuration backup failed: " + exception.getMessage()), candidate.warnings());
        }
        current.set(candidate.snapshot());
        activeSourceFiles = candidateFiles;
        return new ReloadResult(true, candidate.snapshot(), List.of(), candidate.warnings());
    }

    private void backupActiveFiles() throws IOException {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(java.time.ZoneOffset.UTC)
                .format(clock.instant());
        Path backupRoot = dataDirectory.resolve("backups").normalize();
        Path backup = uniqueBackup(backupRoot, "config-" + timestamp);
        if (!backup.startsWith(dataDirectory)) throw new IOException("backup path leaves data directory");
        for (var source : activeSourceFiles.entrySet()) {
            Path target = backup.resolve(source.getKey()).normalize();
            if (!target.startsWith(backup)) throw new IOException("backup target escaped backup directory");
            Files.createDirectories(target.getParent());
            Files.write(target, source.getValue());
        }
        pruneReloadBackups(backupRoot);
    }

    private static Path uniqueBackup(Path backupRoot, String name) {
        Path candidate = backupRoot.resolve(name).normalize();
        int suffix = 1;
        while (Files.exists(candidate)) candidate = backupRoot.resolve(name + "-" + suffix++).normalize();
        return candidate;
    }

    private void pruneReloadBackups(Path backupRoot) throws IOException {
        if (!Files.isDirectory(backupRoot, LinkOption.NOFOLLOW_LINKS)) return;
        List<Path> backups;
        try (Stream<Path> stream = Files.list(backupRoot)) {
            backups = stream.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> RELOAD_BACKUP.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();
        }
        for (Path expired : backups.stream().skip(backupRetention).toList()) deleteBackup(backupRoot, expired);
    }

    private static void deleteBackup(Path backupRoot, Path target) throws IOException {
        Path normalizedRoot = backupRoot.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedRoot)
                || !Objects.equals(normalizedTarget.getParent(), normalizedRoot)
                || !RELOAD_BACKUP.matcher(normalizedTarget.getFileName().toString()).matches()) {
            throw new IOException("refusing to delete unsafe backup path " + normalizedTarget);
        }
        try (Stream<Path> paths = Files.walk(normalizedTarget)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private Map<String, byte[]> captureSourceFiles() throws IOException {
        List<Path> sources = new ArrayList<>(List.of(dataDirectory.resolve("classes.yml"),
                dataDirectory.resolve("blessings.yml"), dataDirectory.resolve("rooms.yml")));
        if (Files.isDirectory(dataDirectory.resolve("floors"))) {
            try (Stream<Path> stream = Files.list(dataDirectory.resolve("floors"))) {
                sources.addAll(stream.filter(Files::isRegularFile).sorted().toList());
            }
        }
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (Path source : sources.stream().filter(Files::isRegularFile)
                .sorted(Comparator.comparing(Path::toString)).toList()) {
            String relative = dataDirectory.relativize(source).toString().replace('\\', '/');
            result.put(relative, Files.readAllBytes(source));
        }
        return Map.copyOf(result);
    }

    public record ReloadResult(boolean swapped, ConfigSnapshot snapshot, List<String> errors, List<String> warnings) {
        public ReloadResult {
            errors = List.copyOf(errors); warnings = List.copyOf(warnings);
        }
    }
}
