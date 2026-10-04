package com.harbor.relationshipassistant.domain.observation;

import java.time.LocalDateTime;

/** 一条主体观察（AI 原始结果永久保留，用户修改以申请形式叠加）。 */
public class Observation {
    private Long id;
    private Long batchId;
    private String subject; // SELF / OTHER / RELATIONSHIP
    private String aiRawText;
    private String userEditedText;
    private String editReason;
    private boolean includeInLongterm;
    private String longtermVersion; // AI_RAW / USER_EDITED / null
    private LocalDateTime editSubmittedAt;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long v) { this.batchId = v; }
    public String getSubject() { return subject; }
    public void setSubject(String v) { this.subject = v; }
    public String getAiRawText() { return aiRawText; }
    public void setAiRawText(String v) { this.aiRawText = v; }
    public String getUserEditedText() { return userEditedText; }
    public void setUserEditedText(String v) { this.userEditedText = v; }
    public String getEditReason() { return editReason; }
    public void setEditReason(String v) { this.editReason = v; }
    public boolean isIncludeInLongterm() { return includeInLongterm; }
    public void setIncludeInLongterm(boolean v) { this.includeInLongterm = v; }
    public String getLongtermVersion() { return longtermVersion; }
    public void setLongtermVersion(String v) { this.longtermVersion = v; }
    public LocalDateTime getEditSubmittedAt() { return editSubmittedAt; }
    public void setEditSubmittedAt(LocalDateTime v) { this.editSubmittedAt = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
