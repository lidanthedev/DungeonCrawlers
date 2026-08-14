package me.lidan.dungeonCrawlers.config;

import me.lidan.cavecrawlers.boostedyaml.block.implementation.Section;
import me.lidan.cavecrawlers.boostedyaml.settings.loader.LoaderSettings;
import me.lidan.cavecrawlers.boostedyaml.settings.updater.UpdaterSettings;
import me.lidan.cavecrawlers.utils.BasicDefaultVersioning;
import me.lidan.cavecrawlers.utils.BoostedCustomConfig;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class BoostedConfigFactory {
    public static final int CURRENT_SCHEMA_VERSION = 4;
    public static final String VERSION_ROUTE = "schema-version";
    private static final int PACK_SCHEMA_VERSION = 1;

    private final Map<Path, BoostedCustomConfig> documents = new HashMap<>();

    public synchronized BoostedCustomConfig open(Path path) throws IOException {
        Path key = path.toAbsolutePath().normalize();
        BoostedCustomConfig existing = documents.get(key);
        if (existing != null) {
            existing.reload();
            return existing;
        }
        BoostedCustomConfig created = create(key.toFile());
        documents.put(key, created);
        return created;
    }

    public BoostedCustomConfig open(File file) throws IOException {
        return open(file.toPath());
    }

    public synchronized void release(Path path) {
        documents.remove(path.toAbsolutePath().normalize());
    }

    public BoostedCustomConfig openMainConfig(Path path, Path backupDirectory) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        BoostedCustomConfig config = open(normalized);
        if (!config.contains(VERSION_ROUTE, true)) {
            Path normalizedBackup = backupDirectory.toAbsolutePath().normalize();
            Files.createDirectories(normalizedBackup);
            Path backup = normalizedBackup.resolve("config-schema-v0.yml").normalize();
            if (!backup.startsWith(normalizedBackup)) {
                throw new IOException("main config backup escaped backup directory");
            }
            if (!Files.exists(backup)) Files.copy(normalized, backup, StandardCopyOption.COPY_ATTRIBUTES);
            config.set(VERSION_ROUTE, PACK_SCHEMA_VERSION);
            if (!config.save()) throw new IOException("failed to persist config.yml schema migration");
        }

        int storedVersion = schemaVersion(config);
        if (storedVersion < CURRENT_SCHEMA_VERSION) {
            BasicDefaultVersioning versioning = new BasicDefaultVersioning(VERSION_ROUTE, CURRENT_SCHEMA_VERSION);
            try (InputStream defaults = BoostedConfigFactory.class.getClassLoader().getResourceAsStream("config.yml")) {
                if (defaults == null) throw new IOException("missing bundled config.yml defaults");
                config.update(defaults, updaterSettings(versioning));
            }
            config.set(VERSION_ROUTE, CURRENT_SCHEMA_VERSION);
            if (!config.save()) throw new IOException("failed to persist config.yml schema update");
        }
        return config;
    }

    public static int schemaVersion(Section config) {
        Object value = config.get(VERSION_ROUTE);
        if (value instanceof Number number) {
            try {
                return new BigDecimal(number.toString()).intValueExact();
            } catch (NumberFormatException | ArithmeticException ignored) {
                return -1;
            }
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private BoostedCustomConfig create(File file) throws IOException {
        BasicDefaultVersioning versioning = new BasicDefaultVersioning(VERSION_ROUTE, PACK_SCHEMA_VERSION);
        LoaderSettings loader = LoaderSettings.builder()
                .setCreateFileIfAbsent(false)
                .setAutoUpdate(false)
                .setAllowDuplicateKeys(false)
                .setDetailedErrors(true)
                .build();
        return new BoostedCustomConfig(file, versioning.getVirtualDefaults(), loader, updaterSettings(versioning));
    }

    private static UpdaterSettings updaterSettings(BasicDefaultVersioning versioning) {
        return UpdaterSettings.builder()
                .setVersioning(versioning)
                .setKeepAll(true)
                .setOptionSorting(UpdaterSettings.OptionSorting.NONE)
                .setAutoSave(false)
                .build();
    }

    public Map<String, Object> read(Path path) throws IOException {
        return toPlainValues(open(path));
    }

    public static Map<String, Object> toPlainValues(Section section) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Object key : section.getKeys()) {
            result.put(String.valueOf(key), plainValue(section.get(String.valueOf(key))));
        }
        return result;
    }

    private static Object plainValue(Object value) {
        if (value instanceof Section section) return toPlainValues(section);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), plainValue(item)));
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            list.forEach(item -> result.add(plainValue(item)));
            return result;
        }
        return value;
    }
}
