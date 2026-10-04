package com.harbor.relationshipassistant.domain.analysis;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 描述本次 Context 中实际数据情况的元数据。
 * <p>
 * {@code actualDataStart/End} 表示数据库中真实可获得的聊天数据范围，
 * 不是用户在 {@link AnalysisRange} 中选择的范围。
 * 如果用户选了 ALL 但库中只有 2026-01-01 之后的数据，
 * 则 {@code dataComplete=false}。
 * </p>
 */
public final class AnalysisMetadata {

    private final LocalDateTime generatedAt;
    private final LocalDateTime actualDataStart;
    private final LocalDateTime actualDataEnd;
    private final long totalMessageCount;
    private final long analyzedMessageCount;
    private final boolean dataComplete;

    public AnalysisMetadata(LocalDateTime generatedAt,
                            LocalDateTime actualDataStart,
                            LocalDateTime actualDataEnd,
                            long totalMessageCount,
                            long analyzedMessageCount,
                            boolean dataComplete) {
        this.generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
        this.actualDataStart = actualDataStart;
        this.actualDataEnd = actualDataEnd;
        this.totalMessageCount = totalMessageCount;
        this.analyzedMessageCount = analyzedMessageCount;
        this.dataComplete = dataComplete;
    }

    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public LocalDateTime getActualDataStart() { return actualDataStart; }
    public LocalDateTime getActualDataEnd() { return actualDataEnd; }
    public long getTotalMessageCount() { return totalMessageCount; }
    public long getAnalyzedMessageCount() { return analyzedMessageCount; }
    public boolean isDataComplete() { return dataComplete; }
}
