package me.lidan.dungeonCrawlers.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
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
                validateStats(entry.getConfigurationSection("per-level.stat-add"), file, id, errors);
                validateStats(entry.getConfigurationSection("per-level.stat-multiply"), file, id, errors);
            } else {
                validateStats(entry.getConfigurationSection("stat-add"), file, id, errors);
                validateStats(entry.getConfigurationSection("stat-multiply"), file, id, errors);
            }
        }
    }

    private static void validateStats(ConfigurationSection stats, File file, String id, List<String> errors) {
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

