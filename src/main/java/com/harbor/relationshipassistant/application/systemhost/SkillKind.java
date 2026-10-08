package com.harbor.relationshipassistant.application.systemhost;

/**
 * Skill 来源类型。
 *
 * <p>SYSTEM = RUI 内置 System Skill（当前唯一正式成员：默认三策略）。
 * USER   = 用户从 skills/ 目录加载的 User Skill（quick-reply / goutoujunshi / tong-jincheng-perspective 等）。</p>
 *
 * <p>这只是工程上的 Authority 标记，不是 LLM 权重打分。</p>
 */
public enum SkillKind {
    SYSTEM,
    USER
}
