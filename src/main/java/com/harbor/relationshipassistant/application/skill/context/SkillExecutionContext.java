package com.harbor.relationshipassistant.application.skill.context;

import com.harbor.relationshipassistant.application.skill.SkillDefinition;

/**
 * Skill 执行时收到的完整运行上下文。
 * = task（做什么） + context（能看到什么） + skill（怎么做）。
 */
public final class SkillExecutionContext {
    private final AnalysisTask task;
    private final AnalysisContext context;
    private final SkillDefinition skill;

    public SkillExecutionContext(AnalysisTask task, AnalysisContext context, SkillDefinition skill) {
        this.task = task;
        this.context = context;
        this.skill = skill;
    }

    public AnalysisTask getTask() { return task; }
    public AnalysisContext getContext() { return context; }
    public SkillDefinition getSkill() { return skill; }
}
