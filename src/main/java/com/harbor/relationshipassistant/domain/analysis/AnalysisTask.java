package com.harbor.relationshipassistant.domain.analysis;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * JEVE 一次 AI 分析任务的领域模型。
 * <p>
 * 这是纯内存 Domain Model：第一批不绑定 JDBC、不加 JPA 注解。
 * 与数据库表 {@code analysis_task} 不做 1:1 映射——后者的 analysis_type
 * （OBJECTIVE_DATA / CHAT_QUALITY 等）是历史指标分类，
 * 本类使用 {@link AnalysisTaskType} 描述产品入口。
 * </p>
 */
public final class AnalysisTask {

    private final Long id;
    private final long relationshipId;
    private final AnalysisTaskType taskType;
    private final AnalysisRange range;
    private final Skill skill;
    private final OutputMode outputMode;
    private final LocalDateTime createdAt;

    public AnalysisTask(Long id,
                        long relationshipId,
                        AnalysisTaskType taskType,
                        AnalysisRange range,
                        Skill skill,
                        OutputMode outputMode,
                        LocalDateTime createdAt) {
        this.id = id;
        this.relationshipId = relationshipId;
        this.taskType = Objects.requireNonNull(taskType, "taskType");
        this.range = Objects.requireNonNull(range, "range");
        this.skill = Objects.requireNonNull(skill, "skill");
        this.outputMode = Objects.requireNonNull(outputMode, "outputMode");
        this.createdAt = (createdAt == null) ? LocalDateTime.now() : createdAt;
    }

    public Long getId() { return id; }
    public long getRelationshipId() { return relationshipId; }
    public AnalysisTaskType getTaskType() { return taskType; }
    public AnalysisRange getRange() { return range; }
    public Skill getSkill() { return skill; }
    public OutputMode getOutputMode() { return outputMode; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
