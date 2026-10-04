package com.harbor.relationshipassistant.application.analysis.validator;

import com.harbor.relationshipassistant.domain.analysis.AnalysisContext;
import com.harbor.relationshipassistant.domain.analysis.EvidenceWindow;
import com.harbor.relationshipassistant.domain.analysis.PatternCandidate;
import com.harbor.relationshipassistant.domain.analysis.TimelineEvent;
import com.harbor.relationshipassistant.domain.analysis.deep.DeepAnalysisResult;
import com.harbor.relationshipassistant.domain.analysis.deep.LongTermPattern;
import com.harbor.relationshipassistant.domain.analysis.deep.RelationshipChange;
import com.harbor.relationshipassistant.domain.analysis.deep.TheoryExplanation;
import com.harbor.relationshipassistant.domain.analysis.knowledge.KnowledgeSelection;
import com.harbor.relationshipassistant.domain.analysis.validation.ValidationIssue;
import com.harbor.relationshipassistant.domain.analysis.validation.ValidationResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DeepAnalysisResult 的交叉校验：Base 复用 {@link AnalysisResultValidator}；
 * 额外校验 Deep 子项的 evidenceIds / patternIds / knowledgeIds 是否真实存在。
 */
public class DeepAnalysisValidator {

    private final AnalysisResultValidator baseValidator = new AnalysisResultValidator();

    public ValidationResult validate(DeepAnalysisResult deep, AnalysisContext ctx, List<KnowledgeSelection> selected) {
        List<ValidationIssue> issues = new ArrayList<>();
        ValidationResult baseVr = baseValidator.validate(deep.getBase(), ctx, selected);
        issues.addAll(baseVr.getIssues());

        Set<String> validEvidence = new HashSet<>();
        for (EvidenceWindow w : ctx.getEvidenceWindows()) validEvidence.add(w.getEvidenceId());
        Set<String> validPatterns = new HashSet<>();
        for (PatternCandidate p : ctx.getPatterns()) validPatterns.add(p.getPatternId());
        Set<String> selectedKb = new HashSet<>();
        if (selected != null) for (KnowledgeSelection k : selected) selectedKb.add(k.getKnowledgeId());

        // Timeline validation
        Set<String> seenEventIds = new HashSet<>();
        var rel = ctx.getRelationship();
        int ti = 0;
        for (TimelineEvent ev : deep.getTimeline()) {
            String path = "timeline[" + ti + "]";
            if (!seenEventIds.add(ev.getEventId())) {
                issues.add(ValidationIssue.error("DUPLICATE_EVENT_ID", "duplicate eventId: " + ev.getEventId(), path + ".eventId"));
            }
            Set<String> seenEids = new HashSet<>();
            for (String eid : ev.getEvidenceIds()) {
                if (!seenEids.add(eid)) {
                    issues.add(ValidationIssue.error("DUPLICATE_EVIDENCE_ID", "duplicate evidenceId: " + eid, path + ".evidenceIds"));
                }
                if (!validEvidence.contains(eid)) {
                    issues.add(ValidationIssue.error("INVALID_EVIDENCE_ID", "Unknown evidenceId: " + eid, path + ".evidenceIds"));
                }
            }
            if (ev.getTime() != null && rel != null) {
                if (rel.getAnalysisStart() != null && ev.getTime().isBefore(rel.getAnalysisStart())) {
                    issues.add(ValidationIssue.error("TIME_OUT_OF_RANGE", "time before analysisStart", path + ".time"));
                }
                if (rel.getAnalysisEnd() != null && ev.getTime().isAfter(rel.getAnalysisEnd())) {
                    issues.add(ValidationIssue.error("TIME_OUT_OF_RANGE", "time after analysisEnd", path + ".time"));
                }
            }
            ti++;
        }
        System.out.println("[DeepAnalysisValidator] timelineCount=" + deep.getTimeline().size());

        int i = 0;
        for (RelationshipChange c : deep.getRelationshipChanges()) {
            String path = "relationshipChanges[" + i + "]";
            for (String eid : c.getEvidenceIds()) {
                if (!validEvidence.contains(eid)) {
                    issues.add(ValidationIssue.error("INVALID_EVIDENCE_ID", "Unknown evidenceId: " + eid, path + ".evidenceIds"));
                }
            }
            i++;
        }
        i = 0;
        for (LongTermPattern p : deep.getLongTermPatterns()) {
            String path = "longTermPatterns[" + i + "]";
            for (String eid : p.getEvidenceIds()) {
                if (!validEvidence.contains(eid)) {
                    issues.add(ValidationIssue.error("INVALID_EVIDENCE_ID", "Unknown evidenceId: " + eid, path + ".evidenceIds"));
                }
            }
            for (String pid : p.getPatternIds()) {
                if (!validPatterns.contains(pid)) {
                    issues.add(ValidationIssue.error("INVALID_PATTERN_ID", "Unknown patternId: " + pid, path + ".patternIds"));
                }
            }
            i++;
        }
        i = 0;
        for (TheoryExplanation t : deep.getTheoryExplanations()) {
            String path = "theoryExplanations[" + i + "]";
            for (String eid : t.getEvidenceIds()) {
                if (!validEvidence.contains(eid)) {
                    issues.add(ValidationIssue.error("INVALID_EVIDENCE_ID", "Unknown evidenceId: " + eid, path + ".evidenceIds"));
                }
            }
            for (String kid : t.getKnowledgeIds()) {
                if (!selectedKb.contains(kid)) {
                    issues.add(ValidationIssue.error("INVALID_KNOWLEDGE_ID", "knowledgeId not in current selection: " + kid, path + ".knowledgeIds"));
                }
            }
            i++;
        }
        System.out.println("[DeepAnalysisValidator] evidenceIds/patternIds/knowledgeIds checked. issues=" + issues.size());
        return new ValidationResult(issues);
    }
}
