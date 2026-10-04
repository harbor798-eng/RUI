package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.domain.analysis.AnalysisMessage;
import com.harbor.relationshipassistant.domain.analysis.interaction.ConversationSession;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 把按时间升序排列的 {@link AnalysisMessage} 切分成一个个 {@link ConversationSession}。
 * <p>
 * 切分规则：相邻两条有效时间消息的间隔 &gt; {@link #sessionGap} 时，开新 Session；
 * {@code <= sessionGap} 时归入当前 Session。
 * </p>
 * <p>
 * <b>不</b>负责：统计发起方、判断谁回复谁、计算回复时间、识别情绪/冲突/暧昧、
 * 生成 EvidenceWindow、调用 LLM、查询数据库。
 * </p>
 * <p>
 * 调用方必须提供按 {@code messageTime ASC, id ASC} 排序的消息；
 * 输入中若出现时间倒序，本类会抛 {@link IllegalArgumentException}，不静默修正。
 * </p>
 */
public class ConversationSessionBuilder {

    /**
     * 默认会话切分阈值：相邻有效消息间隔超过 2 小时即开新 Session。
     * <p>
     * 这是当前 Analysis Pipeline 的工程默认值，不是心理学结论或关系判断标准；
     * 未来产品规则调整时只改这一处。
     * </p>
     */
    private static final Duration DEFAULT_SESSION_GAP = Duration.ofHours(2);

    private final Duration sessionGap;

    public ConversationSessionBuilder() {
        this(DEFAULT_SESSION_GAP);
    }

    public ConversationSessionBuilder(Duration sessionGap) {
        this.sessionGap = (sessionGap == null) ? DEFAULT_SESSION_GAP : sessionGap;
    }

    public List<ConversationSession> build(List<AnalysisMessage> messages) {
        System.out.println("[ConversationSessionBuilder] Start");
        if (messages == null) {
            throw new IllegalArgumentException("messages must not be null");
        }
        System.out.println("[ConversationSessionBuilder] Input message count=" + messages.size());
        System.out.println("[ConversationSessionBuilder] sessionGap=" + sessionGap);

        if (messages.isEmpty()) {
            System.out.println("[ConversationSessionBuilder] Total session count=0");
            System.out.println("[ConversationSessionBuilder] Completed");
            return Collections.emptyList();
        }

        try {
            long relationshipId = -1L;
            int untimed = 0;
            LocalDateTime previousTime = null;

            List<ConversationSession> sessions = new ArrayList<>();
            List<AnalysisMessage> current = new ArrayList<>();
            LocalDateTime currentStart = null;
            LocalDateTime currentEnd = null;

            for (AnalysisMessage m : messages) {
                // 1) relationshipId 一致性校验
                long rid = m.getRelationshipId() == null ? -1L : m.getRelationshipId();
                if (relationshipId == -1L) {
                    relationshipId = rid;
                } else if (rid != relationshipId) {
                    throw new IllegalArgumentException(
                            "Input mixes relationshipId=" + relationshipId + " and " + rid
                            + "; cannot build sessions across relationships");
                }

                // 2) messageTime == null：无法参与时间切分，跳过并计数
                LocalDateTime t = m.getMessageTime();
                if (t == null) {
                    untimed++;
                    continue;
                }

                // 3) 排序校验：输入必须 messageTime ASC
                if (previousTime != null && t.isBefore(previousTime)) {
                    throw new IllegalArgumentException(
                            "[ConversationSessionBuilder] ERROR Input messages are not sorted by messageTime");
                }
                previousTime = t;

                // 4) 切分判断
                if (current.isEmpty()) {
                    // 开启第一个 Session
                    currentStart = t;
                    currentEnd = t;
                    current.add(m);
                } else {
                    Duration gap = Duration.between(currentEnd, t);
                    if (gap.compareTo(sessionGap) > 0) {
                        // gap > sessionGap：结束当前 Session，开新的
                        System.out.println("[ConversationSessionBuilder] Session split because gap=" + gap);
                        sessions.add(buildSession(relationshipId, currentStart, currentEnd, current));
                        current = new ArrayList<>();
                        currentStart = t;
                        currentEnd = t;
                        current.add(m);
                    } else {
                        // gap <= sessionGap：继续当前 Session
                        currentEnd = t;
                        current.add(m);
                    }
                }
            }

            // 5) 收尾最后一个 Session
            if (!current.isEmpty()) {
                sessions.add(buildSession(relationshipId, currentStart, currentEnd, current));
            }

            System.out.println("[ConversationSessionBuilder] Valid timed message count="
                    + (messages.size() - untimed));
            System.out.println("[ConversationSessionBuilder] Untimed message count=" + untimed);
            System.out.println("[ConversationSessionBuilder] Total session count=" + sessions.size());
            System.out.println("[ConversationSessionBuilder] Completed");
            return Collections.unmodifiableList(sessions);
        } catch (RuntimeException e) {
            System.out.println("[ConversationSessionBuilder] ERROR " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
            System.out.println("[ConversationSessionBuilder] Exception: " + e);
            throw e;
        }
    }

    private ConversationSession buildSession(long relationshipId,
                                             LocalDateTime start,
                                             LocalDateTime end,
                                             List<AnalysisMessage> msgs) {
        String id = "SESSION-" + UUID.randomUUID();
        System.out.println("[ConversationSessionBuilder] Session created: id=" + id
                + ", start=" + start + ", end=" + end + ", messageCount=" + msgs.size());
        return new ConversationSession(id, relationshipId, start, end, new ArrayList<>(msgs));
    }
}
