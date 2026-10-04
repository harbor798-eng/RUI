package com.harbor.relationshipassistant.application.chat;

import com.harbor.relationshipassistant.domain.chat.ChatMessageRevision;
import com.harbor.relationshipassistant.domain.chat.MessageType;
import com.harbor.relationshipassistant.domain.chat.SenderType;

import java.time.LocalDateTime;

/** 修改历史展示 DTO（对应 chat_message_revision 行，展示用）。 */
public class ChatMessageRevisionView {

    private final Long revisionId;
    private final Long messageId;
    private final String beforeContent;
    private final SenderType beforeSender;
    private final MessageType beforeType;
    private final LocalDateTime beforeTime;
    private final String operation;   // EDIT/ADD/DELETE/RESTORE
    private final String editedBy;
    private final LocalDateTime modifiedAt;

    public ChatMessageRevisionView(ChatMessageRevision r) {
        this.revisionId = r.getId();
        this.messageId = r.getMessageId();
        this.beforeContent = r.getContent();
        this.beforeSender = r.getSenderType();
        this.beforeType = r.getMessageType();
        this.beforeTime = r.getMessageTime();
        this.operation = r.getOperation();
        this.editedBy = r.getEditedBy();
        this.modifiedAt = r.getCreatedAt();
    }

    public Long getRevisionId() { return revisionId; }
    public Long getMessageId() { return messageId; }
    public String getBeforeContent() { return beforeContent; }
    public SenderType getBeforeSender() { return beforeSender; }
    public MessageType getBeforeType() { return beforeType; }
    public LocalDateTime getBeforeTime() { return beforeTime; }
    public String getOperation() { return operation; }
    public String getEditedBy() { return editedBy; }
    public LocalDateTime getModifiedAt() { return modifiedAt; }
}
