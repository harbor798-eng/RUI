package com.harbor.relationshipassistant.domain.analysis;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 每个 {@link AnalysisTaskType} 允许产出的 Result 段。
 * <p>
 * 这是程序级能力声明，不参与 Prompt 拼装、不替代 Validator。
 * </p>
 */
public final class TaskCapability {

    public enum Section {
        FACTS,
        OBSERVATIONS,
        POSSIBILITIES,
        EMOTIONS,
        USER_ISSUES,
        RECOMMENDATIONS,
        QUICK_REPLY,
        TIMELINE,
        RELATIONSHIP_CHANGES,
        LONG_TERM_PATTERNS,
        THEORY_EXPLANATIONS
    }

    private final AnalysisTaskType taskType;
    private final Set<Section> allowedSections;
    private final Skill requiredSkill; // null = 任意
    private final Class<?> resultType;

    private TaskCapability(AnalysisTaskType taskType, Set<Section> sections,
                           Skill requiredSkill, Class<?> resultType) {
        this.taskType = taskType;
        this.allowedSections = Collections.unmodifiableSet(EnumSet.copyOf(sections));
        this.requiredSkill = requiredSkill;
        this.resultType = resultType;
    }

    public static TaskCapability of(AnalysisTaskType type) {
        return switch (type) {
            case QUICK_REPLY -> new TaskCapability(type,
                    EnumSet.of(Section.QUICK_REPLY), Skill.NONE,
                    com.harbor.relationshipassistant.domain.analysis.quickreply.QuickReplyResult.class);
            case DETAIL_ANALYSIS -> new TaskCapability(type,
                    EnumSet.of(Section.FACTS, Section.OBSERVATIONS, Section.POSSIBILITIES,
                            Section.EMOTIONS, Section.USER_ISSUES, Section.RECOMMENDATIONS),
                    null, AnalysisResult.class);
            case DEEP_OBSERVATION -> new TaskCapability(type,
                    EnumSet.of(Section.FACTS, Section.OBSERVATIONS, Section.POSSIBILITIES,
                            Section.EMOTIONS, Section.USER_ISSUES, Section.RECOMMENDATIONS,
                            Section.TIMELINE, Section.RELATIONSHIP_CHANGES,
                            Section.LONG_TERM_PATTERNS, Section.THEORY_EXPLANATIONS),
                    Skill.GOUTOUJUNSHI,
                    com.harbor.relationshipassistant.domain.analysis.deep.DeepAnalysisResult.class);
        };
    }

    public AnalysisTaskType getTaskType() { return taskType; }
    public Set<Section> getAllowedSections() { return allowedSections; }
    public Skill getRequiredSkill() { return requiredSkill; }
    public Class<?> getResultType() { return resultType; }

    public boolean allows(Section s) { return allowedSections.contains(s); }

    public boolean isSkillSatisfied(Skill s) {
        return requiredSkill == null || requiredSkill == s;
    }
}
