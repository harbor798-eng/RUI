package com.harbor.relationshipassistant.domain.analysis;

import com.harbor.relationshipassistant.domain.chat.MessageType;
import com.harbor.relationshipassistant.domain.chat.SenderType;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 已经进入 AI 分析流水线的标准化聊天消息。
 * <p>
 * 这是 AI Analysis Domain Model，<b>不是</b>数据库 Entity，也不是
 * {@link com.harbor.relationshipassistant.domain.chat.ChatMessage} 的替代品。
 * ChatMessage 是数据库/业务层消息；AnalysisMessage 是 Pipeline 专用消息。
 * 两者之间由 {@code ChatProcessor} 转换。
 * </p>
 * <p>
 * 只保留后续分析真正需要的字段；不携带 source_* / metadataJson / status 等
 * 数据库内部字段。
 * </p>
 */
public final class AnalysisMessage {

    private final Long messageId;
    private final Long relationshipId;
    private final SenderType senderType;
    private final MessageType messageType;
    private final LocalDateTime messageTime;
    private final String content;

    public AnalysisMessage(Long messageId,
                           Long relationshipId,
                           SenderType senderType,
                           MessageType messageType,
                           LocalDateTime messageTime,
                           String content) {
        this.messageId = messageId;
        this.relationshipId = relationshipId;
        this.senderType = Objects.requireNonNull(senderType, "senderType");
        this.messageType = Objects.requireNonNull(messageType, "messageType");
        this.messageTime = messageTime;
        this.content = content;
    }

    public Long getMessageId() { return messageId; }
    public Long getRelationshipId() { return relationshipId; }
    public SenderType getSenderType() { return senderType; }
    public MessageType getMessageType() { return messageType; }
    public LocalDateTime getMessageTime() { return messageTime; }
    public String getContent() { return content; }
}
