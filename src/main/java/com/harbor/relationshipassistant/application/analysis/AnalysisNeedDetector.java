package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.domain.analysis.AnalysisContext;
import com.harbor.relationshipassistant.domain.analysis.EvidenceLevel;
import com.harbor.relationshipassistant.domain.analysis.EvidenceType;
import com.harbor.relationshipassistant.domain.analysis.EvidenceWindow;
import com.harbor.relationshipassistant.domain.analysis.PatternCandidate;
import com.harbor.relationshipassistant.domain.analysis.need.AnalysisNeed;
import com.harbor.relationshipassistant.domain.analysis.need.NeedPriority;
import com.harbor.relationshipassistant.domain.analysis.need.NeedType;
import com.harbor.relationshipassistant.domain.analysis.skill.SkillDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 根据 {@link AnalysisContext} 中的 Evidence / Pattern，生成"本次需要分析什么"的中间对象。
 * <p>
 * 纯本地规则；不调用 LLM、不选 Knowledge、不拼 Prompt、不做心理诊断、不做未来预测。
 * 同一 NeedType 的多个触发会被合并；所有 evidenceIds / patternIds 必须来自 Context。
 * </p>
 */
public class AnalysisNeedDetector {

    public List<AnalysisNeed> detect(AnalysisContext context, SkillDefinition skillDefinition) {
        System.out.println("[AnalysisNeedDetector] Start detecting analysis needs");
        if (context == null || context.getTask() == null) {
            throw new IllegalArgumentException("[AnalysisNeedDetector][ERROR] context/task must not be null");
        }
        System.out.println("[AnalysisNeedDetector] taskId=" + context.getTask().getId());
        System.out.println("[AnalysisNeedDetector] relationshipId=" + context.getTask().getRelationshipId());
        System.out.println("[AnalysisNeedDetector] taskType=" + context.getTask().getTaskType());
        System.out.println("[AnalysisNeedDetector] skill=" + context.getTask().getSkill());

        List<EvidenceWindow> windows = context.getEvidenceWindows();
        List<PatternCandidate> patterns = context.getPatterns();
        System.out.println("[AnalysisNeedDetector] evidenceCount=" + windows.size()
                + ", patternCount=" + patterns.size());

        Set<String> validEvidenceIds = new HashSet<>();
        for (EvidenceWindow w : windows) validEvidenceIds.add(w.getEvidenceId());
        Set<String> validPatternIds = new HashSet<>();
        for (PatternCandidate p : patterns) validPatternIds.add(p.getPatternId());

        Map<NeedType, Accumulator> accum = new EnumMap<>(NeedType.class);

        for (EvidenceWindow w : windows) {
            for (EvidenceType t : w.getTypes()) {
                for (NeedType nt : evidenceToNeed(t)) {
                    NeedPriority p = boostByLevel(basePriority(nt), w.getLevel());
                    Accumulator a = accum.computeIfAbsent(nt, k -> new Accumulator());
                    a.addEvidence(w.getEvidenceId(), p);
                }
            }
        }

        for (PatternCandidate p : patterns) {
            for (NeedType nt : patternToNeed(p.getType())) {
                Accumulator a = accum.computeIfAbsent(nt, k -> new Accumulator());
                a.addPattern(p.getPatternId(), basePriority(nt));
            }
        }

        if (accum.isEmpty()) {
            Accumulator a = new Accumulator();
            a.reason = "当前 AnalysisContext 中没有足够的 EvidenceWindow / PatternCandidate，无法形成明确分析方向。";
            a.priority = NeedPriority.LOW;
            accum.put(NeedType.UNKNOWN_OR_INSUFFICIENT_EVIDENCE, a);
        }

        List<AnalysisNeed> result = new ArrayList<>();
        for (Map.Entry<NeedType, Accumulator> e : accum.entrySet()) {
            Accumulator a = e.getValue();
            if (a.reason == null) {
                a.reason = defaultReason(e.getKey(), a.evidenceIds.size(), a.patternIds.size());
            }
            // ID 完整性校验
            for (String eid : a.evidenceIds) {
                if (!validEvidenceIds.contains(eid)) {
                    throw new IllegalArgumentException("[AnalysisNeedDetector][ERROR] unknown evidenceId=" + eid);
                }
            }
            for (String pid : a.patternIds) {
                if (!validPatternIds.contains(pid)) {
                    throw new IllegalArgumentException("[AnalysisNeedDetector][ERROR] unknown patternId=" + pid);
                }
            }
            AnalysisNeed need = new AnalysisNeed(e.getKey(), a.priority, a.reason,
                    new ArrayList<>(a.evidenceIds), new ArrayList<>(a.patternIds));
            result.add(need);
            System.out.println("[AnalysisNeedDetector] need created: type=" + need.getNeedType()
                    + ", priority=" + need.getPriority()
                    + ", evidenceIds=" + need.getEvidenceIds().size()
                    + ", patternIds=" + need.getPatternIds().size());
        }

        result.sort(Comparator
                .comparingInt((AnalysisNeed n) -> n.getPriority().ordinal() * -1)
                .thenComparing(n -> n.getNeedType().name()));

        System.out.println("[AnalysisNeedDetector] Analysis needs generated: count=" + result.size());
        return result;
    }

