package me.lidan.dungeonCrawlers.integration.addon;

import me.lidan.caveCrawlAddon.pets.ActivePet;
import me.lidan.caveCrawlAddon.pets.BasePet;
import me.lidan.caveCrawlAddon.pets.PetsManager;
import me.lidan.cavecrawlers.stats.Stats;
import me.lidan.cavecrawlers.stats.Stat;
import me.lidan.cavecrawlers.stats.StatType;
import me.lidan.cavecrawlers.utils.StringUtils;
import me.lidan.cavecrawlers.utils.MiniMessageUtils;
import net.kyori.adventure.text.Component;
import java.util.List;
import java.util.UUID;

/** Loaded only when CaveCrawlAddon is enabled. Native pet stats apply globally. */
public final class RunicPetAdapter implements AutoCloseable {
    public static final String ID = "RUNIC_PET";
    private final BasePet definition = new BasePet("Runic", new Stats(List.of(new Stat(StatType.HEALTH, 1),
            new Stat(StatType.DEFENSE, .5), new Stat(StatType.MAGIC_FIND, .1))), 100, "dungeon") {
        @Override public List<Component> petAbilitiesToLore(ActivePet pet) {
            return List.of(
                    Component.empty(),
                    MiniMessageUtils.miniMessage("<gold>Pet Ability: Runic Power"),
                    MiniMessageUtils.miniMessage("<gray>Gain <green>+" + StringUtils.getNumberFormat(pet.getLevel() * .5)
                            + "% <red>❤ Health<gray>"),
                    MiniMessageUtils.miniMessage("<gray>while in dungeons."),
                    Component.empty(),
                    MiniMessageUtils.miniMessage("<gold>Pet Ability: Second Chance"),
                    MiniMessageUtils.miniMessage("<gray>Revive after <green>5 seconds<gray>, once per run."),
                    MiniMessageUtils.miniMessage("<gray>Gain <green>+2 Bonus score<gray> while active."),
                    MiniMessageUtils.miniMessage("<dark_gray>Works on every dungeon difficulty."));
        }
    };
    public RunicPetAdapter() { PetsManager.getInstance().registerPet(ID, definition); }
    public int activeLevel(UUID player) {
        ActivePet pet = PetsManager.getInstance().getActivePet(player);
        return pet != null && ID.equals(pet.getPetId()) ? Math.clamp(pet.getLevel(), 1, 100) : 0;
    }
    @Override public void close() { PetsManager.getInstance().unregisterPet(ID, definition); }
}
