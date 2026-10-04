package com.harbor.relationshipassistant.application.skill.context;

/**
 * 一次 AI 分析任务描述。纯数据对象，不执行任何业务逻辑。
 */
public final class AnalysisTask {
    private final AnalysisFunction function;
    private final String requestedSkillName;
    private final AnalysisContext context;
    private final boolean useKnowledge;

    public AnalysisTask(AnalysisFunction function, String requestedSkillName, AnalysisContext context) {
        this(function, requestedSkillName, context, false);
    }

    public AnalysisTask(AnalysisFunction function, String requestedSkillName, AnalysisContext context, boolean useKnowledge) {
        this.function = function;
        this.requestedSkillName = requestedSkillName;
        this.context = context;
        this.useKnowledge = useKnowledge;
    }

    public AnalysisFunction getFunction() { return function; }
    public String getRequestedSkillName() { return requestedSkillName; }
    public AnalysisContext getContext() { return context; }
    public boolean isUseKnowledge() { return useKnowledge; }
}
