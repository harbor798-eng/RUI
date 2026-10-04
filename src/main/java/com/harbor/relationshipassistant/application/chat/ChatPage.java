package com.harbor.relationshipassistant.application.chat;

import com.harbor.relationshipassistant.domain.chat.ChatMessage;

import java.util.List;

/** 一页聊天记录（ViewModel 直接消费）。 */
public class ChatPage {

    private final long relationshipId;
    private final int total;
    private final int offset;
    private final int limit;
    private final List<ChatMessage> messages;

    public ChatPage(long relationshipId, int total, int offset, int limit, List<ChatMessage> messages) {
        this.relationshipId = relationshipId;
        this.total = total;
        this.offset = offset;
        this.limit = limit;
        this.messages = messages;
    }

    public long getRelationshipId() { return relationshipId; }
    public int getTotal() { return total; }
    public int getOffset() { return offset; }
    public int getLimit() { return limit; }
    public List<ChatMessage> getMessages() { return messages; }
}
