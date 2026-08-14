package me.lidan.dungeonCrawlers.config.registry;

import me.lidan.cavecrawlers.boostedyaml.YamlDocument;
import me.lidan.cavecrawlers.boostedyaml.settings.loader.LoaderSettings;
import me.lidan.dungeonCrawlers.config.BoostedConfigFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

final class TestBoostedConfigFactory extends BoostedConfigFactory {
    @Override
    public Map<String, Object> read(Path path) throws IOException {
        LoaderSettings loader = LoaderSettings.builder()
                .setCreateFileIfAbsent(false)
                .setAutoUpdate(false)
                .setAllowDuplicateKeys(false)
                .setDetailedErrors(true)
                .build();
        try (InputStream input = Files.newInputStream(path)) {
            return toPlainValues(YamlDocument.create(input, loader));
        }
    }
}
