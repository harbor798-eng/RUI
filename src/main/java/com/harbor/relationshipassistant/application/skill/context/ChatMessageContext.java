package com.harbor.relationshipassistant.application.skill.context;

import java.time.LocalDateTime;

public final class ChatMessageContext {
    private final Long id;
    private final MessageSender sender;
    private final String content;
    private final LocalDateTime time;

    public ChatMessageContext(Long id, MessageSender sender, String content, LocalDateTime time) {
        this.id = id;
        this.sender = sender;
        this.content = content;
        this.time = time;
    }

    public Long getId() { return id; }
    public MessageSender getSender() { return sender; }
    public String getContent() { return content; }
    public LocalDateTime getTime() { return time; }
}
