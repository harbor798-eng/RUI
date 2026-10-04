package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.domain.analysis.AnalysisContext;
import com.harbor.relationshipassistant.domain.analysis.EvidenceType;
import com.harbor.relationshipassistant.domain.analysis.EvidenceWindow;
import com.harbor.relationshipassistant.domain.analysis.Skill;
import com.harbor.relationshipassistant.domain.analysis.need.AnalysisNeed;
import com.harbor.relationshipassistant.domain.analysis.need.NeedType;
import com.harbor.relationshipassistant.domain.analysis.knowledge.KnowledgeDefinition;
import com.harbor.relationshipassistant.domain.analysis.knowledge.KnowledgeRegistry;
import com.harbor.relationshipassistant.domain.analysis.knowledge.KnowledgeSelection;
import com.harbor.relationshipassistant.domain.analysis.knowledge.KnowledgeSelection.RiskLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 根据 AnalysisNeed 从 KnowledgeRegistry 中选出 0~3 条最相关知识。
 * <p>
 * 纯本地确定性规则；不调用 LLM，不读 MD 正文，不做 Embedding/RAG。
 * relevance 仅用于知识排序，不是关系评分。
 * </p>
 */
public class KnowledgeRouter {

    private static final int MIN_RELEVANCE = 40;
    private static final int MAX_SELECT = 3;

    public List<KnowledgeSelection> route(AnalysisContext context,
                                          List<AnalysisNeed> needs,
                                          Skill currentSkill,
                                          KnowledgeRegistry registry) {
        System.out.println("[KnowledgeRouter] Start routing knowledge");
        if (context == null || needs == null || registry == null) {
            throw new IllegalArgumentException("[KnowledgeRouter][ERROR] context/needs/registry must not be null");
        }
        System.out.println("[KnowledgeRouter] taskType=" + context.getTask().getTaskType());
        System.out.println("[KnowledgeRouter] skill=" + currentSkill);
        System.out.println("[KnowledgeRouter] needCount=" + needs.size()
                + ", knowledgeCount=" + registry.size());

        if (needs.isEmpty() || registry.size() == 0) {
            System.out.println("[KnowledgeRouter] selected knowledge count=0");
            return new ArrayList<>();
        }

        Set<NeedType> needTypes = new HashSet<>();
        Set<EvidenceType> evidenceTypes = new HashSet<>();
        for (AnalysisNeed n : needs) needTypes.add(n.getNeedType());
        for (AnalysisNeed n : needs) {
            for (EvidenceWindow w : context.getEvidenceWindows()) {
                if (n.getEvidenceIds().contains(w.getEvidenceId())) {
                    evidenceTypes.addAll(w.getTypes());
                }
            }
        }

        List<Scored> scored = new ArrayList<>();
        for (KnowledgeDefinition kd : registry.all()) {
            if (!skillAllowed(kd, currentSkill)) continue;
            Score score = score(kd, needTypes, evidenceTypes, currentSkill);
            if (score.total < MIN_RELEVANCE) continue;
            scored.add(new Scored(kd.getKnowledgeId(), score.total, score.reason, score.riskLevel));
        }

        scored.sort(Comparator.comparingInt((Scored s) -> s.relevance).reversed()
                .thenComparing(s -> s.knowledgeId));

        List<KnowledgeSelection> result = new ArrayList<>();
        for (Scored s : scored) {
            if (result.size() >= MAX_SELECT) break;
            KnowledgeSelection sel = new KnowledgeSelection(s.knowledgeId, s.relevance, s.reason, s.riskLevel);
            result.add(sel);
            System.out.println("[KnowledgeRouter] selected: knowledgeId=" + s.knowledgeId
                    + ", relevance=" + s.relevance + ", reason=" + s.reason);
        }
        System.out.println("[KnowledgeRouter] selected knowledge count=" + result.size());
        return result;
    }

    private static boolean skillAllowed(KnowledgeDefinition kd, Skill current) {
        Set<Skill> sup = kd.getSupportedSkills();
        if (sup == null || sup.isEmpty()) return true; // GLOBAL
        return sup.contains(current);
    }

    private static Score score(KnowledgeDefinition kd, Set<NeedType> needs,
                               Set<EvidenceType> evs, Skill current) {
        int total = 0;
        List<String> reasons = new ArrayList<>();

        Set<NeedType> matchedNeeds = new HashSet<>(kd.getNeedTypes());
        matchedNeeds.retainAll(needs);
        if (!matchedNeeds.isEmpty()) {
            total += Math.min(30, 15 * matchedNeeds.size());
            reasons.add("匹配分析需求 " + matchedNeeds);
        }

        Set<EvidenceType> matchedEvs = new HashSet<>(kd.getEvidenceTypes());
        matchedEvs.retainAll(evs);
        if (!matchedEvs.isEmpty()) {
            total += Math.min(20, 10 * matchedEvs.size());
            reasons.add("匹配证据类型 " + matchedEvs);
        }

        if (kd.getSupportedSkills().isEmpty() || kd.getSupportedSkills().contains(current)) {
            total += 15;
        }

        total += 10; // V1：所有已通过 Skill 过滤的知识给基础上下文分

        RiskLevel risk = RiskLevel.LOW;
        if (!kd.getRiskTags().isEmpty()) {
            total -= 20;
            risk = kd.getRiskTags().contains("MANIPULATION") ? RiskLevel.HIGH : RiskLevel.MEDIUM;
            reasons.add("含风险标签 " + kd.getRiskTags());
        }

        Score s = new Score();
        s.total = Math.max(0, Math.min(100, total));
        s.reason = reasons.isEmpty() ? "相关性匹配" : String.join("；", reasons);
        s.riskLevel = risk;
        return s;
    }

    private static final class Score {
        int total;
        String reason;
        RiskLevel riskLevel;
    }

    private static final class Scored {
        final String knowledgeId;
        final int relevance;
        final String reason;
        final RiskLevel riskLevel;
        Scored(String id, int r, String reason, RiskLevel risk) {
            this.knowledgeId = id; this.relevance = r; this.reason = reason; this.riskLevel = risk;
        }
    }
}
