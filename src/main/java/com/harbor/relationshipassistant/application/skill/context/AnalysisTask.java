package com.harbor.relationshipassistant.application.skill.context;

import com.harbor.relationshipassistant.application.systemhost.PlannedExecution;
import java.util.Set;

/**
 * 一次 AI 分析任务描述。纯数据对象，不执行任何业务逻辑。
 */
public final class AnalysisTask {
    private final AnalysisFunction function;
    private final String requestedSkillName;
    private final AnalysisContext context;
    private final boolean useKnowledge;
    private final PlannedExecution plannedExecution;
    private final Set<String> knowledgeScope;

    public AnalysisTask(AnalysisFunction function, String requestedSkillName, AnalysisContext context) {
        this(function, requestedSkillName, context, false, null);
    }

    public AnalysisTask(AnalysisFunction function, String requestedSkillName, AnalysisContext context, boolean useKnowledge) {
        this(function, requestedSkillName, context, useKnowledge, null);
    }

    public AnalysisTask(AnalysisFunction function, String requestedSkillName, AnalysisContext context,
                        boolean useKnowledge, PlannedExecution plannedExecution) {
        this(function, requestedSkillName, context, useKnowledge, plannedExecution, Set.of());
    }

    public AnalysisTask(AnalysisFunction function, String requestedSkillName, AnalysisContext context,
                        boolean useKnowledge, PlannedExecution plannedExecution, Set<String> knowledgeScope) {
        this.function = function;
        this.requestedSkillName = requestedSkillName;
        this.context = context;
        this.useKnowledge = useKnowledge;
        this.plannedExecution = plannedExecution;
        this.knowledgeScope = knowledgeScope == null ? Set.of() : Set.copyOf(knowledgeScope);
    }

    public AnalysisFunction getFunction() { return function; }
    public String getRequestedSkillName() { return requestedSkillName; }
    public AnalysisContext getContext() { return context; }
    public boolean isUseKnowledge() { return useKnowledge; }
    public PlannedExecution getPlannedExecution() { return plannedExecution; }
    public Set<String> getKnowledgeScope() { return knowledgeScope; }

    public AnalysisTask withPlannedExecution(PlannedExecution plan) {
        return new AnalysisTask(function, requestedSkillName, context, useKnowledge, plan, knowledgeScope);
    }

    public AnalysisTask withKnowledgeScope(Set<String> scope) {
        return new AnalysisTask(function, requestedSkillName, context, useKnowledge, plannedExecution, scope);
    }
}
