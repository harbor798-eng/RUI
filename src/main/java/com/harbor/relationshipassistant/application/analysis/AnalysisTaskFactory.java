package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.application.skill.context.AnalysisContext;
import com.harbor.relationshipassistant.application.skill.context.AnalysisFunction;
import com.harbor.relationshipassistant.application.skill.context.AnalysisTask;

import java.util.Objects;

/**
 * 业务功能 → AnalysisTask 的纯转换层。
 * 不执行任务、不调 Router/LLM/DB；Skill 名称与 useKnowledge 由构造器显式配置。
 */
public final class AnalysisTaskFactory {
    private final String quickReplySkillName;
    private final String detailedAnalysisSkillName;
    private final String deepObservationSkillName;

    public AnalysisTaskFactory(String quickReplySkillName,
                               String detailedAnalysisSkillName,
                               String deepObservationSkillName) {
        requireNotBlank(quickReplySkillName, "quickReplySkillName");
        requireNotBlank(detailedAnalysisSkillName, "detailedAnalysisSkillName");
        requireNotBlank(deepObservationSkillName, "deepObservationSkillName");
        this.quickReplySkillName = quickReplySkillName;
        this.detailedAnalysisSkillName = detailedAnalysisSkillName;
        this.deepObservationSkillName = deepObservationSkillName;
    }

    public AnalysisTask quickReply(AnalysisContext context) {
        requireContext(context);
        return new AnalysisTask(AnalysisFunction.QUICK_REPLY, quickReplySkillName, context, false);
    }

    public AnalysisTask detailedAnalysis(AnalysisContext context) {
        requireContext(context);
        return new AnalysisTask(AnalysisFunction.DETAILED_ANALYSIS, detailedAnalysisSkillName, context, true);
    }

    public AnalysisTask deepObservation(AnalysisContext context) {
        requireContext(context);
        return new AnalysisTask(AnalysisFunction.DEEP_OBSERVATION, deepObservationSkillName, context, true);
    }

    private static void requireContext(AnalysisContext context) {
        if (context == null) throw new IllegalArgumentException("AnalysisContext must not be null");
    }

    private static void requireNotBlank(String s, String name) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
