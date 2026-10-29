package me.lidan.dungeonCrawlers.integration;

import me.lidan.cavecrawlers.skills.SkillsManager;
import me.lidan.cavecrawlers.storage.PlayerDataManager;
import org.bukkit.entity.Player;

public final class DungeonClassScaling {
    private DungeonClassScaling() { }

    public static int dungeonLevel(Player player) {
        var skill = SkillsManager.getInstance().getSkillInfo("dungeon");
        var level = skill == null ? null : PlayerDataManager.getInstance().getSkills(player).get(skill);
        return level == null ? 0 : Math.clamp(level.getLevel(), 0, 60);
    }
}
