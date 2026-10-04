package com.harbor.relationshipassistant.domain.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * AI 根据事实和证据形成的观察。
 * <p>
 * 例："最近一周对方回复速度明显下降，且主动发起聊天次数减少。"
 * </p>
 * <p>
 * 注意：本类名为 {@code AnalysisObservation}，故意不叫 {@code Observation}，
 * 以避免与 {@code com.harbor.relationshipassistant.domain.observation.Observation}
 * （数据库持久化的观察实体）重名。
 * </p>
 */
public final class AnalysisObservation {

    private final String content;
    private final List<String> evidenceIds;
    private final double confidence;

    public AnalysisObservation(String content, List<String> evidenceIds, double confidence) {
        this.content = Objects.requireNonNull(content, "content");
        this.evidenceIds = (evidenceIds == null || evidenceIds.isEmpty())
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(evidenceIds));
        this.confidence = confidence;
    }

    public String getContent() { return content; }
    public List<String> getEvidenceIds() { return evidenceIds; }

    /**
     * AI 对该观察判断的置信程度，取值 0~1。
     * <p>
     * 这是 AI 的主观置信度，不是科学统计概率。
     * </p>
     */
    public double getConfidence() { return confidence; }
}
