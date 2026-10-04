package com.harbor.relationshipassistant.domain.chat;

import java.time.LocalDateTime;

/**
 * 聊天消息修改历史（chat_message_revision 行，阶段2）。
 * 保存“修改前”状态；operation 标记操作类型（EDIT/ADD/DELETE/RESTORE）。
 */
public class ChatMessageRevision {

    private Long id;
    private Long messageId;
    private String content;        // 修改前内容
    private SenderType senderType; // 修改前发送者
    private MessageType messageType; // 修改前类型
    private LocalDateTime messageTime; // 修改前消息时间（V2 新增）
    private String operation;      // EDIT/ADD/DELETE/RESTORE（V2 新增）
    private String editedBy;
    private LocalDateTime createdAt; // 修改时间

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public SenderType getSenderType() { return senderType; }
    public void setSenderType(SenderType senderType) { this.senderType = senderType; }
    public MessageType getMessageType() { return messageType; }
    public void setMessageType(MessageType messageType) { this.messageType = messageType; }
    public LocalDateTime getMessageTime() { return messageTime; }
    public void setMessageTime(LocalDateTime messageTime) { this.messageTime = messageTime; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getEditedBy() { return editedBy; }
    public void setEditedBy(String editedBy) { this.editedBy = editedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
