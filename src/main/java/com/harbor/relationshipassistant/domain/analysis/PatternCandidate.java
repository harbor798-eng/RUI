package com.harbor.relationshipassistant.domain.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 程序或规则发现的潜在行为模式。
 * <p>
 * 这是候选，不是最终 AI 结论；不包含用户确认状态。
 * evidenceIds 引用 {@link EvidenceWindow#getEvidenceId()}。
 * </p>
 */
public final class PatternCandidate {

    private final String patternId;
    private final String type;
    private final String description;
    private final List<String> evidenceIds;
    private final double confidence;

    public PatternCandidate(String patternId,
                            String type,
                            String description,
                            List<String> evidenceIds,
                            double confidence) {
        this.patternId = Objects.requireNonNull(patternId, "patternId");
        this.type = Objects.requireNonNull(type, "type");
        this.description = description;
        this.evidenceIds = (evidenceIds == null || evidenceIds.isEmpty())
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(evidenceIds));
        this.confidence = confidence;
    }

    public String getPatternId() { return patternId; }
    public String getType() { return type; }
    public String getDescription() { return description; }
    public List<String> getEvidenceIds() { return evidenceIds; }

    /** 程序规则对"该模式存在"的确定程度（0~1）；不是关系概率。 */
    public double getConfidence() { return confidence; }
}
