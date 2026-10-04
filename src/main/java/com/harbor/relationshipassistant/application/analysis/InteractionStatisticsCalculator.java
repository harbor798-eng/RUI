package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.domain.analysis.AnalysisMessage;
import com.harbor.relationshipassistant.domain.analysis.interaction.ConversationSession;
import com.harbor.relationshipassistant.domain.analysis.interaction.InteractionStatistics;
import com.harbor.relationshipassistant.domain.chat.SenderType;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 从 {@link ConversationSession} 计算客观互动统计事实。
 * <p>
 * 只做：Session 计数、发起方计数、消息计数、回复配对、回复时间统计。
 * <b>不</b>做：情绪/冲突/暧昧识别、关系判断、Evidence、Pattern、AI 调用。
 * </p>
 * <p>
 * 回复配对 V1 规则：
 * <ol>
 *   <li>连续同一发送者（ME/OTHER，忽略 SYSTEM）的消息合并为一个 Block；</li>
 *   <li>相邻两个不同发送者的 Block (A→B) 视为 B 对 A 的一次回复；</li>
 *   <li>回复时间 = B.Block 首条时间 - A.Block 末条时间；</li>
 *   <li>回复统计只在同一 Session 内进行，Session 之间不配对。</li>
 * </ol>
 * </p>
 */
public class InteractionStatisticsCalculator {

