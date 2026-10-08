package com.harbor.relationshipassistant.application.skill.context;

import com.harbor.relationshipassistant.application.skill.SkillDefinition;
import com.harbor.relationshipassistant.application.systemhost.PlannedExecution;

/**
 * Skill 执行时收到的完整运行上下文。
 * = task（做什么） + context（能看到什么） + skill（怎么做） + systemHost（System Host 边界）。
 */
public final class SkillExecutionContext {
    private final AnalysisTask task;
    private final AnalysisContext context;
    private final SkillDefinition skill;
    private final PlannedExecution plannedExecution;

    public SkillExecutionContext(AnalysisTask task, AnalysisContext context, SkillDefinition skill) {
        this(task, context, skill, task == null ? null : task.getPlannedExecution());
    }

    public SkillExecutionContext(AnalysisTask task, AnalysisContext context, SkillDefinition skill,
                                PlannedExecution plannedExecution) {
        this.task = task;
        this.context = context;
        this.skill = skill;
        this.plannedExecution = plannedExecution;
    }

    public AnalysisTask getTask() { return task; }
    public AnalysisContext getContext() { return context; }
    public SkillDefinition getSkill() { return skill; }
    public PlannedExecution getPlannedExecution() { return plannedExecution; }
}
