package com.harbor.relationshipassistant.application.skill.adapter;

/**
 * 本次请求中 Skill 的来源。
 * NONE: 当前 Function 没有 Skill。
 * FUNCTION_DEFAULT: 用户未选择，使用 Function 内置默认 Skill。
 * USER_SELECTED: 用户显式选择了某个 Skill。
 */
public enum SkillSelectionSource {
    NONE,
    FUNCTION_DEFAULT,
    USER_SELECTED
}
