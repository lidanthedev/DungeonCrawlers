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

    public static double killHealing(int level) {
        return 2 + Math.clamp(level, 0, 60) / 20D;
    }

    public static double regenerationBonus(int level) {
        return .25 + Math.clamp(level, 0, 60) / 80D;
    }

    public static double bowAttackSpeed(int level) {
        return 15 + Math.clamp(level, 0, 60) / 2D;
    }

    public static double tankRadius(int level) {
        return 8 + Math.clamp(level, 0, 60) / 15D;
    }

    public static double abilityPower(int level) {
        return 1 + Math.clamp(level, 0, 60) / 200D;
    }

    public static double tankReduction(int level) {
        return .5 + Math.clamp(level, 0, 60) / 600D;
    }

    public static double lifesurgeRegeneration(int level) {
        return 2 + Math.clamp(level, 0, 60) / 20D;
    }
}
