package me.lidan.dungeonCrawlers.config.registry;

import me.lidan.cavecrawlers.boostedyaml.YamlDocument;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FloorOneMobConfigTest {
    @Test
    void adventurersStartRangedAndSwitchOnlyOnceWithoutHealingResettingTheirPhase() throws Exception {
        var mobs = YamlDocument.create(new File("server-config/mythicmobs/Mobs/dungeoncrawlers_floor1.yml"));
        var skills = YamlDocument.create(new File("server-config/mythicmobs/Skills/dungeoncrawlers_floor1.yml"));
        for (String variant : List.of("Young", "Unstable", "Holy", "Superior")) {
            String id = "DCF1_LostAdventurer" + variant;
            assertTrue(mobs.getStringList(id + ".Equipment").contains("BOW HAND"));
            assertFalse(mobs.getStringList(id + ".AIGoalSelectors").contains("2 meleeattack"));
            assertTrue(mobs.getStringList(id + ".Skills").contains("skill{s=DCF1_AdventurerMelee} @target ~onTimer:10"));
            assertTrue(mobs.getStringList(id + ".Skills").contains("skill{s=DCF1_AdventurerBow} @target ~onTimer:20"));
        }
        assertTrue(skills.getStringList("DCF1_AdventurerBow.Conditions").containsAll(List.of(
                "variableisset{var=caster.dcf1_melee} false", "targetwithin{d=6} false", "hasaura{n=DCF1_EATING} false")));
        assertEquals(2, skills.getInt("DCF1_AdventurerBow.Cooldown"));
        assertTrue(skills.getStringList("DCF1_AdventurerMelee.Conditions").containsAll(List.of(
                "variableisset{var=caster.dcf1_melee} false", "targetwithin{d=6} true", "hasaura{n=DCF1_EATING} false")));
        assertTrue(skills.getStringList("DCF1_AdventurerMelee.Skills").containsAll(List.of(
                "setvariable{var=caster.dcf1_melee;type=BOOLEAN;value=true} @self",
                "equip{item=DIAMOND_SWORD HAND} @self", "runaigoalselector{goal=meleeattack} @self")));
        var apple = skills.getStringList("DCF1_Apple.Skills");
        assertFalse(apple.contains("equip{item=DIAMOND_SWORD HAND} @self"));
        assertTrue(apple.containsAll(List.of("aura{n=DCF1_EATING;d=32} @self",
                "skill{s=DCF1_AdventurerRestoreBow} @self", "skill{s=DCF1_AdventurerRestoreSword} @self")));
        assertEquals(List.of("variableisset{var=caster.dcf1_melee} false"),
                skills.getStringList("DCF1_AdventurerRestoreBow.Conditions"));
        assertEquals(List.of("variableisset{var=caster.dcf1_melee} true"),
                skills.getStringList("DCF1_AdventurerRestoreSword.Conditions"));
    }
}
