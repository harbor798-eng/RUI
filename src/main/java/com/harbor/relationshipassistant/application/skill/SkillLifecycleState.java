package com.harbor.relationshipassistant.application.skill;

/**
 * Skill 运行时生命周期状态。
 * 只描述 JEVE 当前如何管理这个 Skill，不代表 Skill 内容本身。
 */
public enum SkillLifecycleState {
    /** 已发现目录，尚未加载。 */
    DISCOVERED,
    /** 正在加载/校验。 */
    LOADING,
    /** 加载成功且已注册到 Registry。 */
    READY,
    /** 加载或校验失败，旧版本仍在 Registry 中可用。 */
    INVALID,
    /** 目录已删除，正在从 Registry 移除。 */
    REMOVED
}