    private static List<NeedType> evidenceToNeed(EvidenceType t) {
        switch (t) {
            case RELATIONSHIP_STATUS_CHANGE: return List.of(NeedType.RELATIONSHIP_STATUS_CHANGE, NeedType.RELATIONSHIP_CHANGE);
            case RELATIONSHIP_EXPRESSION:    return List.of(NeedType.RELATIONSHIP_EXPRESSION);
            case CONFLICT:                   return List.of(NeedType.CONFLICT);
            case REPAIR:                     return List.of(NeedType.REPAIR);
            case BOUNDARY_OR_REJECTION:      return List.of(NeedType.BOUNDARY_OR_REJECTION);
            case EXPLICIT_EMOTION:           return List.of(NeedType.EMOTION);
            case IMPORTANT_ACTION:           return List.of(NeedType.RELATIONSHIP_EVENT);
            case INTERACTION_PATTERN_CHANGE:return List.of(NeedType.INTERACTION_INITIATIVE, NeedType.RESPONSE_PATTERN);
            case PERSISTENT_BEHAVIOR:        return List.of(NeedType.PERSISTENT_BEHAVIOR);
            case REPRESENTATIVE_POSITIVE_INTERACTION: return List.of(NeedType.POSITIVE_INTERACTION);
            default: return List.of();
        }
    }

    private static List<NeedType> patternToNeed(String typeName) {
        if (typeName == null) return List.of();
        switch (typeName) {
            case "PERSISTENT_REJECTION":        return List.of(NeedType.PERSISTENT_BEHAVIOR, NeedType.BOUNDARY_OR_REJECTION);
            case "REPAIR_AFTER_CONFLICT":       return List.of(NeedType.REPAIR);
            case "POSITIVE_INTERACTION_PATTERN":return List.of(NeedType.POSITIVE_INTERACTION);
            case "INTERACTION_INITIATIVE_CHANGE": return List.of(NeedType.INTERACTION_INITIATIVE);
            case "RESPONSE_TIME_CHANGE":        return List.of(NeedType.RESPONSE_PATTERN);
            case "SESSION_FREQUENCY_CHANGE":    return List.of(NeedType.SESSION_FREQUENCY);
            case "MESSAGE_FREQUENCY_CHANGE":    return List.of(NeedType.SESSION_FREQUENCY);
            case "RELATIONSHIP_EVENT_CLUSTER": return List.of(NeedType.RELATIONSHIP_EVENT);
            case "REPEATED_BEHAVIOR":           return List.of(NeedType.PERSISTENT_BEHAVIOR);
            default: return List.of();
        }
    }

    private static NeedPriority basePriority(NeedType nt) {
        switch (nt) {
            case RELATIONSHIP_STATUS_CHANGE:
            case RELATIONSHIP_EXPRESSION:
            case CONFLICT:
            case REPAIR:
            case BOUNDARY_OR_REJECTION:
            case PERSISTENT_BEHAVIOR:
            case RELATIONSHIP_CHANGE:
            case RELATIONSHIP_EVENT:
            case USER_BEHAVIOR:
                return NeedPriority.HIGH;
            case EMOTION:
            case INTERACTION_INITIATIVE:
            case RESPONSE_PATTERN:
            case SESSION_FREQUENCY:
            case POSITIVE_INTERACTION:
                return NeedPriority.NORMAL;
            default:
                return NeedPriority.LOW;
        }
    }

    private static NeedPriority boostByLevel(NeedPriority base, EvidenceLevel level) {
        if (level == EvidenceLevel.S || level == EvidenceLevel.A) {
            if (base == NeedPriority.LOW) return NeedPriority.NORMAL;
            if (base == NeedPriority.NORMAL) return NeedPriority.HIGH;
        }
        return base;
    }

    private static String defaultReason(NeedType nt, int evidenceCount, int patternCount) {
        StringBuilder sb = new StringBuilder("触发于 ").append(evidenceCount).append(" 个 EvidenceWindow");
        if (patternCount > 0) sb.append(" 与 ").append(patternCount).append(" 个 PatternCandidate");
        sb.append("；NeedType=").append(nt);
        return sb.toString();
    }

    private static final class Accumulator {
        final Set<String> evidenceIds = new HashSet<>();
        final Set<String> patternIds = new HashSet<>();
        NeedPriority priority = NeedPriority.LOW;
        String reason;

        void addEvidence(String eid, NeedPriority p) {
            evidenceIds.add(eid);
            if (p.ordinal() < priority.ordinal()) priority = p;
        }

        void addPattern(String pid, NeedPriority p) {
            patternIds.add(pid);
            if (p.ordinal() < priority.ordinal()) priority = p;
        }
    }
}
