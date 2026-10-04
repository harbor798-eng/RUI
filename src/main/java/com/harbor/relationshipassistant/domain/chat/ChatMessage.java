package com.harbor.relationshipassistant.domain.chat;

import java.time.LocalDateTime;

/**
 * 当前有效聊天消息（技术设计 §13）。
 * sourceContent 保留导入原始值，content 为当前有效值；二者分离，不互相覆盖（PRD §3.2）。
 */
public class ChatMessage {
    private Long id;
    private Long relationshipId;
    private SenderType senderType;
    private MessageType messageType;
    private String content;
    private LocalDateTime messageTime;
    private MessageSourceType sourceType;
    private String sourceMessageId;
    private String sourceHash;
    private String sourceContent;
    private Long sourceAiMessageId;
    private String metadataJson;
    private String status;   // ACTIVE/DELETED（软删除，阶段2）
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRelationshipId() { return relationshipId; }
    public void setRelationshipId(Long relationshipId) { this.relationshipId = relationshipId; }
    public SenderType getSenderType() { return senderType; }
    public void setSenderType(SenderType senderType) { this.senderType = senderType; }
    public MessageType getMessageType() { return messageType; }
    public void setMessageType(MessageType messageType) { this.messageType = messageType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getMessageTime() { return messageTime; }
    public void setMessageTime(LocalDateTime messageTime) { this.messageTime = messageTime; }
    public MessageSourceType getSourceType() { return sourceType; }
    public void setSourceType(MessageSourceType sourceType) { this.sourceType = sourceType; }
    public String getSourceMessageId() { return sourceMessageId; }
    public void setSourceMessageId(String sourceMessageId) { this.sourceMessageId = sourceMessageId; }
    public String getSourceHash() { return sourceHash; }
    public void setSourceHash(String sourceHash) { this.sourceHash = sourceHash; }
    public String getSourceContent() { return sourceContent; }
    public void setSourceContent(String sourceContent) { this.sourceContent = sourceContent; }
    public Long getSourceAiMessageId() { return sourceAiMessageId; }
    public void setSourceAiMessageId(Long sourceAiMessageId) { this.sourceAiMessageId = sourceAiMessageId; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
