package com.harbor.relationshipassistant.application.skill.context;

import java.time.LocalDateTime;

/**
 * 用户明确确认过的事实。与 AI 推测严格分离。
 */
public final class UserConfirmedFact {
    private final String content;
    private final LocalDateTime confirmedAt;
    private final String source;

    public UserConfirmedFact(String content, LocalDateTime confirmedAt, String source) {
        this.content = content;
        this.confirmedAt = confirmedAt;
        this.source = source;
    }

    public String getContent() { return content; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public String getSource() { return source; }
}
