package com.harbor.relationshipassistant.domain.analysis.quickreply;

/** 单条 Quick Reply 候选：一个策略对应一条回复文本。 */
public final class QuickReplyCandidate {

    private final QuickReplyStrategy strategy;
    private final String replyText;

    public QuickReplyCandidate(QuickReplyStrategy strategy, String replyText) {
        this.strategy = strategy;
        this.replyText = replyText;
    }

    public QuickReplyStrategy getStrategy() { return strategy; }
    public String getReplyText() { return replyText; }
}
