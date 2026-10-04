package com.harbor.relationshipassistant.domain.analysis.report;

import com.harbor.relationshipassistant.domain.analysis.AnalysisRange;
import com.harbor.relationshipassistant.domain.analysis.AnalysisTaskType;
import com.harbor.relationshipassistant.domain.analysis.Skill;

import java.time.LocalDateTime;
import java.util.Objects;

/** 报告元数据：严格区分"用户选择的分析范围"与"程序实际拥有的数据范围"。 */
public final class ReportMetadata {

    private final String reportId;
    private final long relationshipId;
    private final AnalysisTaskType taskType;
    private final Skill skill;
    private final AnalysisRange analysisRange;
    private final LocalDateTime analysisStart;
    private final LocalDateTime analysisEnd;
    private final LocalDateTime actualDataStart;
    private final LocalDateTime actualDataEnd;
    private final boolean dataComplete;
    private final long messageCount;
    private final LocalDateTime generatedAt;

    public ReportMetadata(String reportId, long relationshipId, AnalysisTaskType taskType,
                          Skill skill, AnalysisRange analysisRange,
                          LocalDateTime analysisStart, LocalDateTime analysisEnd,
                          LocalDateTime actualDataStart, LocalDateTime actualDataEnd,
                          boolean dataComplete, long messageCount, LocalDateTime generatedAt) {
        this.reportId = Objects.requireNonNull(reportId);
        this.relationshipId = relationshipId;
        this.taskType = Objects.requireNonNull(taskType);
        this.skill = skill;
        this.analysisRange = Objects.requireNonNull(analysisRange);
        this.analysisStart = analysisStart;
        this.analysisEnd = analysisEnd;
        this.actualDataStart = actualDataStart;
        this.actualDataEnd = actualDataEnd;
        this.dataComplete = dataComplete;
        this.messageCount = messageCount;
        this.generatedAt = Objects.requireNonNull(generatedAt);
    }

    public String getReportId() { return reportId; }
    public long getRelationshipId() { return relationshipId; }
    public AnalysisTaskType getTaskType() { return taskType; }
    public Skill getSkill() { return skill; }
    public AnalysisRange getAnalysisRange() { return analysisRange; }
    public LocalDateTime getAnalysisStart() { return analysisStart; }
    public LocalDateTime getAnalysisEnd() { return analysisEnd; }
    public LocalDateTime getActualDataStart() { return actualDataStart; }
    public LocalDateTime getActualDataEnd() { return actualDataEnd; }
    public boolean isDataComplete() { return dataComplete; }
    public long getMessageCount() { return messageCount; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
}
