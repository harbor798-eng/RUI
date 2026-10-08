package com.harbor.relationshipassistant.application.skill.adapter;

/**
 * 用户显式选择的 Skill 不存在或未 READY。
 * 不能静默降级到 Function 默认。
 */
public class SkillNotAvailableException extends RuntimeException {
    private final String skillName;

    public SkillNotAvailableException(String skillName, String message) {
        super(message);
        this.skillName = skillName;
    }

    public String getSkillName() {
        return skillName;
    }
}
