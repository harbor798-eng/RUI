package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.domain.analysis.AnalysisMessage;
import com.harbor.relationshipassistant.domain.analysis.statistics.MessageStatistics;
import com.harbor.relationshipassistant.domain.analysis.statistics.StatisticsContext;
import com.harbor.relationshipassistant.domain.chat.SenderType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 纯计算组件：接收 {@link AnalysisMessage} 列表，输出客观统计结果。
 * <p>
 * <b>不</b>查询数据库、<b>不</b>调用 Repository/Service/AI、<b>不</b>修改输入 List。
 * 数据获取已由 {@link ChatProcessor} 负责。
 * </p>
 * <p>
 * 核心原则：程序计算事实，AI 解释事实。本类不做任何关系判断、不识别情绪、
 * 不生成 Evidence、不计算主动发起或回复时间。
 * </p>
 */
public class StatisticsCalculator {

    public StatisticsContext calculate(List<AnalysisMessage> messages) {
        System.out.println("[StatisticsCalculator] Start");
        if (messages == null) {
            throw new IllegalArgumentException("messages must not be null");
        }
        System.out.println("[StatisticsCalculator] Input message count=" + messages.size());

        if (messages.isEmpty()) {
            System.out.println("[StatisticsCalculator] Empty input; returning empty statistics");
            System.out.println("[StatisticsCalculator] Completed");
            return new StatisticsContext(MessageStatistics.empty());
        }

        long me = 0, other = 0, system = 0;
        LocalDateTime first = null;
        LocalDateTime last = null;
        Map<LocalDate, Integer> byDay = new LinkedHashMap<>();

        try {
            for (AnalysisMessage m : messages) {
                // 消息数量：包括所有 senderType（含 messageTime == null 的消息）
                SenderType st = m.getSenderType();
                if (st == SenderType.ME) {
                    me++;
                } else if (st == SenderType.OTHER) {
                    other++;
                } else if (st == SenderType.SYSTEM) {
                    system++;
                }

                // 时间相关：只看 messageTime != null
                LocalDateTime t = m.getMessageTime();
                if (t == null) {
                    continue;
                }
                if (first == null || t.isBefore(first)) first = t;
                if (last == null || t.isAfter(last)) last = t;

                LocalDate day = t.toLocalDate();
                byDay.merge(day, 1, Integer::sum);
            }

            int activeDays = byDay.size();
            MessageStatistics stats = new MessageStatistics(
                    me + other + system,
                    me, other, system,
                    first, last,
                    activeDays,
                    byDay
            );

            System.out.println("[StatisticsCalculator] ME count=" + me);
            System.out.println("[StatisticsCalculator] OTHER count=" + other);
            System.out.println("[StatisticsCalculator] SYSTEM count=" + system);
            System.out.println("[StatisticsCalculator] First message time=" + first);
            System.out.println("[StatisticsCalculator] Last message time=" + last);
            System.out.println("[StatisticsCalculator] Active days=" + activeDays);
            System.out.println("[StatisticsCalculator] Active day distribution size=" + byDay.size());
            System.out.println("[StatisticsCalculator] Completed");
            return new StatisticsContext(stats);
        } catch (RuntimeException e) {
            System.out.println("[StatisticsCalculator] ERROR " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("[StatisticsCalculator] Exception: " + e);
            throw e;
        }
    }
}
