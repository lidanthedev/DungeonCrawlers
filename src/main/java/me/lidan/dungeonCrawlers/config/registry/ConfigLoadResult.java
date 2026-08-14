package me.lidan.dungeonCrawlers.config.registry;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ConfigSnapshot;

import java.util.List;

public record ConfigLoadResult(ConfigSnapshot snapshot, List<String> errors, List<String> warnings) {
    public ConfigLoadResult {
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
    }

    public boolean successful() { return snapshot != null && errors.isEmpty(); }
}
