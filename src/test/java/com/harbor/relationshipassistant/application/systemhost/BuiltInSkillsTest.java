package com.harbor.relationshipassistant.application.systemhost;

import com.harbor.relationshipassistant.application.skill.SkillDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuiltInSkillsTest {

    @Test
    void defaultThreeStrategy_hasNameAndNonEmptyInstructions() {
        SkillDefinition def = BuiltInSkills.defaultThreeStrategy();
        assertEquals("default-three-strategy", def.getMetadata().getName());
        assertNotNull(def.getInstructions());
        assertFalse(def.getInstructions().isBlank(), "instructions should not be blank");
        assertTrue(def.getInstructions().contains("NATURAL"));
        assertTrue(def.getInstructions().contains("PROACTIVE"));
        assertTrue(def.getInstructions().contains("LIGHT_FLIRT"));
    }

    @Test
    void defaultThreeStrategy_isSystemKindViaActiveSkill() {
        ActiveSkill s = ActiveSkill.systemDefaultThreeStrategy();
        assertTrue(s.isSystem());
        assertEquals("default-three-strategy", s.name());
    }
}
