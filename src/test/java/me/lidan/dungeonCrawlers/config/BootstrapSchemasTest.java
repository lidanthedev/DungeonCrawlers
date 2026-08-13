package me.lidan.dungeonCrawlers.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapSchemasTest {
    @TempDir
    Path tempDir;

    @Test
    void builtInSchemasLoad() throws IOException {
        File classes = copyResource("classes.yml");
        File blessings = copyResource("blessings.yml");

        assertTrue(BootstrapSchemas.validate(classes, blessings).isEmpty());
    }

    @Test
    void invalidVersionIdMaterialStatAndStackingAreReportedTogether() throws IOException {
        Path classes = tempDir.resolve("classes.yml");
        Path blessings = tempDir.resolve("blessings.yml");
        Files.writeString(classes, """
                schema-version: 2
                classes:
                  BAD ID:
                    icon: NOT_A_MATERIAL
                    stat-add: { MANA: 5 }
                """);
        Files.writeString(blessings, """
                schema-version: 1
                blessings:
                  bad:
                    icon: STONE
                    stacking: unknown
                    max-level: 0
                    per-level: { stat-add: {}, stat-multiply: {} }
                """);

        var errors = BootstrapSchemas.validate(classes.toFile(), blessings.toFile());

        assertTrue(errors.size() >= 6, errors.toString());
    }

    private File copyResource(String name) throws IOException {
        Path target = tempDir.resolve(name);
        try (var input = getClass().getClassLoader().getResourceAsStream(name)) {
            if (input == null) {
                throw new IOException("Missing test resource " + name);
            }
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target.toFile();
    }
}

