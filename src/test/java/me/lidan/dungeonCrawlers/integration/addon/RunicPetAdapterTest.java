package me.lidan.dungeonCrawlers.integration.addon;

import me.lidan.caveCrawlAddon.CaveCrawlAddon;
import me.lidan.caveCrawlAddon.pets.ActivePet;
import me.lidan.caveCrawlAddon.pets.BasePet;
import me.lidan.caveCrawlAddon.pets.PetsManager;
import me.lidan.cavecrawlers.items.Rarity;
import me.lidan.cavecrawlers.stats.StatType;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RunicPetAdapterTest {
    @BeforeAll static void initializeNativeApiWithoutPluginClassLoader() {
        try (var plugin = mockStatic(CaveCrawlAddon.class)) {
            plugin.when(CaveCrawlAddon::getInstance).thenReturn(mock(CaveCrawlAddon.class));
            PetsManager.getInstance();
            // Both native classes capture their plugin during static initialization.
            mock(ActivePet.class);
        }
    }
    @Test void usesNativeAbilityStyleAndFormattedNumbers() {
        try (var adapter = new RunicPetAdapter()) {
            var pet = mock(ActivePet.class);
            when(pet.getLevel()).thenReturn(100);
            var lore = PetsManager.getInstance().getPet(RunicPetAdapter.ID).petAbilitiesToLore(pet);
            var plain = PlainTextComponentSerializer.plainText();
            assertEquals("Pet Ability: Runic Power", plain.serialize(lore.get(1)));
            assertEquals(NamedTextColor.GOLD, lore.get(1).color());
            assertEquals("Gain +50% ❤ Health", plain.serialize(lore.get(2)));
            assertEquals("while in dungeons.", plain.serialize(lore.get(3)));
            assertEquals("Pet Ability: Second Chance", plain.serialize(lore.get(5)));
            when(pet.getLevel()).thenReturn(1);
            assertEquals("Gain +0.5% ❤ Health", plain.serialize(PetsManager.getInstance()
                    .getPet(RunicPetAdapter.ID).petAbilitiesToLore(pet).get(2)));
        }
    }

    @Test void nativePetStatsScaleWithLevelWithoutDungeonCondition() {
        try (var adapter = new RunicPetAdapter()) {
            var definition = PetsManager.getInstance().getPet(RunicPetAdapter.ID);
            var stats = definition.getStatsAtLevel(100);
            assertEquals(100, stats.get(StatType.HEALTH).getValue());
            assertEquals(50, stats.get(StatType.DEFENSE).getValue());
            assertEquals(10, stats.get(StatType.MAGIC_FIND).getValue());
            var firstLevel = definition.getStatsAtLevel(1);
            assertEquals(1, firstLevel.get(StatType.HEALTH).getValue());
            assertEquals(.5, firstLevel.get(StatType.DEFENSE).getValue());
            assertEquals(.1, firstLevel.get(StatType.MAGIC_FIND).getValue());
        }
    }

    @Test void registrationRefreshesExistingPetsWithoutChangingTheirXp() throws Exception {
        var manager = PetsManager.getInstance();
        String id = "DEFINITION_RELOAD_TEST";
        var original = mock(BasePet.class);
        when(original.getXpToLevelList()).thenReturn(List.of(100D));
        when(original.getMaxLevel()).thenReturn(2);
        when(original.getRarityXpMultiplier(Rarity.LEGENDARY)).thenReturn(1D);
        manager.registerPet(id, original);
        var pet = new ActivePet(UUID.randomUUID(), id, Rarity.LEGENDARY, 25);
        var field = PetsManager.class.getDeclaredField("petsById");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var cache = (Map<UUID, ActivePet>) field.get(manager);
        cache.put(pet.getInstanceId(), pet);
        var replacement = mock(BasePet.class);
        try {
            manager.registerPet(id, replacement);
            assertSame(replacement, pet.getBasePet());
            assertEquals(25, pet.getTotalXp());
            manager.unregisterPet(id, original);
            assertSame(replacement, manager.getPet(id));
        } finally {
            cache.remove(pet.getInstanceId());
            manager.unregisterPet(id, replacement);
        }
    }
}
