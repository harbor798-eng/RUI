package com.harbor.relationshipassistant.application.analysis.validator;

import com.harbor.relationshipassistant.domain.analysis.AnalysisContext;
import com.harbor.relationshipassistant.domain.analysis.AnalysisObservation;
import com.harbor.relationshipassistant.domain.analysis.AnalysisResult;
import com.harbor.relationshipassistant.domain.analysis.EmotionObservation;
import com.harbor.relationshipassistant.domain.analysis.EvidenceWindow;
import com.harbor.relationshipassistant.domain.analysis.Fact;
import com.harbor.relationshipassistant.domain.analysis.FactType;
import com.harbor.relationshipassistant.domain.analysis.Possibility;
import com.harbor.relationshipassistant.domain.analysis.Recommendation;
import com.harbor.relationshipassistant.domain.analysis.Unknown;
import com.harbor.relationshipassistant.domain.analysis.UserIssue;
import com.harbor.relationshipassistant.domain.analysis.knowledge.KnowledgeSelection;
import com.harbor.relationshipassistant.domain.analysis.validation.ValidationIssue;
import com.harbor.relationshipassistant.domain.analysis.validation.ValidationResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 校验 LLM 返回的 {@link AnalysisResult} 是否遵守 JEVE 的结构 / 证据 / 权限约束。
 * <p>
 * 不重新分析关系、不判断 AI 观点是否正确、不自动修复。
 * </p>
 */
public class AnalysisResultValidator {

    public ValidationResult validate(AnalysisResult result,
                                     AnalysisContext context,
                                     List<KnowledgeSelection> selectedKnowledge) {
        System.out.println("[AnalysisResultValidator] start");
        List<ValidationIssue> issues = new ArrayList<>();
        if (result == null) {
            issues.add(ValidationIssue.error("NULL_RESULT", "AnalysisResult is null", "root"));
            return new ValidationResult(issues);
        }
        if (context == null) {
            issues.add(ValidationIssue.error("NULL_CONTEXT", "AnalysisContext is null", "root"));
            return new ValidationResult(issues);
        }

        Set<String> validEvidenceIds = new HashSet<>();
        for (EvidenceWindow w : context.getEvidenceWindows()) validEvidenceIds.add(w.getEvidenceId());
        Set<String> meFields = context.getProfiles().getMe().keySet();
        Set<String> otherFields = context.getProfiles().getOther().keySet();
        Set<String> selectedKbIds = new HashSet<>();
        if (selectedKnowledge != null) {
            for (KnowledgeSelection k : selectedKnowledge) selectedKbIds.add(k.getKnowledgeId());
        }

        checkFacts(result.getFacts(), validEvidenceIds, meFields, otherFields, issues);
        checkObservations(result.getObservations(), validEvidenceIds, issues);
        checkPossibilities(result.getPossibilities(), validEvidenceIds, issues);
        checkUnknowns(result.getUnknowns(), validEvidenceIds, issues);
        checkEmotions(result.getEmotions(), validEvidenceIds, issues);
        checkUserIssues(result.getUserIssues(), validEvidenceIds, issues);
        checkRecommendations(result.getRecommendations(), validEvidenceIds, issues);

        ValidationResult vr = new ValidationResult(issues);
        System.out.println("[AnalysisResultValidator] done. valid=" + vr.isValid()
                + ", errors=" + issues.stream().filter(i -> i.getSeverity() == ValidationIssue.Severity.ERROR).count());
        return vr;
    }

    private void checkFacts(List<Fact> facts, Set<String> validEvidenceIds,
                            Set<String> meFields, Set<String> otherFields, List<ValidationIssue> issues) {
        if (facts == null) return;
        for (int i = 0; i < facts.size(); i++) {
            Fact f = facts.get(i);
            String p = "facts[" + i + "]";
            if (f.getType() == null) {
                issues.add(ValidationIssue.error("MISSING_REQUIRED_FIELD", "Fact.type is null", p + ".type"));
                continue;
            }
            if (f.getType() == FactType.CHAT) {
                if (f.getEvidenceIds() == null || f.getEvidenceIds().isEmpty()) {
                    issues.add(ValidationIssue.error("EMPTY_EVIDENCE_IDS", "CHAT Fact requires evidenceIds", p));
                }
                for (String eid : f.getEvidenceIds()) {
                    if (!validEvidenceIds.contains(eid)) {
                        issues.add(ValidationIssue.error("INVALID_EVIDENCE_ID", "Unknown evidenceId: " + eid, p + ".evidenceIds"));
                    }
                }
            } else if (f.getType() == FactType.STATISTICS) {
                if (f.getStatisticKeys() == null || f.getStatisticKeys().isEmpty()) {
                    issues.add(ValidationIssue.error("INVALID_STATISTIC_KEY", "STATISTICS Fact requires statisticKeys", p));
                }
            } else if (f.getType() == FactType.USER_CONFIRMED) {
                if (f.getProfileField() == null || f.getProfileField().isBlank()) {
                    issues.add(ValidationIssue.error("INVALID_USER_CONFIRMED_FACT", "USER_CONFIRMED Fact requires profileField", p));
                } else if (!meFields.contains(f.getProfileField()) && !otherFields.contains(f.getProfileField())) {
                    issues.add(ValidationIssue.error("INVALID_PROFILE_FIELD",
                            "USER_CONFIRMED field not in AI-visible profile: " + f.getProfileField(), p + ".profileField"));
                }
            }
        }
    }

