package com.harbor.relationshipassistant.domain.analysis.interaction;

import com.harbor.relationshipassistant.domain.analysis.AnalysisMessage;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 一段连续的互动聊天过程（运行时对象）。
 * <p>
 * Session 只是按时间连续性对消息分组，<b>不是</b>关系判断、不是回复判断、
 * 不是 AI 结论。本批只持有时间范围 + 消息集合；initiator / response 等
 * 留到后续 InteractionStatistics 批次。
 * </p>
 */
public final class ConversationSession {

    private final String sessionId;
    private final long relationshipId;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final List<AnalysisMessage> messages;

    public ConversationSession(String sessionId,
                               long relationshipId,
                               LocalDateTime startTime,
                               LocalDateTime endTime,
                               List<AnalysisMessage> messages) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.relationshipId = relationshipId;
        this.startTime = Objects.requireNonNull(startTime, "startTime");
        this.endTime = Objects.requireNonNull(endTime, "endTime");
        this.messages = (messages == null || messages.isEmpty())
                ? Collections.emptyList()
                : Collections.unmodifiableList(messages);
    }

    public String getSessionId() { return sessionId; }
    public long getRelationshipId() { return relationshipId; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public List<AnalysisMessage> getMessages() { return messages; }
}
