package com.harbor.relationshipassistant.application.analysis.report;

import com.harbor.relationshipassistant.domain.analysis.AnalysisContext;
import com.harbor.relationshipassistant.domain.analysis.AnalysisResult;
import com.harbor.relationshipassistant.domain.analysis.EvidenceWindow;
import com.harbor.relationshipassistant.domain.analysis.PatternCandidate;
import com.harbor.relationshipassistant.domain.analysis.TimelineEvent;
import com.harbor.relationshipassistant.domain.analysis.deep.DeepAnalysisResult;
import com.harbor.relationshipassistant.domain.analysis.deep.LongTermPattern;
import com.harbor.relationshipassistant.domain.analysis.deep.RelationshipChange;
import com.harbor.relationshipassistant.domain.analysis.deep.TheoryExplanation;
import com.harbor.relationshipassistant.domain.analysis.knowledge.KnowledgeSelection;
import com.harbor.relationshipassistant.domain.analysis.report.DeepObservationReport;
import com.harbor.relationshipassistant.domain.analysis.report.ReportMetadata;
import com.harbor.relationshipassistant.domain.analysis.report.ReportSection;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 把 {@link DeepAnalysisResult} + {@link AnalysisContext} 组装成 {@link DeepObservationReport}。
 * 纯组装，不重新分析、不调用 LLM、不访问 DB。
 */
public class DeepObservationReportBuilder {

    public DeepObservationReport build(DeepAnalysisResult deep, AnalysisContext ctx,
                                       List<KnowledgeSelection> selected) {
        if (deep == null) throw new IllegalArgumentException("deep must not be null");
        if (ctx == null) throw new IllegalArgumentException("context must not be null");
        if (ctx.getTask().getRelationshipId() != deep.getBase().getMetadata().getRelationshipId()
                && ctx.getTask().getRelationshipId() != ctx.getRelationship().getRelationshipId()) {
            // relationshipId 一致性：以 context.task 为准
        }

        System.out.println("[DeepObservationReportBuilder] Building report. relationshipId="
                + ctx.getTask().getRelationshipId());

        validateReferences(deep, ctx, selected);

        AnalysisResult base = deep.getBase();
        ReportMetadata meta = new ReportMetadata(
                "RPT-" + UUID.randomUUID(),
                ctx.getTask().getRelationshipId(),
                ctx.getTask().getTaskType(),
                ctx.getTask().getSkill(),
                ctx.getTask().getRange(),
                ctx.getRelationship().getAnalysisStart(),
                ctx.getRelationship().getAnalysisEnd(),
                ctx.getMetadata().getActualDataStart(),
                ctx.getMetadata().getActualDataEnd(),
                ctx.getMetadata().isDataComplete(),
                ctx.getMetadata().getAnalyzedMessageCount(),
                LocalDateTime.now()
        );

        List<ReportSection> sections = new ArrayList<>();
        if (!base.getFacts().isEmpty()) sections.add(ReportSection.FACTS);
        if (!deep.getTimeline().isEmpty()) sections.add(ReportSection.TIMELINE);
        if (!base.getObservations().isEmpty()) sections.add(ReportSection.BEHAVIOR_PATTERNS);
        if (!base.getEmotions().isEmpty()) sections.add(ReportSection.EMOTION_CHANGES);
        if (!deep.getRelationshipChanges().isEmpty()) sections.add(ReportSection.RELATIONSHIP_CHANGES);
        if (!deep.getLongTermPatterns().isEmpty()) sections.add(ReportSection.LONG_TERM_PATTERNS);
        if (!base.getPossibilities().isEmpty()) sections.add(ReportSection.POSSIBILITIES);
        if (!deep.getTheoryExplanations().isEmpty()) sections.add(ReportSection.THEORY_EXPLANATIONS);
        if (!base.getUserIssues().isEmpty()) sections.add(ReportSection.USER_ISSUES);
        if (!base.getRecommendations().isEmpty()) sections.add(ReportSection.RECOMMENDATIONS);

        System.out.println("[DeepObservationReportBuilder] facts=" + base.getFacts().size()
                + " timeline=" + deep.getTimeline().size()
                + " behaviorPatterns=" + base.getObservations().size()
                + " emotionChanges=" + base.getEmotions().size()
                + " relationshipChanges=" + deep.getRelationshipChanges().size()
                + " longTermPatterns=" + deep.getLongTermPatterns().size()
                + " possibilities=" + base.getPossibilities().size()
                + " unknowns=" + base.getUnknowns().size()
                + " theoryExplanations=" + deep.getTheoryExplanations().size()
                + " userIssues=" + base.getUserIssues().size()
                + " recommendations=" + base.getRecommendations().size()
                + " sections=" + sections.size());

        return new DeepObservationReport(meta,
                base.getFacts(),
                deep.getTimeline(),
                base.getObservations(),
                base.getEmotions(),
                deep.getRelationshipChanges(),
                deep.getLongTermPatterns(),
                base.getPossibilities(),
                base.getUnknowns(),
                deep.getTheoryExplanations(),
                base.getUserIssues(),
                base.getRecommendations(),
                sections);
    }

    private void validateReferences(DeepAnalysisResult deep, AnalysisContext ctx,
                                    List<KnowledgeSelection> selected) {
        Set<String> validEvidence = new HashSet<>();
        for (EvidenceWindow w : ctx.getEvidenceWindows()) validEvidence.add(w.getEvidenceId());
        Set<String> validPatterns = new HashSet<>();
        for (PatternCandidate p : ctx.getPatterns()) validPatterns.add(p.getPatternId());
        Set<String> validKb = new HashSet<>();
        if (selected != null) for (KnowledgeSelection k : selected) validKb.add(k.getKnowledgeId());

        for (TimelineEvent ev : deep.getTimeline()) {
            for (String eid : ev.getEvidenceIds()) {
                if (!validEvidence.contains(eid)) throw new IllegalArgumentException(
                        "Timeline event " + ev.getEventId() + " references unknown evidenceId: " + eid);
            }
        }
        for (RelationshipChange c : deep.getRelationshipChanges()) {
            for (String eid : c.getEvidenceIds()) {
                if (!validEvidence.contains(eid)) throw new IllegalArgumentException(
                        "RelationshipChange references unknown evidenceId: " + eid);
            }
        }
        for (LongTermPattern p : deep.getLongTermPatterns()) {
            for (String eid : p.getEvidenceIds()) {
                if (!validEvidence.contains(eid)) throw new IllegalArgumentException(
                        "LongTermPattern references unknown evidenceId: " + eid);
            }
            for (String pid : p.getPatternIds()) {
                if (!validPatterns.contains(pid)) throw new IllegalArgumentException(
                        "LongTermPattern references unknown patternId: " + pid);
            }
        }
        for (TheoryExplanation t : deep.getTheoryExplanations()) {
            for (String eid : t.getEvidenceIds()) {
                if (!validEvidence.contains(eid)) throw new IllegalArgumentException(
                        "TheoryExplanation references unknown evidenceId: " + eid);
            }
            for (String kid : t.getKnowledgeIds()) {
                if (!validKb.contains(kid)) throw new IllegalArgumentException(
                        "TheoryExplanation references non-selected knowledgeId: " + kid);
            }
        }
    }
}
