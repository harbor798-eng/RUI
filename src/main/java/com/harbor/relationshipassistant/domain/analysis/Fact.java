package com.harbor.relationshipassistant.domain.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 有证据支持、可以作为事实使用的信息。
 * <p>
 * Fact 不能表达"我觉得对方已经不喜欢你了"——那属于
 * {@link AnalysisObservation} 或 {@link Possibility}。
 * Fact 必须有明确来源（聊天 / 统计 / 用户确认档案）。
 * </p>
 */
public final class Fact {

    private final FactType type;
    private final String content;
    private final List<String> evidenceIds;
    private final List<String> statisticKeys;
    private final String profileField;

    public Fact(FactType type,
                String content,
                List<String> evidenceIds,
                List<String> statisticKeys,
                String profileField) {
        this.type = Objects.requireNonNull(type, "type");
        this.content = Objects.requireNonNull(content, "content");
        this.evidenceIds = immutable(evidenceIds);
        this.statisticKeys = immutable(statisticKeys);
        this.profileField = profileField;
    }

    private static List<String> immutable(List<String> src) {
        if (src == null || src.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(src));
    }

    public FactType getType() { return type; }
    public String getContent() { return content; }
    public List<String> getEvidenceIds() { return evidenceIds; }
    public List<String> getStatisticKeys() { return statisticKeys; }
    public String getProfileField() { return profileField; }
}
