package com.harbor.relationshipassistant.application.systemhost;

/**
 * 一次 RUI 执行中唯一的 Active Skill。
 *
 * <p>同一时间只能存在一个 Active Skill。它要么是 RUI 内置 System Skill，
 * 要么是用户显式选择的 User Skill。不存在 null Active Skill。</p>
 *
 * @param name Skill 名；System Skill 使用常量 {@link #DEFAULT_THREE_STRATEGY_NAME}
 * @param kind SYSTEM 或 USER
 */
public record ActiveSkill(String name, SkillKind kind) {

    /** System Skill 唯一正式成员：默认三策略。 */
    public static final String DEFAULT_THREE_STRATEGY_NAME = "default-three-strategy";

    public static ActiveSkill systemDefaultThreeStrategy() {
        return new ActiveSkill(DEFAULT_THREE_STRATEGY_NAME, SkillKind.SYSTEM);
    }

    public static ActiveSkill userSkill(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("User Skill name must not be blank");
        }
        return new ActiveSkill(name, SkillKind.USER);
    }

    public boolean isSystem() {
        return kind == SkillKind.SYSTEM;
    }
}
