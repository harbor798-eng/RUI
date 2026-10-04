package com.harbor.relationshipassistant.domain.analysis.statistics;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 最基础、最客观的消息统计。
 * <p>
 * 这是程序计算的事实，不是 AI 的解释，也不包含任何分数/评分。
 * 只统计不需要"会话切分"的指标。
 * </p>
 * <p>
 * 时间语义：项目中 {@code message_time} 是 Asia/Shanghai 墙钟 LocalDateTime，
 * 因此 {@code activeDays} 与 {@code messageCountByDay} 的自然日边界直接取
 * {@link LocalDateTime#toLocalDate()}，不再做时区换算。
 * </p>
 */
public final class MessageStatistics {

    private final long totalMessageCount;
    private final long meMessageCount;
    private final long otherMessageCount;
    private final long systemMessageCount;
    private final LocalDateTime firstMessageTime;
    private final LocalDateTime lastMessageTime;
    private final int activeDays;
    private final Map<LocalDate, Integer> messageCountByDay;

    public MessageStatistics(long totalMessageCount,
                            long meMessageCount,
                            long otherMessageCount,
                            long systemMessageCount,
                            LocalDateTime firstMessageTime,
                            LocalDateTime lastMessageTime,
                            int activeDays,
                            Map<LocalDate, Integer> messageCountByDay) {
        this.totalMessageCount = totalMessageCount;
        this.meMessageCount = meMessageCount;
        this.otherMessageCount = otherMessageCount;
        this.systemMessageCount = systemMessageCount;
        this.firstMessageTime = firstMessageTime;
        this.lastMessageTime = lastMessageTime;
        this.activeDays = activeDays;
        this.messageCountByDay = (messageCountByDay == null || messageCountByDay.isEmpty())
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(messageCountByDay));
    }

    /** 空输入对应的统计（全部 0 / null / 空 Map）。 */
    public static MessageStatistics empty() {
        return new MessageStatistics(0, 0, 0, 0, null, null, 0, Collections.emptyMap());
    }

    public long getTotalMessageCount() { return totalMessageCount; }
    public long getMeMessageCount() { return meMessageCount; }
    public long getOtherMessageCount() { return otherMessageCount; }
    public long getSystemMessageCount() { return systemMessageCount; }
    public LocalDateTime getFirstMessageTime() { return firstMessageTime; }
    public LocalDateTime getLastMessageTime() { return lastMessageTime; }
    public int getActiveDays() { return activeDays; }
    public Map<LocalDate, Integer> getMessageCountByDay() { return messageCountByDay; }
}
