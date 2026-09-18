package me.lidan.dungeonCrawlers.core.template;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Canonical room-authoring markers shared by scanning, validation, and setup tooling. */
public enum RoomMarker {
    ENTRANCE("entrance", "minecraft:jigsaw", "<aqua><bold>Dungeon Entrance</bold>",
            "Room entrance connector. Rotate the Jigsaw to choose its direction; used by NORMAL, MINIBOSS, and PORTAL rooms."),
    EXIT("exit", "minecraft:jigsaw", "<aqua><bold>Dungeon Exit</bold>",
            "Room exit connector for NORMAL rooms; START rooms use it for the coal door."),
    NORMAL_MOB("mob_spawn", "minecraft:gray_concrete_powder", "<gray><bold>Normal Mob Spawn</bold>",
            "Spawns mobs from the floor's normal MythicMobs pool."),
    MINIBOSS_MOB("miniboss_spawn", "minecraft:yellow_concrete_powder", "<yellow><bold>Miniboss Spawn</bold>",
            "Spawns mobs from the floor's miniboss MythicMobs pool."),
    PLAYER_SPAWN("player_spawn", "minecraft:emerald_block", "<green><bold>Player Spawn</bold>",
            "START party spawn or BOSS-room party teleport destination."),
    CLASS_SELECTOR_NPC("class_selector", "minecraft:orange_concrete_powder",
            "<gold><bold>Class Selector NPC</bold>",
            "Optional START-room marker; spawns a Citizens NPC for the dungeon class menu. Only valid in START rooms."),
    BOSS_SPAWN("boss_spawn", "minecraft:red_concrete_powder", "<red><bold>Boss Spawn</bold>",
            "BOSS-room encounter spawn point."),
    REWARD_CHEST("reward_chest", "minecraft:lime_concrete_powder", "<green><bold>Reward Chest</bold>",
            "BOSS-room reward location; becomes the reward Ender Chest."),
    BLESSING_CHEST("blessing_chest", "minecraft:chest", "<light_purple><bold>Blessing Chest</bold>",
            "Secret chest that grants a blessing."),
    STANDARD_CHEST("secret_chest", "minecraft:trapped_chest", "<dark_gray><bold>Secret Chest</bold>",
            "Standard secret chest."),
    PORTAL("portal", "minecraft:nether_portal", "<dark_purple><bold>Portal</bold>",
            "Light a connected obsidian frame; portal blocks trigger the boss room.");

    private final String id;
    private final String blockType;
    private final String displayName;
    private final String description;

    RoomMarker(String id, String blockType, String displayName, String description) {
        this.id = Objects.requireNonNull(id);
        this.blockType = Objects.requireNonNull(blockType);
        this.displayName = Objects.requireNonNull(displayName);
        this.description = Objects.requireNonNull(description);
    }

    public String id() { return id; }
    public String blockType() { return blockType; }
    public String displayName() { return displayName; }
    public String description() { return description; }

    public boolean matches(String type) {
        return blockType.equals(canonicalType(type));
    }

    public static boolean isAuthoringMarker(String type) {
        String canonical = canonicalType(type);
        return Arrays.stream(values()).anyMatch(marker -> marker.blockType.equals(canonical));
    }

    public static Optional<RoomMarker> byId(String id) {
        if (id == null) return Optional.empty();
        String normalized = id.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(marker -> marker.id.equals(normalized)).findFirst();
    }

    public static List<RoomMarker> ordered() { return List.of(values()); }

    private static String canonicalType(String type) {
        Objects.requireNonNull(type, "type");
        String normalized = type.toLowerCase(Locale.ROOT);
        return normalized.contains(":") ? normalized : "minecraft:" + normalized;
    }
}
