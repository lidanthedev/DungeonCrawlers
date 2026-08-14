package me.lidan.dungeonCrawlers.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class BootstrapSchemas {
    private static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,63}$");
    private static final Set<String> STATS = Set.of(
            "HEALTH", "DEFENSE", "STRENGTH", "INTELLIGENCE",
            "CRIT_CHANCE", "CRIT_DAMAGE", "SPEED", "ATTACK_SPEED"
    );

    private BootstrapSchemas() {}

    public static List<String> validate(File classesFile, File blessingsFile) {
        List<String> errors = new ArrayList<>();
        validateRoot(classesFile, "classes", errors, false);
        validateRoot(blessingsFile, "blessings", errors, true);
        return List.copyOf(errors);
    }

    private static void validateRoot(File file, String rootName, List<String> errors, boolean blessings) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<?, ?> raw = loadRaw(file, errors);
        Map<?, ?> rawRoot = raw.get(rootName) instanceof Map<?, ?> map ? map : Map.of();
        if (yaml.getInt("schema-version", -1) != 1) {
            errors.add(file.getName() + ": schema-version must be 1");
        }
        ConfigurationSection root = yaml.getConfigurationSection(rootName);
        if (root == null || root.getKeys(false).isEmpty()) {
            errors.add(file.getName() + ": " + rootName + " must not be empty");
            return;
        }
        for (String id : root.getKeys(false)) {
            if (!ID.matcher(id).matches()) {
                errors.add(file.getName() + ": invalid id " + id);
            }
            ConfigurationSection entry = root.getConfigurationSection(id);
            Map<?, ?> rawEntry = rawRoot.get(id) instanceof Map<?, ?> map ? map : Map.of();
            if (entry == null) {
                errors.add(file.getName() + ": " + id + " must be a section");
                continue;
            }
            if (Material.matchMaterial(entry.getString("icon", "")) == null) {
                errors.add(file.getName() + ": " + id + " has invalid icon");
            }
            if (blessings) {
                String stacking = entry.getString("stacking", "");
                if (!stacking.equals("levels") && !stacking.equals("replace")) {
                    errors.add(file.getName() + ": " + id + " stacking must be levels or replace");
                }
                if (entry.getInt("max-level", 0) < 1) {
                    errors.add(file.getName() + ": " + id + " max-level must be positive");
                }
                ConfigurationSection perLevel = declaredSection(entry, rawEntry, "per-level", file, id, errors);
                if (perLevel != null) {
                    Map<?, ?> rawPerLevel = rawEntry.get("per-level") instanceof Map<?, ?> map ? map : Map.of();
                    validateStats(perLevel, rawPerLevel, "stat-add", file, id, errors);
                    validateStats(perLevel, rawPerLevel, "stat-multiply", file, id, errors);
                }
            } else {
                validateStats(entry, rawEntry, "stat-add", file, id, errors);
                validateStats(entry, rawEntry, "stat-multiply", file, id, errors);
            }
        }
    }

    private static Map<?, ?> loadRaw(File file, List<String> errors) {
        try (var input = Files.newInputStream(file.toPath())) {
            Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
            return loaded instanceof Map<?, ?> map ? map : Map.of();
        } catch (IOException | RuntimeException exception) {
            errors.add(file.getName() + ": cannot inspect declared map fields: " + exception.getMessage());
            return Map.of();
        }
    }

    private static ConfigurationSection declaredSection(ConfigurationSection parent, Map<?, ?> rawParent,
                                                         String key, File file, String id, List<String> errors) {
        if (!rawParent.containsKey(key)) {
            return null;
        }
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (!(rawParent.get(key) instanceof Map<?, ?>) || section == null) {
            errors.add(file.getName() + ": " + id + "." + key + " must be a map");
            return null;
        }
        return section;
    }

    private static void validateStats(ConfigurationSection parent, Map<?, ?> rawParent, String key, File file,
                                      String id, List<String> errors) {
        ConfigurationSection stats = declaredSection(parent, rawParent, key, file, id, errors);
        if (stats == null) {
            return;
        }
        for (String stat : stats.getKeys(false)) {
            if (!STATS.contains(stat)) {
                errors.add(file.getName() + ": " + id + " has unsupported stat " + stat);
            }
            double value = stats.getDouble(stat, Double.NaN);
            if (!Double.isFinite(value)) {
                errors.add(file.getName() + ": " + id + "." + stat + " must be finite");
            }
        }
    }
}
