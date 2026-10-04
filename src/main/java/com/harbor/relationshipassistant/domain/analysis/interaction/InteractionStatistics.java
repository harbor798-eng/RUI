package com.harbor.relationshipassistant.domain.analysis.interaction;

import java.time.Duration;
import java.util.Objects;

/**
 * 一组 {@link ConversationSession} 的客观互动统计结果。
 * <p>
 * 这是程序计算的统计事实，<b>不是</b>关系判断、心理结论或 AI 解释。
 * 所有 Duration 字段在无有效样本时为 {@code null}（"没有数据"与"0 分钟"含义不同）。
 * </p>
 */
public final class InteractionStatistics {

    private final long relationshipId;
    private final int sessionCount;
    private final int meInitiatedSessionCount;
    private final int otherInitiatedSessionCount;
    private final int untimedMessageCount;
    private final int meMessageCount;
    private final int otherMessageCount;
    private final int systemMessageCount;
    private final int meResponseCount;
    private final int otherResponseCount;
    private final Duration meAverageResponseTime;
    private final Duration otherAverageResponseTime;
    private final Duration meMedianResponseTime;
    private final Duration otherMedianResponseTime;
    private final Duration meMinResponseTime;
    private final Duration otherMinResponseTime;
    private final Duration meMaxResponseTime;
    private final Duration otherMaxResponseTime;

    public InteractionStatistics(long relationshipId,
                                  int sessionCount,
                                  int meInitiatedSessionCount,
                                  int otherInitiatedSessionCount,
                                  int untimedMessageCount,
                                  int meMessageCount,
                                  int otherMessageCount,
                                  int systemMessageCount,
                                  int meResponseCount,
                                  int otherResponseCount,
                                  Duration meAverageResponseTime,
                                  Duration otherAverageResponseTime,
                                  Duration meMedianResponseTime,
                                  Duration otherMedianResponseTime,
                                  Duration meMinResponseTime,
                                  Duration otherMinResponseTime,
                                  Duration meMaxResponseTime,
                                  Duration otherMaxResponseTime) {
        this.relationshipId = relationshipId;
        this.sessionCount = sessionCount;
        this.meInitiatedSessionCount = meInitiatedSessionCount;
        this.otherInitiatedSessionCount = otherInitiatedSessionCount;
        this.untimedMessageCount = untimedMessageCount;
        this.meMessageCount = meMessageCount;
        this.otherMessageCount = otherMessageCount;
        this.systemMessageCount = systemMessageCount;
        this.meResponseCount = meResponseCount;
        this.otherResponseCount = otherResponseCount;
        this.meAverageResponseTime = meAverageResponseTime;
        this.otherAverageResponseTime = otherAverageResponseTime;
        this.meMedianResponseTime = meMedianResponseTime;
        this.otherMedianResponseTime = otherMedianResponseTime;
        this.meMinResponseTime = meMinResponseTime;
        this.otherMinResponseTime = otherMinResponseTime;
        this.meMaxResponseTime = meMaxResponseTime;
        this.otherMaxResponseTime = otherMaxResponseTime;
    }

    public static InteractionStatistics empty(long relationshipId) {
        return new InteractionStatistics(relationshipId, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                null, null, null, null, null, null, null, null);
    }

    public long getRelationshipId() { return relationshipId; }
    public int getSessionCount() { return sessionCount; }
    public int getMeInitiatedSessionCount() { return meInitiatedSessionCount; }
    public int getOtherInitiatedSessionCount() { return otherInitiatedSessionCount; }
    public int getUntimedMessageCount() { return untimedMessageCount; }
    public int getMeMessageCount() { return meMessageCount; }
    public int getOtherMessageCount() { return otherMessageCount; }
    public int getSystemMessageCount() { return systemMessageCount; }
    public int getMeResponseCount() { return meResponseCount; }
    public int getOtherResponseCount() { return otherResponseCount; }
    public Duration getMeAverageResponseTime() { return meAverageResponseTime; }
    public Duration getOtherAverageResponseTime() { return otherAverageResponseTime; }
    public Duration getMeMedianResponseTime() { return meMedianResponseTime; }
    public Duration getOtherMedianResponseTime() { return otherMedianResponseTime; }
    public Duration getMeMinResponseTime() { return meMinResponseTime; }
    public Duration getOtherMinResponseTime() { return otherMinResponseTime; }
    public Duration getMeMaxResponseTime() { return meMaxResponseTime; }
    public Duration getOtherMaxResponseTime() { return otherMaxResponseTime; }
}
