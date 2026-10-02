package me.lidan.dungeonCrawlers.commands;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.lidan.cavecrawlers.utils.MiniMessageUtils;
import me.lidan.dungeonCrawlers.core.difficulty.Difficulty;
import me.lidan.dungeonCrawlers.core.difficulty.DifficultyRules;
import me.lidan.dungeonCrawlers.core.difficulty.DungeonProgressionService;
import me.lidan.dungeonCrawlers.config.registry.ConfigRegistryService;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class DungeonDifficultyMenuService {
    private static final int[] TIER_SLOTS = {10, 11, 12, 13, 14, 15, 16, 21, 22, 23};
    private static final Material[] TIER_ICONS = {Material.LIME_DYE, Material.YELLOW_DYE, Material.ORANGE_DYE,
            Material.RED_DYE, Material.BLAZE_POWDER, Material.MAGMA_CREAM, Material.WITHER_SKELETON_SKULL,
            Material.ENDER_EYE, Material.CRYING_OBSIDIAN, Material.NETHER_STAR};
    private static final String[] TIER_COLORS = {"green", "yellow", "gold", "red", "dark_red", "red",
            "dark_red", "dark_purple", "light_purple", "dark_purple"};
    private final ConfigRegistryService configs;
    private final DungeonProgressionService progression;
    private final BiConsumer<Player, String[]> start;

    public DungeonDifficultyMenuService(ConfigRegistryService configs, DungeonProgressionService progression,
                                        BiConsumer<Player, String[]> start) {
        this.configs = configs; this.progression = progression; this.start = start;
    }

    public void open(Player player, String floor) {
        Gui gui = Gui.gui().rows(3).title(MiniMessageUtils.miniMessage("<dark_red><bold>Choose difficulty</bold></dark_red>"))
                .disableAllInteractions().create();
        gui.setItem(4, item(Material.MAP, "<gold>Dungeon difficulties</gold>",
                List.of(MiniMessageUtils.miniMessage("<gray>Clear each tier to unlock the next.</gray>"),
                        MiniMessageUtils.miniMessage("<gray>Every party member must have it unlocked.</gray>")),
                event -> event.setCancelled(true)));
        for (Difficulty tier : Difficulty.values()) {
            DifficultyRules rules = configs.snapshot().difficulties().get(tier.id());
            boolean unlocked = progression.isUnlocked(player.getUniqueId(), floor, tier);
            String color = TIER_COLORS[tier.ordinal()];
            gui.setItem(TIER_SLOTS[tier.ordinal()], item(TIER_ICONS[tier.ordinal()],
                    "<" + color + "><bold>" + tier.displayName() + "</bold></" + color + ">"
                            + (unlocked ? "" : " <dark_gray>[Locked]</dark_gray>"),
                    lore(tier, rules, unlocked), event -> {
                        event.setCancelled(true);
                        player.closeInventory();
                        start.accept(player, new String[]{floor, tier.id()});
                    }));
        }
        gui.open(player);
    }

    private static GuiItem item(Material material, String name, List<Component> lore,
                                Consumer<InventoryClickEvent> action) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(MiniMessageUtils.miniMessage(name));
            meta.lore(lore);
        });
        return new GuiItem(stack, action::accept);
    }

    private static List<Component> lore(Difficulty tier, DifficultyRules rules, boolean unlocked) {
        List<String> lines = new ArrayList<>(List.of("", "<gold>Combat</gold>",
                "<gray>Enemy health: <red>x" + number(rules.healthMultiplier()) + "</red></gray>",
                "<gray>Enemy attacks: <red>x" + number(rules.incomingMultiplier()) + "</red></gray>"));
        if (rules.duplicateClassReduction() > 0) {
            lines.add("<gray>Duplicate classes: <red>-" + percent(rules.duplicateClassReduction()) + "% damage</red></gray>");
        }
        if (rules.groupMechanics()) {
            lines.add("<gray>Grouped enemies: <red>20% less damage taken</red></gray>");
            lines.add("<dark_gray>Within 8 blocks of another non-boss enemy.</dark_gray>");
            lines.add("<gray>While alone: <red>additional x" + number(rules.isolationMultiplier()) + " damage</red></gray>");
            lines.add("<dark_gray>No living ally within 12 blocks. Includes solo.</dark_gray>");
        }
        lines.addAll(List.of("", "<gold>Rewards</gold>",
                "<gray>Dungeon XP: <aqua>x" + number(rules.xpMultiplier()) + "</aqua></gray>",
                "<gray>Magic Find: <green>x" + number(rules.magicFindMultiplier()) + "</green></gray>",
                "", "<gold>Special rules</gold>",
                rules.ordinaryRevival() ? "<gray>Ordinary revives: <green>Allowed</green></gray>"
                        : "<gray>Ordinary revives: <red>Disabled</red></gray>"));
        if (!rules.ordinaryRevival()) lines.add("<dark_gray>Runic pet revival still works once per run.</dark_gray>");
        if (rules.runicChance() > 0) lines.add("<gray>Runic enemies: <light_purple>" + percent(rules.runicChance()) + "%</light_purple></gray>");
        if (rules.runicBossChance() > 0) lines.add("<gray>Runic bosses: <light_purple>" + percent(rules.runicBossChance()) + "%</light_purple></gray>");
        lines.add("");
        if (unlocked) lines.add("<yellow>Click to start</yellow>");
        else {
            lines.add("<red>Locked on this floor</red>");
            lines.add("<gray>Clear <white>" + Difficulty.values()[Math.max(0, tier.ordinal() - 1)].displayName()
                    + "</white> to unlock.</gray>");
        }
        return lines.stream().map(MiniMessageUtils::miniMessage).toList();
    }

    private static String number(double value) { return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }
    private static String percent(double fraction) { return java.math.BigDecimal.valueOf(fraction * 100).stripTrailingZeros().toPlainString(); }
}
