package com.harbor.relationshipassistant.domain.observation;

import java.util.List;
import java.util.Map;

/**
 * Batch 窗口内的客观事实统计（只描述"是什么"，不做任何关系/性格推断）。
 */
public record FactStats(
        long totalMessages,
        long meCount,
        long otherCount,
        long systemCount,
        double meRatio,
        double otherRatio,
        long activeDays,
        Map<String, Long> messageTypeCounts,
        /** 我回复对方的中位间隔（小时）；样本不足为 null。 */
        Double myReplyLatencyHoursMedian,
        Double otherReplyLatencyHoursMedian,
        long myReplySampleCount,
        long otherReplySampleCount,
        /** 会话开启：与上一条间隔超过 INITIATION_GAP_HOURS 视为新会话。 */
        long meStartedSessions,
        long otherStartedSessions,
        double initiationGapHours) {

    /** 开启新会话的客观阈值：距上一条消息超过该时长即视为新会话。 */
    public static final double INITIATION_GAP_HOURS = 4.0;
}
