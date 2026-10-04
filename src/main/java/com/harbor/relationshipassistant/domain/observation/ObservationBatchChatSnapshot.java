package com.harbor.relationshipassistant.domain.observation;

import java.time.LocalDateTime;

/** Batch 创建时复制的聊天行快照；sourceChatMessageId 仅追踪用。 */
public class ObservationBatchChatSnapshot {
    private Long id;
    private Long batchId;
    private Long sourceChatMessageId;
    private String senderType;
    private String messageType;
    private String content;
    private LocalDateTime messageTime;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long v) { this.batchId = v; }
    public Long getSourceChatMessageId() { return sourceChatMessageId; }
    public void setSourceChatMessageId(Long v) { this.sourceChatMessageId = v; }
    public String getSenderType() { return senderType; }
    public void setSenderType(String v) { this.senderType = v; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String v) { this.messageType = v; }
    public String getContent() { return content; }
    public void setContent(String v) { this.content = v; }
    public LocalDateTime getMessageTime() { return messageTime; }
    public void setMessageTime(LocalDateTime v) { this.messageTime = v; }
}
