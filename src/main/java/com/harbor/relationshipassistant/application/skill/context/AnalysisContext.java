package com.harbor.relationshipassistant.application.skill.context;

import java.util.List;

/**
 * AI 执行一次任务时可以看到的业务上下文。
 * 全部为 DTO，不暴露 JPA Entity / Repository。
 */
public final class AnalysisContext {
    private final RelationshipContext relationship;
    private final ParticipantContext participants;
    private final List<ChatMessageContext> chatMessages;
    private final ChatMessageContext currentMessage;
    private final List<UserConfirmedFact> userConfirmedFacts;
    private final AnalysisTimeRange timeRange;

    public AnalysisContext(RelationshipContext relationship,
                           ParticipantContext participants,
                           List<ChatMessageContext> chatMessages,
                           ChatMessageContext currentMessage,
                           List<UserConfirmedFact> userConfirmedFacts,
                           AnalysisTimeRange timeRange) {
        this.relationship = relationship;
        this.participants = participants;
        this.chatMessages = chatMessages == null ? List.of() : List.copyOf(chatMessages);
        this.currentMessage = currentMessage;
        this.userConfirmedFacts = userConfirmedFacts == null ? List.of() : List.copyOf(userConfirmedFacts);
        this.timeRange = timeRange;
    }

    public RelationshipContext getRelationship() { return relationship; }
    public ParticipantContext getParticipants() { return participants; }
    public List<ChatMessageContext> getChatMessages() { return chatMessages; }
    public ChatMessageContext getCurrentMessage() { return currentMessage; }
    public List<UserConfirmedFact> getUserConfirmedFacts() { return userConfirmedFacts; }
    public AnalysisTimeRange getTimeRange() { return timeRange; }
}
