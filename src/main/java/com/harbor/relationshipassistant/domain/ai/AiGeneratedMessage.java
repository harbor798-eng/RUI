package com.harbor.relationshipassistant.domain.ai;

import java.time.LocalDateTime;

/** AI 生成的候选回复原文（技术设计 §21）。 */
public class AiGeneratedMessage {
    private Long id;
    private Long relationshipId;
    private String contextId;
    private ReplyStrategy strategy;
    private String originalText;
    private String provider;
    private String model;
    private AiMessageStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime selectedAt;
    private LocalDateTime sentAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRelationshipId() { return relationshipId; }
    public void setRelationshipId(Long relationshipId) { this.relationshipId = relationshipId; }
    public String getContextId() { return contextId; }
    public void setContextId(String contextId) { this.contextId = contextId; }
    public ReplyStrategy getStrategy() { return strategy; }
    public void setStrategy(ReplyStrategy strategy) { this.strategy = strategy; }
    public String getOriginalText() { return originalText; }
    public void setOriginalText(String originalText) { this.originalText = originalText; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public AiMessageStatus getStatus() { return status; }
    public void setStatus(AiMessageStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getSelectedAt() { return selectedAt; }
    public void setSelectedAt(LocalDateTime selectedAt) { this.selectedAt = selectedAt; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
}
