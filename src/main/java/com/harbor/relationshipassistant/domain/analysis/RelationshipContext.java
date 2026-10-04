package com.harbor.relationshipassistant.domain.analysis;

import com.harbor.relationshipassistant.domain.relationship.RelationshipStage;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 本次分析所针对的关系上下文。
 * <p>
 * 复用现有 {@link RelationshipStage}，不重新定义阶段枚举。
 * </p>
 */
public final class RelationshipContext {

    private final long relationshipId;
    private final RelationshipStage stage;
    private final LocalDateTime relationshipStart;
    private final LocalDateTime analysisStart;
    private final LocalDateTime analysisEnd;

    public RelationshipContext(long relationshipId,
                               RelationshipStage stage,
                               LocalDateTime relationshipStart,
                               LocalDateTime analysisStart,
                               LocalDateTime analysisEnd) {
        this.relationshipId = relationshipId;
        this.stage = Objects.requireNonNull(stage, "stage");
        this.relationshipStart = relationshipStart;
        this.analysisStart = analysisStart;
        this.analysisEnd = analysisEnd;
    }

    public long getRelationshipId() { return relationshipId; }
    public RelationshipStage getStage() { return stage; }
    public LocalDateTime getRelationshipStart() { return relationshipStart; }
    public LocalDateTime getAnalysisStart() { return analysisStart; }
    public LocalDateTime getAnalysisEnd() { return analysisEnd; }
}
