package com.harbor.relationshipassistant.domain.analysis;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 描述这份 {@link AnalysisResult} 是何时、针对哪个任务/关系生成的。
 * <p>
 * 这是运行时元数据，<b>不是</b>数据库表 {@code analysis_result} 的 Entity。
 * </p>
 */
public final class AnalysisResultMetadata {

    private final Long taskId;
    private final long relationshipId;
    private final LocalDateTime generatedAt;

    public AnalysisResultMetadata(Long taskId, long relationshipId, LocalDateTime generatedAt) {
        this.taskId = taskId;
        this.relationshipId = relationshipId;
        this.generatedAt = (generatedAt == null) ? LocalDateTime.now() : generatedAt;
    }

    public Long getTaskId() { return taskId; }
    public long getRelationshipId() { return relationshipId; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
}
