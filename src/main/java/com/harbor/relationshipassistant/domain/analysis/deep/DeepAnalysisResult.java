package com.harbor.relationshipassistant.domain.analysis.deep;

import com.harbor.relationshipassistant.domain.analysis.AnalysisOutput;
import com.harbor.relationshipassistant.domain.analysis.AnalysisResult;
import com.harbor.relationshipassistant.domain.analysis.TimelineEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * DEEP_OBSERVATION 的结果：Base {@link AnalysisResult} + Deep-only 扩展。
 * <p>
 * composition 而非继承，避免修改现有 AnalysisResult。
 * </p>
 */
public final class DeepAnalysisResult implements AnalysisOutput {

    private final AnalysisResult base;
    private final List<TimelineEvent> timeline;
    private final List<RelationshipChange> relationshipChanges;
    private final List<LongTermPattern> longTermPatterns;
    private final List<TheoryExplanation> theoryExplanations;

    public DeepAnalysisResult(AnalysisResult base,
                              List<TimelineEvent> timeline,
                              List<RelationshipChange> relationshipChanges,
                              List<LongTermPattern> longTermPatterns,
                              List<TheoryExplanation> theoryExplanations) {
        this.base = Objects.requireNonNull(base, "base");
        this.timeline = immutable(timeline);
        this.relationshipChanges = immutable(relationshipChanges);
        this.longTermPatterns = immutable(longTermPatterns);
        this.theoryExplanations = immutable(theoryExplanations);
    }

    public AnalysisResult getBase() { return base; }
    public List<TimelineEvent> getTimeline() { return timeline; }
    public List<RelationshipChange> getRelationshipChanges() { return relationshipChanges; }
    public List<LongTermPattern> getLongTermPatterns() { return longTermPatterns; }
    public List<TheoryExplanation> getTheoryExplanations() { return theoryExplanations; }

    private static <T> List<T> immutable(List<T> src) {
        if (src == null || src.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(src));
    }
}
