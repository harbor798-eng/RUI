package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.application.skill.SkillDescriptor;
import com.harbor.relationshipassistant.application.skill.adapter.SkillResolution;
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
        return new AnalysisTask(AnalysisFunction.QUICK_REPLY, quickReplySkillName, context, true);
    }

    /**
     * Quick Reply Function 消费 SkillResolution：用户选了 Skill 就用用户选的，否则用默认。
     * singleReply 旧兼容标记已从 v2 流程移除。
     */
    public AnalysisTask quickReply(AnalysisContext context, SkillResolution resolution) {
        requireContext(context);
        Objects.requireNonNull(resolution, "resolution");
        if (resolution.isUserSelected()) {
            String skillName = resolution.resolvedSkillName();
            return new AnalysisTask(AnalysisFunction.QUICK_REPLY, skillName, context, true);
        }
        return quickReply(context);
    }

    public AnalysisTask detailedAnalysis(AnalysisContext context) {
        requireContext(context);
        return new AnalysisTask(AnalysisFunction.DETAILED_ANALYSIS, detailedAnalysisSkillName, context, true);
    }

    /** Detailed Analysis Function 消费 SkillResolution：USER_SELECTED → 用户选的 Skill；其他 → 默认 goutoujunshi。 */
    public AnalysisTask detailedAnalysis(AnalysisContext context, SkillResolution resolution) {
        requireContext(context);
        Objects.requireNonNull(resolution, "resolution");
        if (resolution.isUserSelected()) {
            return new AnalysisTask(AnalysisFunction.DETAILED_ANALYSIS, resolution.resolvedSkillName(), context, true);
        }
        return detailedAnalysis(context);
    }

    public AnalysisTask deepObservation(AnalysisContext context) {
        requireContext(context);
        return new AnalysisTask(AnalysisFunction.DEEP_OBSERVATION, deepObservationSkillName, context, true);
    }

    /** 暴露 Function → 默认 Skill 名，供 SkillAdapter 复用，避免重复配置。 */
    public String defaultSkillName(AnalysisFunction function) {
        return switch (function) {
            case QUICK_REPLY -> quickReplySkillName;
            case DETAILED_ANALYSIS -> detailedAnalysisSkillName;
            case DEEP_OBSERVATION -> deepObservationSkillName;
        };
    }

    private static void requireContext(AnalysisContext context) {
        if (context == null) throw new IllegalArgumentException("AnalysisContext must not be null");
    }

    private static void requireNotBlank(String s, String name) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
