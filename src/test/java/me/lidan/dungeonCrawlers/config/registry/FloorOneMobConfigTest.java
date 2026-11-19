package me.lidan.dungeonCrawlers.config.registry;

import me.lidan.cavecrawlers.boostedyaml.YamlDocument;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FloorOneMobConfigTest {
    @Test
    void minibossesFireThreeArrowsBeforeChargingAndHealWithResistance() throws Exception {
        var mobs = YamlDocument.create(new File("server-config/mythicmobs/Mobs/dungeoncrawlers_floor1.yml"));
        var skills = YamlDocument.create(new File("server-config/mythicmobs/Skills/dungeoncrawlers_floor1.yml"));
        for (String id : List.of("DCF1_LostAdventurerYoung", "DCF1_LostAdventurerUnstable",
                "DCF1_LostAdventurerHoly", "DCF1_LostAdventurerSuperior", "DCF1_AngryArchaeologist")) {
            assertTrue(mobs.getStringList(id + ".Equipment").contains("BOW HAND"), id);
            assertFalse(mobs.getStringList(id + ".AIGoalSelectors").stream()
                    .anyMatch(goal -> goal.contains("meleeattack")), id);
            assertTrue(mobs.getStringList(id + ".Skills")
                    .contains("skill{s=DCF1_MinibossOpener} @target ~onTimer:20"), id);
        }
        assertTrue(skills.getStringList("DCF1_MinibossOpener.Conditions")
                .contains("variableisset{var=caster.dcf1_melee} false"));
        var opener = skills.getStringList("DCF1_MinibossOpener.Skills");
        String arrow = "skill{s=DCF1_MinibossArrow}";
        assertEquals(List.of(arrow, "delay 4", arrow, "delay 4", arrow,
                "setvariable{var=caster.dcf1_melee;type=BOOLEAN;value=true} @self",
                "equip{item=DIAMOND_SWORD HAND} @self"), opener.subList(0, 7));
        assertTrue(skills.getStringList("DCF1_MinibossArrow.Skills").stream()
                .anyMatch(skill -> skill.startsWith("shoot{type=ARROW;")));
        assertTrue(opener.contains("runaigoalselector{goal=meleeattack} @self"));
        assertTrue(opener.contains("potion{type=SPEED;duration=80;level=1} @self"));

        for (String heal : List.of("DCF1_Apple", "DCF1_HealingPotion")) {
            assertTrue(skills.getStringList(heal + ".Conditions")
                    .contains("variableisset{var=caster.dcf1_melee} true"), heal);
            var animation = skills.getStringList(heal + ".Skills");
            int resistance = animation.indexOf("potion{type=DAMAGE_RESISTANCE;duration=30;level=3} @self");
            assertTrue(resistance >= 0 && resistance < animation.indexOf("delay 30"), heal);
            assertEquals("equip{item=DIAMOND_SWORD HAND} @self", animation.getLast(), heal);
        }
    }
}
