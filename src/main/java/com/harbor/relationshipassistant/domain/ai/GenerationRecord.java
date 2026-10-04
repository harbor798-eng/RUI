package com.harbor.relationshipassistant.domain.ai;

import java.time.LocalDateTime;

/**
 * AI 生成行为记录（Phase 3）：一次生成一条。
 * selected 与 final 与 sent 字段均可空：未选 / 选而未发 / 选加改加发 / 选未改加发。
 */
public class GenerationRecord {
    private Long id;
    private Long relationshipId;
    private String requestId;
    private String stage;
    private String provider;
    private String model;
    private int candidateCount;
    private String candidatesSnapshot;
    private String contextSnapshot;
    private String selectedStrategy;
    private Integer selectedCandidateIndex;
    private String selectedOriginalText;
    private LocalDateTime selectedAt;
    private String finalText;
    private boolean modified;
    private Long sentMessageId;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRelationshipId() { return relationshipId; }
    public void setRelationshipId(Long v) { this.relationshipId = v; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String v) { this.requestId = v; }
    public String getStage() { return stage; }
    public void setStage(String v) { this.stage = v; }
    public String getProvider() { return provider; }
    public void setProvider(String v) { this.provider = v; }
    public String getModel() { return model; }
    public void setModel(String v) { this.model = v; }
    public int getCandidateCount() { return candidateCount; }
    public void setCandidateCount(int v) { this.candidateCount = v; }
    public String getCandidatesSnapshot() { return candidatesSnapshot; }
    public void setCandidatesSnapshot(String v) { this.candidatesSnapshot = v; }
    public String getContextSnapshot() { return contextSnapshot; }
    public void setContextSnapshot(String v) { this.contextSnapshot = v; }
    public String getSelectedStrategy() { return selectedStrategy; }
    public void setSelectedStrategy(String v) { this.selectedStrategy = v; }
    public Integer getSelectedCandidateIndex() { return selectedCandidateIndex; }
    public void setSelectedCandidateIndex(Integer v) { this.selectedCandidateIndex = v; }
    public String getSelectedOriginalText() { return selectedOriginalText; }
    public void setSelectedOriginalText(String v) { this.selectedOriginalText = v; }
    public LocalDateTime getSelectedAt() { return selectedAt; }
    public void setSelectedAt(LocalDateTime v) { this.selectedAt = v; }
    public String getFinalText() { return finalText; }
    public void setFinalText(String v) { this.finalText = v; }
    public boolean isModified() { return modified; }
    public void setModified(boolean v) { this.modified = v; }
    public Long getSentMessageId() { return sentMessageId; }
    public void setSentMessageId(Long v) { this.sentMessageId = v; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime v) { this.sentAt = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