    /**
     * @param relationshipId 关系 ID（必须 > 0）
     * @param sessions       ChatProcessor→ConversationSessionBuilder 输出的 Session 列表
     * @param messages       ChatProcessor 输出的原始 AnalysisMessage（用于计算 untimedMessageCount）
     */
    public InteractionStatistics calculate(long relationshipId,
                                           List<ConversationSession> sessions,
                                           List<AnalysisMessage> messages) {
        System.out.println("[InteractionStatisticsCalculator] Start relationshipId=" + relationshipId);
        if (relationshipId <= 0) {
            throw new IllegalArgumentException("relationshipId must be > 0");
        }
        if (sessions == null) {
            throw new IllegalArgumentException("sessions must not be null");
        }
        for (ConversationSession s : sessions) {
            if (s == null) {
                throw new IllegalArgumentException("sessions contains null element");
            }
            if (s.getRelationshipId() != relationshipId) {
                throw new IllegalArgumentException("session relationshipId=" + s.getRelationshipId()
                        + " does not match expected=" + relationshipId);
            }
        }

        if (sessions.isEmpty()) {
            System.out.println("[InteractionStatisticsCalculator] Empty sessions; returning empty stats");
            System.out.println("[InteractionStatisticsCalculator] Completed");
            return InteractionStatistics.empty(relationshipId);
        }

        int sessionCount = 0;
        int meInitiated = 0;
        int otherInitiated = 0;
        int meMsg = 0;
        int otherMsg = 0;
        int systemMsg = 0;
        int meResp = 0;
        int otherResp = 0;

        List<Duration> meLatencies = new ArrayList<>();
        List<Duration> otherLatencies = new ArrayList<>();

        try {
            for (ConversationSession s : sessions) {
                sessionCount++;
                List<AnalysisMessage> msgs = s.getMessages();

                // 1) 消息计数
                for (AnalysisMessage m : msgs) {
                    if (m.getSenderType() == SenderType.ME) meMsg++;
                    else if (m.getSenderType() == SenderType.OTHER) otherMsg++;
                    else if (m.getSenderType() == SenderType.SYSTEM) systemMsg++;
                }

                // 2) 构造 Block（忽略 SYSTEM、忽略 null-time）
                List<MessageBlock> blocks = buildBlocks(msgs);

                // 3) 发起方 = 第一个 ME/OTHER block
                if (!blocks.isEmpty()) {
                    SenderType first = blocks.get(0).sender;
                    if (first == SenderType.ME) meInitiated++;
                    else if (first == SenderType.OTHER) otherInitiated++;
                }
                // 全 SYSTEM 的 session：initiator=NONE，不计入任一发起方

                // 4) 相邻 block 配对
                for (int i = 1; i < blocks.size(); i++) {
                    MessageBlock prev = blocks.get(i - 1);
                    MessageBlock curr = blocks.get(i);
                    if (prev.sender == curr.sender) continue; // 同发送者不应相邻（已合并）
                    Duration latency = Duration.between(prev.endTime, curr.startTime);
                    if (latency.isNegative()) {
                        System.out.println("[InteractionStatisticsCalculator] WARN Negative response latency detected, skipping latency metric.");
                        continue;
                    }
                    if (curr.sender == SenderType.ME) {
                        meResp++;
                        meLatencies.add(latency);
                    } else if (curr.sender == SenderType.OTHER) {
                        otherResp++;
                        otherLatencies.add(latency);
                    }
                }
            }

            // 5) untimedMessageCount：原始消息数 - 已进入 session 的消息数
            int untimed = 0;
            if (messages != null) {
                int inSessions = 0;
                for (ConversationSession s : sessions) inSessions += s.getMessages().size();
                untimed = Math.max(0, messages.size() - inSessions);
            }

            Duration meAvg = avg(meLatencies);
            Duration otherAvg = avg(otherLatencies);
            Duration meMed = median(meLatencies);
            Duration otherMed = median(otherLatencies);
            Duration meMin = min(meLatencies);
            Duration otherMin = min(otherLatencies);
            Duration meMax = max(meLatencies);
            Duration otherMax = max(otherLatencies);

            System.out.println("[InteractionStatisticsCalculator] sessionCount=" + sessionCount);
            System.out.println("[InteractionStatisticsCalculator] ME initiated sessions=" + meInitiated);
            System.out.println("[InteractionStatisticsCalculator] OTHER initiated sessions=" + otherInitiated);
            System.out.println("[InteractionStatisticsCalculator] ME messages=" + meMsg);
            System.out.println("[InteractionStatisticsCalculator] OTHER messages=" + otherMsg);
            System.out.println("[InteractionStatisticsCalculator] SYSTEM messages=" + systemMsg);
            System.out.println("[InteractionStatisticsCalculator] ME responses=" + meResp);
            System.out.println("[InteractionStatisticsCalculator] OTHER responses=" + otherResp);
            System.out.println("[InteractionStatisticsCalculator] ME avg response time=" + meAvg);
            System.out.println("[InteractionStatisticsCalculator] OTHER avg response time=" + otherAvg);
            System.out.println("[InteractionStatisticsCalculator] Completed");

            return new InteractionStatistics(relationshipId, sessionCount, meInitiated, otherInitiated,
                    untimed, meMsg, otherMsg, systemMsg, meResp, otherResp,
                    meAvg, otherAvg, meMed, otherMed, meMin, otherMin, meMax, otherMax);
        } catch (RuntimeException e) {
            System.out.println("[InteractionStatisticsCalculator] ERROR " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
            System.out.println("[InteractionStatisticsCalculator] Exception: " + e);
            throw e;
        }
    }

    /**
     * 把 session 内消息合并为 ME/OTHER Block；忽略 SYSTEM 与 null-time 消息。
     */
    private List<MessageBlock> buildBlocks(List<AnalysisMessage> msgs) {
        List<MessageBlock> blocks = new ArrayList<>();
        MessageBlock current = null;
        for (AnalysisMessage m : msgs) {
            SenderType st = m.getSenderType();
            if (st == SenderType.SYSTEM) continue;
            LocalDateTime t = m.getMessageTime();
            if (t == null) continue;
            if (current == null || current.sender != st) {
                if (current != null) blocks.add(current);
                current = new MessageBlock(st, t, t);
            } else {
                current = new MessageBlock(st, current.startTime, t);
            }
        }
        if (current != null) blocks.add(current);
        return blocks;
    }

    private static Duration avg(List<Duration> list) {
        if (list.isEmpty()) return null;
        long totalNanos = 0L;
        for (Duration d : list) totalNanos += d.toNanos();
        return Duration.ofNanos(totalNanos / list.size());
    }

    private static Duration median(List<Duration> list) {
        if (list.isEmpty()) return null;
        List<Duration> sorted = new ArrayList<>(list);
        Collections.sort(sorted);
        int n = sorted.size();
        if (n % 2 == 1) return sorted.get(n / 2);
        long a = sorted.get(n / 2 - 1).toNanos();
        long b = sorted.get(n / 2).toNanos();
        return Duration.ofNanos((a + b) / 2);
    }

    private static Duration min(List<Duration> list) {
        if (list.isEmpty()) return null;
        Duration m = list.get(0);
        for (Duration d : list) if (d.compareTo(m) < 0) m = d;
        return m;
    }

    private static Duration max(List<Duration> list) {
        if (list.isEmpty()) return null;
        Duration m = list.get(0);
        for (Duration d : list) if (d.compareTo(m) > 0) m = d;
        return m;
    }

    /** Block 是 Calculator 内部临时结构，不暴露为公共领域模型。 */
    private record MessageBlock(SenderType sender, LocalDateTime startTime, LocalDateTime endTime) {}
}
