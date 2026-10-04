package com.harbor.relationshipassistant.infrastructure.importer;

import com.harbor.relationshipassistant.domain.chat.SenderType;
import com.harbor.relationshipassistant.domain.chat.MessageType;

import java.time.LocalDateTime;

/** Importer 输出的统一中间消息（技术设计 §9 Unified Message 的导入期形态）。 */
public class ImportedRawMessage {

    private SenderType senderType;
    private MessageType messageType = MessageType.TEXT;
    private String content;
    private LocalDateTime messageTime;
    private String sourceMessageId;
    private String sourceHash;
    /** 导入解析出的原始内容（只读）；用户在 Preview 中修改的是 {@link #content}，不覆盖本字段。 */
    private String sourceContent;
    /** 原始 Unix epoch 秒（审计/核对用；messageTime 已按 Asia/Shanghai 墙钟转换）。 */
    private long rawEpochSeconds;

    public SenderType getSenderType() { return senderType; }
    public void setSenderType(SenderType senderType) { this.senderType = senderType; }
    public MessageType getMessageType() { return messageType; }
    public void setMessageType(MessageType messageType) { this.messageType = messageType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getMessageTime() { return messageTime; }
    public void setMessageTime(LocalDateTime messageTime) { this.messageTime = messageTime; }
    public String getSourceMessageId() { return sourceMessageId; }
    public void setSourceMessageId(String sourceMessageId) { this.sourceMessageId = sourceMessageId; }
    public String getSourceHash() { return sourceHash; }
    public void setSourceHash(String sourceHash) { this.sourceHash = sourceHash; }
    public String getSourceContent() { return sourceContent; }
    public void setSourceContent(String sourceContent) { this.sourceContent = sourceContent; }
    public long getRawEpochSeconds() { return rawEpochSeconds; }
    public void setRawEpochSeconds(long rawEpochSeconds) { this.rawEpochSeconds = rawEpochSeconds; }
}
