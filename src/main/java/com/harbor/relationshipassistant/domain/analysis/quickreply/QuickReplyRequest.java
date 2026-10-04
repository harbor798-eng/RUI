package com.harbor.relationshipassistant.domain.analysis.quickreply;

import com.harbor.relationshipassistant.domain.analysis.OutputMode;
import com.harbor.relationshipassistant.domain.analysis.Skill;

/** Quick Reply 请求（UI → Service 的不可变 DTO，不持久化）。 */
public record QuickReplyRequest(long relationshipId,
                                Long currentMessageId,
                                Skill skill,
                                OutputMode outputMode,
                                int contextLimit) {

    public QuickReplyRequest {
        if (relationshipId <= 0) throw new IllegalArgumentException("relationshipId must be positive");
        if (skill == null) skill = Skill.NONE;
        if (outputMode == null) outputMode = OutputMode.NORMAL;
        if (contextLimit <= 0 || contextLimit > 50) contextLimit = 15;
    }

    public static QuickReplyRequest of(long relationshipId, Long currentMessageId) {
        return new QuickReplyRequest(relationshipId, currentMessageId, Skill.NONE, OutputMode.NORMAL, 15);
    }
}
