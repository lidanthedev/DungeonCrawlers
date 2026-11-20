package me.lidan.dungeonCrawlers.config.registry;

import io.lumine.mythic.bukkit.utils.numbers.RangedDouble;
import me.lidan.cavecrawlers.boostedyaml.YamlDocument;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FloorOneMobConfigTest {
    @Test
    void minibossesRepeatTheirOpeningVolleyAtRangeWithoutInterruptingHeals() throws Exception {
        var mobs = YamlDocument.create(new File("server-config/mythicmobs/Mobs/dungeoncrawlers_floor1.yml"));
        var skills = YamlDocument.create(new File("server-config/mythicmobs/Skills/dungeoncrawlers_floor1.yml"));
        for (String id : List.of("DCF1_LostAdventurerYoung", "DCF1_LostAdventurerUnstable",
                "DCF1_LostAdventurerHoly", "DCF1_LostAdventurerSuperior", "DCF1_AngryArchaeologist")) {
            assertTrue(mobs.getStringList(id + ".Equipment").contains("BOW HAND"), id);
            assertFalse(mobs.getStringList(id + ".AIGoalSelectors").stream()
                    .anyMatch(goal -> goal.contains("meleeattack")), id);
            assertTrue(mobs.getStringList(id + ".AIGoalSelectors").contains("2 movetowardstarget"), id);
            assertTrue(mobs.getStringList(id + ".Skills")
                    .contains("skill{s=DCF1_MinibossVolley} @target ~onTimer:20"), id);
        }
        String idle = "hasaura{n=DCF1_BUSY} false";
        assertEquals(5, skills.getInt("DCF1_MinibossVolley.Cooldown"));
        assertTrue(skills.getStringList("DCF1_MinibossVolley.Conditions").contains(idle));
        assertTrue(skills.getStringList("DCF1_MinibossVolley.TargetConditions").containsAll(List.of(
                "distance{d=0to28} true",
                "(variableequals{var=caster.dcf1_melee;value=true} false || distance{d=>8} true) true",
                "lineofsight true")));
        var repeatRange = new RangedDouble(">8");
        var bowRange = new RangedDouble("0to28");
        assertFalse(repeatRange.equalsSquared(8.0 * 8.0));
        assertTrue(repeatRange.equalsSquared(8.01 * 8.01));
        assertTrue(bowRange.equalsSquared(28.0 * 28.0));
        assertFalse(bowRange.equalsSquared(28.01 * 28.01));
        var volley = skills.getStringList("DCF1_MinibossVolley.Skills");
        assertEquals(List.of("aura{n=DCF1_BUSY;d=22} @self", "equip{item=BOW HAND} @self"), volley.subList(0, 2));
        String arrow = "skill{s=DCF1_MinibossArrow}";
        int firstShot = volley.indexOf(arrow);
        assertEquals(List.of(arrow, "delay 10", arrow, "delay 10", arrow,
                "setvariable{var=caster.dcf1_melee;type=BOOLEAN;value=true} @self",
                "equip{item=DIAMOND_SWORD HAND} @self"), volley.subList(firstShot, firstShot + 7));
        assertTrue(skills.getStringList("DCF1_MinibossArrow.Skills").stream()
                .anyMatch(skill -> skill.startsWith("shoot{type=ARROW;")));
        assertTrue(volley.subList(firstShot + 7, volley.size()).containsAll(List.of(
                "runaigoalselector{goal=meleeattack} @self", "potion{type=SPEED;duration=80;level=1} @self")));

        for (String heal : List.of("DCF1_Apple", "DCF1_HealingPotion")) {
            assertTrue(skills.getStringList(heal + ".Conditions").containsAll(List.of(
                    idle, "variableisset{var=caster.dcf1_melee} true")), heal);
            var animation = skills.getStringList(heal + ".Skills");
            assertEquals("aura{n=DCF1_BUSY;d=32} @self", animation.getFirst(), heal);
            int resistance = animation.indexOf("potion{type=DAMAGE_RESISTANCE;duration=30;level=3} @self");
            assertTrue(resistance >= 0 && resistance < animation.indexOf("delay 30"), heal);
            assertEquals("equip{item=DIAMOND_SWORD HAND} @self", animation.getLast(), heal);
        }
    }
}
