package me.lidan.dungeonCrawlers.commands;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.BaseGui;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.lidan.cavecrawlers.stats.StatType;
import me.lidan.cavecrawlers.utils.MiniMessageUtils;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.ClassDefinition;
import me.lidan.dungeonCrawlers.config.registry.ConfigModels.StatModifiers;
import me.lidan.dungeonCrawlers.config.registry.ConfigRegistryService;
import me.lidan.dungeonCrawlers.core.door.DoorService;
import me.lidan.dungeonCrawlers.core.run.RunPreparationService;
import me.lidan.dungeonCrawlers.integration.DungeonActionBar;
import me.lidan.dungeonCrawlers.integration.BukkitClassAbilityService;
import me.lidan.dungeonCrawlers.integration.DungeonClassScaling;
import me.lidan.dungeonCrawlers.integration.DungeonMessages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Opens the class-selection GUI and funnels every selection through RunPreparationService. */
public final class DungeonClassMenuService implements Listener {
    private static final int CLOSE_SLOT = 49;
    private static final int[] CLASS_SLOTS = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33};

    private final ConfigRegistryService configRegistry;
    private final RunPreparationService runs;
    private final Consumer<DoorService.DoorSnapshot> doorRenderer;
    private final DungeonActionBar actionBar;
    private final Map<UUID, UUID> menuInstances = new LinkedHashMap<>();
    private final Map<UUID, BaseGui> openMenus = new LinkedHashMap<>();

    public DungeonClassMenuService(ConfigRegistryService configRegistry, RunPreparationService runs,
                                   Consumer<DoorService.DoorSnapshot> doorRenderer,
                                   DungeonActionBar actionBar) {
        this.configRegistry = Objects.requireNonNull(configRegistry, "configRegistry");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.doorRenderer = Objects.requireNonNull(doorRenderer, "doorRenderer");
        this.actionBar = Objects.requireNonNull(actionBar, "actionBar");
    }

    public void open(Player player) {
        Objects.requireNonNull(player, "player");
        UUID instanceId = runs.instanceFor(player.getUniqueId()).orElse(null);
        if (instanceId == null) {
            DungeonMessages.send(player, DungeonMessages.error("You are not preparing a dungeon."));
            return;
        }
        open(player, instanceId);
    }

    /** Opens only when the selector's explicit instance still owns the player. */
    public void open(Player player, UUID expectedInstanceId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(expectedInstanceId, "expectedInstanceId");
        UUID actualInstanceId = runs.instanceFor(player.getUniqueId()).orElse(null);
        if (!expectedInstanceId.equals(actualInstanceId)) {
            DungeonMessages.send(player, DungeonMessages.error(
                    "That class selector does not belong to your dungeon."));
            return;
        }
        var snapshot = runs.info(expectedInstanceId).orElse(null);
        if (snapshot == null) {
            DungeonMessages.send(player, DungeonMessages.error("Your dungeon preparation is no longer active."));
            close(player);
            return;
        }
        String blocked = openBlockReason(snapshot);
        if (blocked != null) {
            DungeonMessages.send(player, DungeonMessages.warning(blocked));
            if (!RunPreparationService.classSelectionOpen(snapshot.state())) close(player);
            return;
        }
        if (isOpen(player) && expectedInstanceId.equals(menuInstances.get(player.getUniqueId()))) return;
        show(player, snapshot);
    }

    public boolean isOpen(Player player) {
        return player != null && openMenus.containsKey(player.getUniqueId());
    }

    public void close(Player player) {
        if (player == null) return;
        BaseGui gui = openMenus.remove(player.getUniqueId());
        menuInstances.remove(player.getUniqueId());
        if (gui != null && player.getOpenInventory().getTopInventory().getHolder() == gui) {
            player.closeInventory();
        }
    }

    public void closeAll() {
        for (UUID playerId : List.copyOf(openMenus.keySet())) {
            BaseGui gui = openMenus.remove(playerId);
            menuInstances.remove(playerId);
            if (gui == null) continue;
            Player player = org.bukkit.Bukkit.getPlayer(playerId);
            if (player != null) {
                if (player.getOpenInventory().getTopInventory().getHolder() == gui) player.closeInventory();
            }
        }
    }

    public void closeInstance(UUID instanceId) {
        if (instanceId == null) return;
        for (Map.Entry<UUID, UUID> entry : List.copyOf(menuInstances.entrySet())) {
            if (!instanceId.equals(entry.getValue())) continue;
            Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
            if (player != null) close(player);
            else {
                openMenus.remove(entry.getKey());
                menuInstances.remove(entry.getKey());
            }
        }
    }

    private void show(Player player, RunPreparationService.RunSnapshot snapshot) {
        BaseGui existing = openMenus.get(player.getUniqueId());
        if (existing instanceof Gui gui
                && snapshot.instanceId().equals(menuInstances.get(player.getUniqueId()))
                && player.getOpenInventory().getTopInventory().getHolder() == gui) {
            renderClassItems(gui, player, snapshot, true);
            return;
        }

        Gui gui = Gui.gui().rows(6)
                .title(MiniMessageUtils.miniMessage("<dark_aqua><bold>Choose your dungeon class</bold></dark_aqua>"))
                .disableAllInteractions().create();
        gui.getFiller().fill(fillerItem());
        renderClassItems(gui, player, snapshot, false);
        gui.setItem(CLOSE_SLOT, ItemBuilder.from(Material.BARRIER)
                .name(MiniMessageUtils.miniMessage("<red>Close</red>"))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    close(player);
                }));

        BaseGui previous = openMenus.put(player.getUniqueId(), gui);
        menuInstances.put(player.getUniqueId(), snapshot.instanceId());
        if (previous != null && player.getOpenInventory().getTopInventory().getHolder() == previous) {
            player.closeInventory();
        }
        gui.open(player);
    }

    private void renderClassItems(Gui gui, Player player, RunPreparationService.RunSnapshot snapshot,
                                  boolean updating) {
        Map<String, ClassDefinition> classes = configRegistry.snapshot().classes();
        List<String> available = snapshot.allowedClasses();
        int dungeonLevel = DungeonClassScaling.dungeonLevel(player);
        for (int index = 0; index < CLASS_SLOTS.length; index++) {
            GuiItem item;
            if (index < available.size()) {
                String classId = available.get(index);
                ClassDefinition definition = classes.get(classId);
                item = classItem(classId, definition, dungeonLevel,
                        classId.equals(snapshot.selectedClasses().get(player.getUniqueId())),
                        event -> select(player, snapshot.instanceId(), classId));
            } else {
                item = fillerItem();
            }
            if (updating) gui.updateItem(CLASS_SLOTS[index], item);
            else gui.setItem(CLASS_SLOTS[index], item);
        }
        if (available.size() > CLASS_SLOTS.length) {
            DungeonMessages.send(player, DungeonMessages.warning(
                    "This dungeon has more classes than the class menu can display."));
        }
    }

    private static GuiItem fillerItem() {
        return ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                .name(MiniMessageUtils.miniMessage("<dark_gray> </dark_gray>"))
                .asGuiItem(event -> event.setCancelled(true));
    }

    private dev.triumphteam.gui.guis.GuiItem classItem(
            String classId, ClassDefinition definition, int dungeonLevel, boolean selected,
            Consumer<org.bukkit.event.inventory.InventoryClickEvent> action) {
        if (definition == null) {
            return ItemBuilder.from(Material.BARRIER)
                    .name(MiniMessageUtils.miniMessage("<red>Class unavailable</red>"))
                    .lore(List.of(MiniMessageUtils.miniMessage(
                            "<gray>This class is temporarily unavailable.</gray>"), MiniMessageUtils.miniMessage(
                            "<yellow>Please try again later.</yellow>")))
                    .asGuiItem(event -> event.setCancelled(true));
        }
        return ItemBuilder.from(definition.icon())
                .name(MiniMessageUtils.miniMessage(definition.displayName()))
                .lore(classLore(definition, dungeonLevel, selected))
                .asGuiItem(event -> {
                    event.setCancelled(true);
                    action.accept(event);
                });
    }

    private static List<net.kyori.adventure.text.Component> classLore(ClassDefinition definition,
                                                                   int dungeonLevel, boolean selected) {
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(MiniMessageUtils.miniMessage("<gray>Requirement: <green>Allowed on this floor</green></gray>"));
        lore.add(MiniMessageUtils.miniMessage("<gray>Configured bonuses:</gray>"));
        appendStats(lore, definition.stats());
        lore.add(MiniMessageUtils.miniMessage("<gray>Dungeon skill level: <white>" + dungeonLevel + "</white></gray>"));
        definition.statPercentPerLevel().entrySet().stream().sorted((left, right) ->
                        statName(left.getKey()).compareTo(statName(right.getKey())))
                .forEach(entry -> lore.add(MiniMessageUtils.miniMessage("<green>+"
                        + amount(entry.getValue() * dungeonLevel) + "% " + statName(entry.getKey())
                        + " <gray>+" + amount(entry.getValue()) + "% per level</gray></green>")));
        if (definition.healingPercentPerLevel() > 0) {
            lore.add(MiniMessageUtils.miniMessage("<green>+"
                    + amount(definition.healingPercentPerLevel() * dungeonLevel) + "% healing <gray>+"
                    + amount(definition.healingPercentPerLevel()) + "% per level</gray></green>"));
        }
        String abilityName = BukkitClassAbilityService.abilityName(definition.id());
        if (!abilityName.isEmpty()) {
            lore.add(MiniMessageUtils.miniMessage("<gold>" + abilityName + " <yellow>DROP</yellow></gold>"));
            lore.add(MiniMessageUtils.miniMessage("<gray>Cooldown: "
                    + BukkitClassAbilityService.cooldownSeconds(definition.id()) + "s</gray>"));
            String description = BukkitClassAbilityService.abilityDescription(definition.id(), dungeonLevel);
            for (String line : description.split("; ")) {
                lore.add(MiniMessageUtils.miniMessage("<gray>" + line + "</gray>"));
            }
        }
        lore.add(MiniMessageUtils.miniMessage(selected
                ? "<green><bold>Currently selected</bold></green>"
                : "<yellow>Click to select</yellow>"));
        lore.add(MiniMessageUtils.miniMessage("<dark_gray>You can change this until the start door opens.</dark_gray>"));
        return List.copyOf(lore);
    }

    private static void appendStats(List<net.kyori.adventure.text.Component> lore, StatModifiers stats) {
        if (stats.add().isEmpty() && stats.multiply().isEmpty()) {
            lore.add(MiniMessageUtils.miniMessage("<dark_gray>None</dark_gray>"));
            return;
        }
        stats.add().entrySet().stream().sorted((left, right) ->
                        statName(left.getKey()).compareTo(statName(right.getKey())))
                .forEach(entry -> lore.add(MiniMessageUtils.miniMessage("<gray>+" + amount(entry.getValue())
                        + " " + statName(entry.getKey()) + "</gray>")));
        stats.multiply().entrySet().stream().sorted((left, right) ->
                        statName(left.getKey()).compareTo(statName(right.getKey())))
                .forEach(entry -> lore.add(MiniMessageUtils.miniMessage("<gray>×" + amount(entry.getValue())
                        + " " + statName(entry.getKey()) + "</gray>")));
    }

    private void select(Player player, UUID instanceId, String classId) {
        var result = runs.selectClass(instanceId, player.getUniqueId(), classId);
        if (!result.successful()) {
            DungeonMessages.send(player, DungeonMessages.error(classError(result.detail())));
            if (!RunPreparationService.classSelectionOpen(
                    runs.info(instanceId).map(RunPreparationService.RunSnapshot::state).orElse(null))) close(player);
            return;
        }
        doorRenderer.accept(result.door());
        String label = configRegistry.snapshot().classes().get(classId) == null ? classId
                : plain(configRegistry.snapshot().classes().get(classId).displayName());
        boolean ready = result.door().state() == DoorService.DoorState.READY;
        DungeonMessages.send(player, DungeonMessages.success("You chose <white>" + label + "</white>. "
                + (ready ? "The start door is ready." : "Waiting for your party to choose.")));
        actionBar.show(player, MiniMessageUtils.miniMessage(ready
                ? "<green>Open the start door to begin.</green>"
                : "<yellow>Waiting for your party to choose a class.</yellow>"));
        show(player, result.snapshot());
    }

    private static String openBlockReason(RunPreparationService.RunSnapshot snapshot) {
        if (!RunPreparationService.classSelectionOpen(snapshot.state())) {
            return "Class selection is locked once the dungeon starts.";
        }
        if (!snapshot.snapshotsReady()) return "Dungeon preparation is still finishing; try again shortly.";
        if (snapshot.allowedClasses().isEmpty()) return "No classes are configured for this dungeon.";
        return null;
    }

    private static String classError(String detail) {
        String normalized = detail == null ? "" : detail.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("allowed") || normalized.contains("unknown class")) {
            return "That class is not available for this dungeon.";
        }
        if (normalized.contains("snapshot") || normalized.contains("preparation")) {
            return "Dungeon preparation is still in progress. Try again shortly.";
        }
        if (normalized.contains("not in this run")) return "You are not a member of this dungeon.";
        if (normalized.contains("already")) return "Class selection is locked once the dungeon starts.";
        return "Your class could not be selected right now.";
    }

    private static String plain(String displayName) {
        return MiniMessageUtils.componentToString(MiniMessageUtils.miniMessage(displayName));
    }

    private static String amount(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    private static String statName(StatType type) {
        return type.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (openMenus.get(playerId) == event.getInventory().getHolder()) {
            openMenus.remove(playerId);
            menuInstances.remove(playerId);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        close(event.getPlayer());
    }
}
