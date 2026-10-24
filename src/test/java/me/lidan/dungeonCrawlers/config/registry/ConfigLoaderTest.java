package me.lidan.dungeonCrawlers.config.registry;

import me.lidan.dungeonCrawlers.config.registry.ConfigModels.RoomType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {
    @TempDir Path directory;

    @Test
    void builtInPackLoadsWithDefaultsAndStableHash() throws Exception {
        copyDefaults();
        ConfigLoader loader = loader();

        ConfigLoadResult first = loader.load(directory);
        ConfigLoadResult second = loader.load(directory);

        assertTrue(first.successful(), first.errors().toString());
        assertEquals(first.snapshot().hash(), second.snapshot().hash());
        assertEquals(RoomType.START, first.snapshot().rooms().get("dungeon_start").type());
        assertEquals(3000, first.snapshot().floors().get("floor_1").templates().bossOffset().z());
        assertEquals(1, first.snapshot().floors().get("floor_1").rewards().get("wooden").items().get(1).minimumAmount());
        assertEquals(2.0, first.snapshot().classes().get("berserker").statPercentPerLevel()
                .get(me.lidan.cavecrawlers.stats.StatType.STRENGTH));
        assertEquals(2.0, first.snapshot().classes().get("healer").healingPercentPerLevel());
    }

    @Test
    void invalidClassPercentagesAreRejected() throws Exception {
        copyDefaults();
        Path classes = directory.resolve("classes.yml");
        Files.writeString(classes, Files.readString(classes)
                .replace("stat-percent-per-level: { STRENGTH: 2 }", "stat-percent-per-level: { STRENGTH: -2 }")
                .replace("healing-percent-per-level: 2", "healing-percent-per-level: .nan"));

        ConfigLoadResult result = loader().load(directory);

        assertFalse(result.successful());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("stat-percent-per-level")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("healing-percent-per-level")));
    }

    @Test
    void invalidStatMaterialAndMalformedMapsAreRejected() throws Exception {
        copyDefaults();
        Path classes = directory.resolve("classes.yml");
        String content = Files.readString(classes)
                .replace("icon: IRON_SWORD", "icon: NOT_A_MATERIAL")
                .replace("stat-add: { STRENGTH: 50 }", "stat-add: { NOT_A_STAT: 50 }")
                .replaceFirst("stat-multiply: \\{\\}", "stat-multiply: nope");
        Files.writeString(classes, content);

        ConfigLoadResult result = loader().load(directory);

        assertFalse(result.successful());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("invalid material")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("invalid value NOT_A_STAT")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("stat-multiply must be a map")));
    }

    @Test
    void nullStatMapIsRejectedButOmittedMapUsesDefaults() throws Exception {
        copyDefaults();
        Path classes = directory.resolve("classes.yml");
        Files.writeString(classes, Files.readString(classes).replace("stat-add: { STRENGTH: 50 }", "stat-add:"));
        ConfigLoadResult malformed = loader().load(directory);
        assertTrue(malformed.errors().stream().anyMatch(error -> error.contains("stat-add must be a map")));

        copyDefaults();
        Files.writeString(classes, Files.readString(classes).replace("    stat-multiply: {}\n", ""));
        assertTrue(loader().load(directory).successful());
    }

    @Test
    void crossReferencesTypesAndCapabilitiesFailTogether() throws Exception {
        copyDefaults();
        Path floor = directory.resolve("floors/floor_1.yml");
        Files.writeString(floor, Files.readString(floor)
                .replace("allowed: [berserker, mage, tank, healer, archer]", "allowed: [missing_class]")
                .replace("id: crypt_strength", "id: missing_blessing"));
        Path rooms = directory.resolve("rooms.yml");
        Files.writeString(rooms, Files.readString(rooms)
                .replaceAll("dungeon_start:\\R    type: start", "dungeon_start:\n    type: portal"));

        ConfigLoadResult result = loader().load(directory);

        assertTrue(result.errors().stream().anyMatch(error -> error.contains("missing class")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("missing blessing")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("requires START")));
    }

    @Test
    void unknownEncounterWarnsAndUsesRegisteredBasicFallback() throws Exception {
        copyDefaults();
        Path floor = directory.resolve("floors/floor_1.yml");
        Files.writeString(floor, Files.readString(floor).replace("encounter: ringmaster", "encounter: future_boss"));

        ConfigLoadResult result = loader().load(directory);

        assertTrue(result.successful(), result.errors().toString());
        assertEquals("basic", result.snapshot().floors().get("floor_1").encounterId());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("future_boss")));
    }

    @Test
    void contentHashChangesWithValidContent() throws Exception {
        copyDefaults();
        ConfigLoader loader = loader();
        String before = loader.load(directory).snapshot().hash();
        Path floor = directory.resolve("floors/floor_1.yml");
        Files.writeString(floor, Files.readString(floor).replace("Floor I", "First Floor"));
        assertNotEquals(before, loader.load(directory).snapshot().hash());
    }

    @Test
    void longRewardPriceAndTemplateVolumeAreAccepted() throws Exception {
        copyDefaults();
        Path floor = directory.resolve("floors/floor_1.yml");
        Files.writeString(floor, Files.readString(floor)
                .replace("price: 0", "price: 3000000000")
                .replace("max-template-volume: 16777216", "max-template-volume: 3000000000"));

        ConfigLoadResult result = loader().load(directory);

        assertTrue(result.successful(), result.errors().toString());
        assertEquals(3_000_000_000L, result.snapshot().floors().get("floor_1").rewards().get("wooden").price());
        assertEquals(3_000_000_000L, result.snapshot().floors().get("floor_1").limits().maxTemplateVolume());
    }

    @Test
    void rewardItemIdsAcceptCaveItemsUppercaseAndRejectMalformedIds() throws Exception {
        copyDefaults();
        Path floor = directory.resolve("floors/floor_1.yml");

        ConfigLoadResult uppercase = loader().load(directory);

        assertTrue(uppercase.successful(), uppercase.errors().toString());
        assertEquals("UNDEAD_ESSENCE", uppercase.snapshot().floors().get("floor_1")
                .rewards().get("wooden").items().getFirst().itemId());

        Files.writeString(floor, Files.readString(floor).replace("item: UNDEAD_ESSENCE", "item: undead_essence"));
        ConfigLoadResult legacy = loader().load(directory);
        assertTrue(legacy.successful(), legacy.errors().toString());
        assertEquals("UNDEAD_ESSENCE", legacy.snapshot().floors().get("floor_1")
                .rewards().get("wooden").items().getFirst().itemId());

        Files.writeString(floor, Files.readString(floor).replace("item: undead_essence", "item: invalid item"));
        ConfigLoadResult malformed = loader().load(directory);

        assertFalse(malformed.successful());
        assertTrue(malformed.errors().stream().anyMatch(error -> error.contains("invalid id invalid item")),
                malformed.errors().toString());
    }

    @Test
    void duplicateFloorNumbersAreRejected() throws Exception {
        copyDefaults();
        String duplicate = Files.readString(directory.resolve("floors/floor_1.yml"))
                .replace("id: floor_1", "id: floor_2");
        Files.writeString(directory.resolve("floors/floor_2.yml"), duplicate);

        ConfigLoadResult result = loader().load(directory);

        assertFalse(result.successful());
        assertTrue(result.errors().contains("duplicate floor number 1"), result.errors().toString());
    }

    @Test
    void schemaAndBoundedIntegersRequireExactIntValues() throws Exception {
        copyDefaults();
        Path classes = directory.resolve("classes.yml");
        Files.writeString(classes, Files.readString(classes).replace("schema-version: 2", "schema-version: 1.5"));
        Path rooms = directory.resolve("rooms.yml");
        Files.writeString(rooms, Files.readString(rooms).replace("schema-version: 1", "schema-version: 4294967297"));
        ConfigLoadResult invalidSchema = loader().load(directory);
        assertTrue(invalidSchema.errors().stream().anyMatch(error -> error.contains("classes.yml:schema-version must be 2")));
        assertTrue(invalidSchema.errors().stream().anyMatch(error -> error.contains("rooms.yml:schema-version must be 1")));

        copyDefaults();
        Path floor = directory.resolve("floors/floor_1.yml");
        Files.writeString(floor, Files.readString(floor).replace("number: 1", "number: 18446744073709551617"));
        ConfigLoadResult invalidNumber = loader().load(directory);
        assertTrue(invalidNumber.errors().stream().anyMatch(error -> error.contains("number must be an integer")));
    }

    @Test
    void overflowingAmountRangeReturnsValidationError() throws Exception {
        copyDefaults();
        Path floor = directory.resolve("floors/floor_1.yml");
        Files.writeString(floor, Files.readString(floor).replace("amount: \"3-8\"",
                "amount: \"99999999999999999999-999999999999999999999\""));

        ConfigLoadResult result = loader().load(directory);

        assertFalse(result.successful());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("amount must be a positive integer")),
                result.errors().toString());
    }

    @Test
    void blessingLevelRangeLoadsFromConfigAndRejectsOutOfBoundsValues() throws Exception {
        copyDefaults();
        Path blessings = directory.resolve("blessings.yml");
        Files.writeString(blessings, Files.readString(blessings)
                .replace("level-range: \"1-1\"", "level-range: \"2-4\""));

        ConfigLoadResult valid = loader().load(directory);

        assertTrue(valid.successful(), valid.errors().toString());
        assertEquals(2, valid.snapshot().blessings().get("crypt_strength").levelRange().getMin());
        assertEquals(4, valid.snapshot().blessings().get("crypt_strength").levelRange().getMax());

        Files.writeString(blessings, Files.readString(blessings).replace("level-range: \"2-4\"", "level-range: \"0-4\""));
        ConfigLoadResult invalid = loader().load(directory);
        assertTrue(invalid.errors().stream().anyMatch(error -> error.contains("level-range")),
                invalid.errors().toString());
    }


    @Test void difficultyConfigAndCustomFloorMigrationAreValidated() throws Exception {
        copyDefaults();
        Path floor = directory.resolve("floors/floor_1.yml");
        String old = Files.readString(floor).replace("schema-version: 3", "schema-version: 1")
                .replace("number: 1", "number: 3").replaceAll("(?m)^dungeon-xp:\n(?:  .*\n)*", "");
        Files.writeString(floor, old);
        var loaded = loader().load(directory);
        assertTrue(loaded.successful(), loaded.errors().toString());
        assertEquals(900, loaded.snapshot().floors().get("floor_1").completionXp());
        assertEquals(10, loaded.snapshot().difficulties().size());
        Path difficulties = directory.resolve("difficulties.yml");
        Files.writeString(difficulties, Files.readString(difficulties).replace("runic-chance: 0.001", "runic-chance: 1.2"));
        assertFalse(loader().load(directory).successful());
    }

    private void copyDefaults() throws IOException {
        Path resources = Path.of("src/main/resources");
        for (String file : new String[]{"classes.yml", "blessings.yml", "rooms.yml", "difficulties.yml"}) {
            Files.copy(resources.resolve(file), directory.resolve(file), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.createDirectories(directory.resolve("floors"));
        Files.copy(resources.resolve("floors/floor_1.yml"), directory.resolve("floors/floor_1.yml"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static ConfigLoader loader() {
        return new ConfigLoader(new EncounterRegistry(), java.time.Clock.systemUTC(),
                new TestBoostedConfigFactory());
    }
}
