package com.harbor.relationshipassistant.domain.analysis;

import com.harbor.relationshipassistant.domain.chat.SenderType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * AI 根据聊天证据对某个人当前/阶段性情绪的观察。
 * <p>
 * {@link #person} 复用项目已有 {@link SenderType}（ME / OTHER / SYSTEM）。
 * SYSTEM 不应用作恋爱情绪分析对象。
 * </p>
 * <p>
 * {@link #estimatedProbability} 是 AI 的主观估计值（0~1），
 * <b>不是</b>客观情绪测量结果。
 * </p>
 */
public final class EmotionObservation {

    private final SenderType person;
    private final String emotion;
    private final Double estimatedProbability;
    private final EmotionIntensity intensity;
    private final List<String> evidenceIds;

    public EmotionObservation(SenderType person,
                              String emotion,
                              Double estimatedProbability,
                              EmotionIntensity intensity,
                              List<String> evidenceIds) {
        this.person = Objects.requireNonNull(person, "person");
        this.emotion = Objects.requireNonNull(emotion, "emotion");
        this.estimatedProbability = estimatedProbability;
        this.intensity = Objects.requireNonNull(intensity, "intensity");
        this.evidenceIds = (evidenceIds == null || evidenceIds.isEmpty())
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(evidenceIds));
    }

    public SenderType getPerson() { return person; }
    public String getEmotion() { return emotion; }
    public Double getEstimatedProbability() { return estimatedProbability; }
    public EmotionIntensity getIntensity() { return intensity; }
    public List<String> getEvidenceIds() { return evidenceIds; }
}
