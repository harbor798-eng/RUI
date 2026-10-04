package com.harbor.relationshipassistant.domain.analysis.report;

import com.harbor.relationshipassistant.domain.analysis.AnalysisObservation;
import com.harbor.relationshipassistant.domain.analysis.EmotionObservation;
import com.harbor.relationshipassistant.domain.analysis.Fact;
import com.harbor.relationshipassistant.domain.analysis.Possibility;
import com.harbor.relationshipassistant.domain.analysis.Recommendation;
import com.harbor.relationshipassistant.domain.analysis.TimelineEvent;
import com.harbor.relationshipassistant.domain.analysis.Unknown;
import com.harbor.relationshipassistant.domain.analysis.UserIssue;
import com.harbor.relationshipassistant.domain.analysis.deep.LongTermPattern;
import com.harbor.relationshipassistant.domain.analysis.deep.RelationshipChange;
import com.harbor.relationshipassistant.domain.analysis.deep.TheoryExplanation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Deep Observation 的结构化报告数据（面向 Renderer，不是 HTML）。
 * <p>
 * 与 {@code DeepAnalysisResult} 解耦：HTML / Natural Language / PDF Renderer 都只依赖本类。
 * 不含任何关系评分、人格诊断字段。
 * </p>
 */
public final class DeepObservationReport {

    private final ReportMetadata metadata;
    private final List<Fact> facts;
    private final List<TimelineEvent> timeline;
    private final List<AnalysisObservation> behaviorPatterns;
    private final List<EmotionObservation> emotionChanges;
    private final List<RelationshipChange> relationshipChanges;
    private final List<LongTermPattern> longTermPatterns;
    private final List<Possibility> possibilities;
    private final List<Unknown> unknowns;
    private final List<TheoryExplanation> theoryExplanations;
    private final List<UserIssue> userIssues;
    private final List<Recommendation> recommendations;
    private final List<ReportSection> sections;

    public DeepObservationReport(ReportMetadata metadata,
                                 List<Fact> facts,
                                 List<TimelineEvent> timeline,
                                 List<AnalysisObservation> behaviorPatterns,
                                 List<EmotionObservation> emotionChanges,
                                 List<RelationshipChange> relationshipChanges,
                                 List<LongTermPattern> longTermPatterns,
                                 List<Possibility> possibilities,
                                 List<Unknown> unknowns,
                                 List<TheoryExplanation> theoryExplanations,
                                 List<UserIssue> userIssues,
                                 List<Recommendation> recommendations,
                                 List<ReportSection> sections) {
        this.metadata = Objects.requireNonNull(metadata);
        this.facts = immutable(facts);
        this.timeline = immutable(timeline);
        this.behaviorPatterns = immutable(behaviorPatterns);
        this.emotionChanges = immutable(emotionChanges);
        this.relationshipChanges = immutable(relationshipChanges);
        this.longTermPatterns = immutable(longTermPatterns);
        this.possibilities = immutable(possibilities);
        this.unknowns = immutable(unknowns);
        this.theoryExplanations = immutable(theoryExplanations);
        this.userIssues = immutable(userIssues);
        this.recommendations = immutable(recommendations);
        this.sections = immutable(sections);
    }

    public ReportMetadata getMetadata() { return metadata; }
    public List<Fact> getFacts() { return facts; }
    public List<TimelineEvent> getTimeline() { return timeline; }
    public List<AnalysisObservation> getBehaviorPatterns() { return behaviorPatterns; }
    public List<EmotionObservation> getEmotionChanges() { return emotionChanges; }
    public List<RelationshipChange> getRelationshipChanges() { return relationshipChanges; }
    public List<LongTermPattern> getLongTermPatterns() { return longTermPatterns; }
    public List<Possibility> getPossibilities() { return possibilities; }
    public List<Unknown> getUnknowns() { return unknowns; }
    public List<TheoryExplanation> getTheoryExplanations() { return theoryExplanations; }
    public List<UserIssue> getUserIssues() { return userIssues; }
    public List<Recommendation> getRecommendations() { return recommendations; }
    public List<ReportSection> getSections() { return sections; }

    private static <T> List<T> immutable(List<T> src) {
        if (src == null || src.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(src));
    }
}
