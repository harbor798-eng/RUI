package com.harbor.relationshipassistant.domain.analysis;

import com.harbor.relationshipassistant.domain.analysis.statistics.StatisticsContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 一次 AI 分析任务允许看到的数据集合。
 * <p>
 * 这是纯内存运行时对象，不是数据库 Entity。
 * 由 ChatProcessor / Statistics / Evidence / Pattern 等阶段填充，再交给 PromptAssembler。
 * </p>
 * <p>
 * Batch 10：新增 {@link StatisticsContext}，同时承载 MessageStatistics 与 InteractionStatistics。
 * </p>
 */
public final class AnalysisContext {

    private final AnalysisTask task;
    private final RelationshipContext relationship;
    private final ProfileContext profiles;
    private final StatisticsContext statistics;
    private final AnalysisMetadata metadata;
    private final List<EvidenceWindow> evidenceWindows;
    private final List<TimelineEvent> timeline;
    private final List<PatternCandidate> patterns;

    public AnalysisContext(AnalysisTask task,
                           RelationshipContext relationship,
                           ProfileContext profiles,
                           StatisticsContext statistics,
                           AnalysisMetadata metadata,
                           List<EvidenceWindow> evidenceWindows,
                           List<TimelineEvent> timeline,
                           List<PatternCandidate> patterns) {
        this.task = Objects.requireNonNull(task, "task");
        this.relationship = Objects.requireNonNull(relationship, "relationship");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.statistics = statistics;
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.evidenceWindows = immutable(evidenceWindows);
        this.timeline = immutable(timeline);
        this.patterns = immutable(patterns);
    }

    private static <T> List<T> immutable(List<T> src) {
        if (src == null || src.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(src));
    }

    public AnalysisTask getTask() { return task; }
    public RelationshipContext getRelationship() { return relationship; }
    public ProfileContext getProfiles() { return profiles; }
    public StatisticsContext getStatistics() { return statistics; }
    public AnalysisMetadata getMetadata() { return metadata; }
    public List<EvidenceWindow> getEvidenceWindows() { return evidenceWindows; }
    public List<TimelineEvent> getTimeline() { return timeline; }
    public List<PatternCandidate> getPatterns() { return patterns; }
}
