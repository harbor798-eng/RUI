package com.harbor.relationshipassistant.domain.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 对当前行为/关系现象的一种可能解释。
 * <p>
 * 例："对方近期可能因为工作压力增加而减少主动互动。"
 * </p>
 * <p>
 * {@link #estimatedProbability} 是 AI 的主观估计（0~1），
 * <b>不是</b>科学统计概率，也不是任何形式的关系评分/匹配度/爱意分数。
 * </p>
 */
public final class Possibility {

    private final String content;
    private final Double estimatedProbability;
    private final List<String> evidenceIds;
    private final List<String> counterEvidenceIds;
    private final PossibilityStatus status;

    public Possibility(String content,
                       Double estimatedProbability,
                       List<String> evidenceIds,
                       List<String> counterEvidenceIds,
                       PossibilityStatus status) {
        this.content = Objects.requireNonNull(content, "content");
        this.estimatedProbability = estimatedProbability;
        this.evidenceIds = immutable(evidenceIds);
        this.counterEvidenceIds = immutable(counterEvidenceIds);
        this.status = (status == null) ? PossibilityStatus.UNCONFIRMED : status;
    }

    private static List<String> immutable(List<String> src) {
        if (src == null || src.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(src));
    }

    public String getContent() { return content; }

    /** AI 对该可能性的估计值（0~1），可能为 null（AI 未给出概率）。 */
    public Double getEstimatedProbability() { return estimatedProbability; }

    public List<String> getEvidenceIds() { return evidenceIds; }
    public List<String> getCounterEvidenceIds() { return counterEvidenceIds; }
    public PossibilityStatus getStatus() { return status; }
}
