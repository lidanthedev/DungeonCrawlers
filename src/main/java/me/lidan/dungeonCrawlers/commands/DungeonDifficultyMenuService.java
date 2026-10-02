package me.lidan.dungeonCrawlers.commands;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import me.lidan.cavecrawlers.utils.MiniMessageUtils;
import me.lidan.dungeonCrawlers.core.difficulty.Difficulty;
import me.lidan.dungeonCrawlers.core.difficulty.DifficultyRules;
import me.lidan.dungeonCrawlers.core.difficulty.DungeonProgressionService;
import me.lidan.dungeonCrawlers.config.registry.ConfigRegistryService;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public final class DungeonDifficultyMenuService {
    private final ConfigRegistryService configs;
    private final DungeonProgressionService progression;
    private final BiConsumer<Player, String[]> start;

    public DungeonDifficultyMenuService(ConfigRegistryService configs, DungeonProgressionService progression,
                                        BiConsumer<Player, String[]> start) {
        this.configs = configs; this.progression = progression; this.start = start;
    }

    public void open(Player player, String floor) {
        Gui gui = Gui.gui().rows(3).title(MiniMessageUtils.miniMessage("<dark_red>Dungeon difficulty</dark_red>"))
                .disableAllInteractions().create();
        for (Difficulty tier : Difficulty.values()) {
            DifficultyRules rules = configs.snapshot().difficulties().get(tier.id());
            boolean unlocked = progression.isUnlocked(player.getUniqueId(), floor, tier);
            List<Component> lore = new ArrayList<>();
            for (String line : List.of("Enemy health: x" + rules.healthMultiplier(),
                    "Duplicate classes: -" + percent(rules.duplicateClassReduction()) + "% damage",
                    "Enemy attacks: x" + rules.incomingMultiplier(), "While alone: additional x" + rules.isolationMultiplier(),
                    "Dungeon XP: x" + rules.xpMultiplier(), "MAGIC_FIND: x" + rules.magicFindMultiplier(),
                    "Ordinary revives: " + (rules.ordinaryRevival() ? "allowed" : "disabled"),
                    "Runic enemies: " + percent(rules.runicChance()) + "%", "Runic bosses: " + percent(rules.runicBossChance()) + "%")) {
                lore.add(MiniMessageUtils.miniMessage("<gray>" + line + "</gray>"));
            }
            if (rules.groupMechanics()) {
                lore.add(MiniMessageUtils.miniMessage("<gray>Enemies near enemies: 20% less damage within 8 blocks</gray>"));
                lore.add(MiniMessageUtils.miniMessage("<gray>Alone: no living ally within 12 blocks, including solo</gray>"));
            }
            lore.add(MiniMessageUtils.miniMessage(unlocked ? "<yellow>Click to start. Every member must unlock this tier.</yellow>"
                    : "<red>Clear " + Difficulty.values()[Math.max(0, tier.ordinal() - 1)].displayName() + " on this floor to unlock.</red>"));
            gui.setItem(10 + tier.ordinal(), ItemBuilder.from(unlocked ? Material.NETHER_STAR : Material.BARRIER)
                    .name(MiniMessageUtils.miniMessage("<red>" + tier.displayName() + "</red>"))
                    .lore(lore).asGuiItem(event -> {
                        event.setCancelled(true);
                        player.closeInventory();
                        start.accept(player, new String[]{floor, tier.id()});
                    }));
        }
        gui.open(player);
    }

    private static String percent(double fraction) { return java.math.BigDecimal.valueOf(fraction * 100).stripTrailingZeros().toPlainString(); }
}
