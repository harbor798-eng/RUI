package com.harbor.relationshipassistant.domain.analysis;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * AI 输入侧证据窗口。
 * <p>
 * 表示"本次分析中，AI 应该重点看到的一段聊天切片"。
 * <b>不是</b>数据库表 {@code observation_evidence}——后者是 AI 输出后回写的证据快照。
 * 两者职责不同，不要合并。
 * </p>
 * <p>
 * 第一批只建立数据结构；不实现自动打分、自动抽取、SignalDetector。
 * </p>
 */
public final class EvidenceWindow {

    private final String evidenceId;
    private final long relationshipId;
    private final Set<EvidenceType> types;
    private final double importance;
    private final EvidenceLevel level;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final List<Long> messageIds;
    private final List<Long> triggerMessageIds;
    private final List<EvidenceSignal> signals;

    public EvidenceWindow(String evidenceId,
                          long relationshipId,
                          Set<EvidenceType> types,
                          double importance,
                          EvidenceLevel level,
                          LocalDateTime startTime,
                          LocalDateTime endTime,
                          List<Long> messageIds,
                          List<Long> triggerMessageIds,
                          List<EvidenceSignal> signals) {
        this.evidenceId = Objects.requireNonNull(evidenceId, "evidenceId");
        this.relationshipId = relationshipId;
        this.types = (types == null || types.isEmpty())
                ? Collections.emptySet()
                : Collections.unmodifiableSet(types);
        this.importance = importance;
        this.level = Objects.requireNonNull(level, "level");
        this.startTime = startTime;
        this.endTime = endTime;
        this.messageIds = immutable(messageIds);
        this.triggerMessageIds = immutable(triggerMessageIds);
        this.signals = immutable(signals);
    }

    private static <T> List<T> immutable(List<T> src) {
        if (src == null || src.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(src));
    }

    public String getEvidenceId() { return evidenceId; }
    public long getRelationshipId() { return relationshipId; }
    public Set<EvidenceType> getTypes() { return types; }
    public double getImportance() { return importance; }
    public EvidenceLevel getLevel() { return level; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public List<Long> getMessageIds() { return messageIds; }
    public List<Long> getTriggerMessageIds() { return triggerMessageIds; }
    public List<EvidenceSignal> getSignals() { return signals; }
}
