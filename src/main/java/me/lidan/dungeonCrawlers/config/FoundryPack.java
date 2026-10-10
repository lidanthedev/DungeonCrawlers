package me.lidan.dungeonCrawlers.config;

import me.lidan.dungeonCrawlers.core.encounter.ChainboundEncounter.Settings;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Ships actual authored rooms on first install; existing administrator edits always win. */
public final class FoundryPack {
    public static final List<String> ROOMS = List.of("foundry_start", "foundry_resonance", "foundry_counterweight",
            "foundry_splitforge", "foundry_archives", "foundry_turbine", "foundry_catwalk", "foundry_warden",
            "foundry_portal", "foundry_heart");
    private FoundryPack() { }

    public static void installTemplates(JavaPlugin plugin) throws IOException {
        Path templates = plugin.getDataFolder().toPath().resolve("templates");
        Files.createDirectories(templates);
        for (String id : ROOMS) {
            Path target = templates.resolve(id + ".schem");
            if (Files.exists(target)) continue;
            try (var input = plugin.getResource("foundry/templates/" + id + ".schem")) {
                if (input == null) throw new IOException("missing bundled foundry template " + id);
                Files.copy(input, target);
            }
        }
        var mythic = plugin.getServer().getPluginManager().getPlugin("MythicMobs");
        if (mythic != null && mythic.isEnabled()) {
            for (String folder : List.of("Mobs", "Skills")) {
                Path target = mythic.getDataFolder().toPath().resolve(folder).resolve("dungeoncrawlers_foundry.yml");
                if (Files.exists(target)) continue;
                Files.createDirectories(target.getParent());
                try (var input = plugin.getResource("foundry/mythic/" + folder + ".yml")) {
                    if (input == null) throw new IOException("missing bundled Foundry MythicMobs " + folder);
                    Files.copy(input, target);
                }
                plugin.getLogger().info("Installed Foundry MythicMobs " + folder + "; run mm reload to activate the new definitions");
            }
        }
    }

    public static Settings load(BoostedConfigFactory factory, Path file) throws IOException {
        try {
            Map<String, Object> root = factory.read(file);
            if (!(root.get("schema-version") instanceof Number version) || version.doubleValue() != 1)
                throw new IllegalArgumentException("foundry.yml schema-version must be 1");
            if (!(root.get("boss") instanceof Map<?, ?> boss)) throw new IllegalArgumentException("foundry.yml boss must be a map");
            return new Settings(number(boss, "health"), number(boss, "damage"), number(boss, "transform-threshold"),
                    number(boss, "final-threshold"), millis(boss, "intro-millis"), millis(boss, "transform-millis"),
                    millis(boss, "warning-millis"), millis(boss, "normal-interval-millis"),
                    millis(boss, "riven-interval-millis"), millis(boss, "final-interval-millis"), millis(boss, "death-millis"));
        } catch (IllegalArgumentException exception) { throw new IOException(exception.getMessage(), exception); }
        finally { factory.release(file); }
    }
    private static double number(Map<?, ?> values, String key) {
        if (!(values.get(key) instanceof Number value) || !Double.isFinite(value.doubleValue()))
            throw new IllegalArgumentException("foundry.yml boss." + key + " must be finite");
        return value.doubleValue();
    }
    private static long millis(Map<?, ?> values, String key) {
        double value = number(values, key);
        if (value != Math.rint(value) || value > Long.MAX_VALUE || value < 0)
            throw new IllegalArgumentException("foundry.yml boss." + key + " must be a positive integer");
        return (long) value;
    }
}
