package com.harbor.relationshipassistant.application.skill;

/**
 * 请求的 Skill 不存在或未 READY。消息只包含 Skill 名称，不含敏感数据。
 */
public class SkillNotFoundException extends RuntimeException {
    public SkillNotFoundException(String skillName) {
        super("Skill not found: " + skillName);
    }
}
