package com.harbor.relationshipassistant.application.skill.context;

import java.time.LocalDateTime;

/**
 * 本次分析允许使用的时间范围。端点可空，不自动填充当前时间。
 */
public final class AnalysisTimeRange {
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;

    public AnalysisTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
}