    private void checkObservations(List<AnalysisObservation> list, Set<String> validEvidenceIds, List<ValidationIssue> issues) {
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            AnalysisObservation o = list.get(i);
            String p = "observations[" + i + "]";
            checkProbability(o.getConfidence(), p + ".confidence", issues);
            checkEvidenceIds(o.getEvidenceIds(), validEvidenceIds, p, issues);
        }
    }

    private void checkPossibilities(List<Possibility> list, Set<String> validEvidenceIds, List<ValidationIssue> issues) {
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            Possibility po = list.get(i);
            String p = "possibilities[" + i + "]";
            if (po.getEstimatedProbability() != null) {
                double v = po.getEstimatedProbability();
                if (v < 0 || v > 1) {
                    issues.add(ValidationIssue.error("INVALID_NUMBER_RANGE", "estimatedProbability out of [0,1]: " + v, p + ".estimatedProbability"));
                }
            }
            checkEvidenceIds(po.getEvidenceIds(), validEvidenceIds, p, issues);
            for (String eid : po.getCounterEvidenceIds()) {
                if (!validEvidenceIds.contains(eid)) {
                    issues.add(ValidationIssue.error("INVALID_COUNTER_EVIDENCE_ID", "Unknown counter evidenceId: " + eid, p + ".counterEvidenceIds"));
                }
            }
        }
    }

    private void checkUnknowns(List<Unknown> list, Set<String> validEvidenceIds, List<ValidationIssue> issues) {
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            Unknown u = list.get(i);
            checkEvidenceIds(u.getRelatedEvidenceIds(), validEvidenceIds, "unknowns[" + i + "]", issues);
        }
    }

    private void checkEmotions(List<EmotionObservation> list, Set<String> validEvidenceIds, List<ValidationIssue> issues) {
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            EmotionObservation e = list.get(i);
            String p = "emotions[" + i + "]";
            if (e.getPerson() == null) {
                issues.add(ValidationIssue.error("MISSING_REQUIRED_FIELD", "emotion person is null", p + ".person"));
            }
            if (e.getEstimatedProbability() != null) {
                double v = e.getEstimatedProbability();
                if (v < 0 || v > 1) {
                    issues.add(ValidationIssue.error("INVALID_NUMBER_RANGE", "emotion probability out of [0,1]: " + v, p + ".estimatedProbability"));
                }
            }
            checkEvidenceIds(e.getEvidenceIds(), validEvidenceIds, p, issues);
        }
    }

    private void checkUserIssues(List<UserIssue> list, Set<String> validEvidenceIds, List<ValidationIssue> issues) {
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            UserIssue u = list.get(i);
            String p = "userIssues[" + i + "]";
            checkProbability(u.getConfidence(), p + ".confidence", issues);
            checkEvidenceIds(u.getEvidenceIds(), validEvidenceIds, p, issues);
        }
    }

    private void checkRecommendations(List<Recommendation> list, Set<String> validEvidenceIds, List<ValidationIssue> issues) {
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            Recommendation r = list.get(i);
            String p = "recommendations[" + i + "]";
            if (r.getReasonEvidenceIds() == null || r.getReasonEvidenceIds().isEmpty()) {
                issues.add(ValidationIssue.warning("RECOMMENDATION_NO_EVIDENCE", "Recommendation has no reason evidence", p));
            } else {
                checkEvidenceIds(r.getReasonEvidenceIds(), validEvidenceIds, p, issues);
            }
        }
    }

    private void checkEvidenceIds(List<String> ids, Set<String> valid, String path, List<ValidationIssue> issues) {
        if (ids == null) return;
        for (String eid : ids) {
            if (!valid.contains(eid)) {
                issues.add(ValidationIssue.error("INVALID_EVIDENCE_ID", "Unknown evidenceId: " + eid, path + ".evidenceIds"));
            }
        }
    }

    private void checkProbability(double v, String path, List<ValidationIssue> issues) {
        if (v < 0 || v > 1) {
            issues.add(ValidationIssue.error("INVALID_NUMBER_RANGE", "probability out of [0,1]: " + v, path));
        }
    }
}
