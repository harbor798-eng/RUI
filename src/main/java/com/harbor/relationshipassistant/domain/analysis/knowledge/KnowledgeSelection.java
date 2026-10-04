package com.harbor.relationshipassistant.domain.analysis.knowledge;

import java.util.Objects;

/**
 * 路由选中的一条知识候选。relevance 仅用于知识排序，<b>不是</b>关系评分。
 */
public final class KnowledgeSelection {

    public enum RiskLevel { LOW, MEDIUM, HIGH }

    private final String knowledgeId;
    private final int relevance;
    private final String reason;
    private final RiskLevel riskLevel;

    public KnowledgeSelection(String knowledgeId, int relevance, String reason, RiskLevel riskLevel) {
        this.knowledgeId = Objects.requireNonNull(knowledgeId, "knowledgeId");
        this.relevance = relevance;
        this.reason = reason;
        this.riskLevel = riskLevel == null ? RiskLevel.LOW : riskLevel;
    }

    public String getKnowledgeId() { return knowledgeId; }
    public int getRelevance() { return relevance; }
    public String getReason() { return reason; }
    public RiskLevel getRiskLevel() { return riskLevel; }
}
