package com.harbor.relationshipassistant.domain.observation;

import java.time.LocalDateTime;
import java.util.List;

/** 一次 AI 长期分析批次。 */
public class ObservationBatch {
    private Long id;
    private Long relationshipId;
    private List<String> targets; // SELF / OTHER / RELATIONSHIP
    private LocalDateTime rangeStart;
    private LocalDateTime rangeEnd;
    private String status;       // RUNNING / DONE / CANCELLED / FAILED / PARTIAL
    private int snapshotChatCount;
    private String contextSnapshotJson;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long elapsedMs;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createdAt;

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String v) { this.errorCode = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { this.errorMessage = v; }

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getRelationshipId() { return relationshipId; }
    public void setRelationshipId(Long v) { this.relationshipId = v; }
    public List<String> getTargets() { return targets; }
    public void setTargets(List<String> v) { this.targets = v; }
    public LocalDateTime getRangeStart() { return rangeStart; }
    public void setRangeStart(LocalDateTime v) { this.rangeStart = v; }
    public LocalDateTime getRangeEnd() { return rangeEnd; }
    public void setRangeEnd(LocalDateTime v) { this.rangeEnd = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public int getSnapshotChatCount() { return snapshotChatCount; }
    public void setSnapshotChatCount(int v) { this.snapshotChatCount = v; }
    public String getContextSnapshotJson() { return contextSnapshotJson; }
    public void setContextSnapshotJson(String v) { this.contextSnapshotJson = v; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime v) { this.startedAt = v; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime v) { this.finishedAt = v; }
    public Long getElapsedMs() { return elapsedMs; }
    public void setElapsedMs(Long v) { this.elapsedMs = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
