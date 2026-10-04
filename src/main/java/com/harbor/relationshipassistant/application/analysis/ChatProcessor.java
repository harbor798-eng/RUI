package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.domain.analysis.AnalysisMessage;
import com.harbor.relationshipassistant.domain.analysis.AnalysisRange;
import com.harbor.relationshipassistant.domain.chat.ChatMessage;
import com.harbor.relationshipassistant.infrastructure.persistence.ChatMessageRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 将 {@link ChatMessage} 转换为 {@link AnalysisMessage}，并按分析任务范围筛选消息。
 * <p>
 * 本批只负责：
 * <ol>
 *   <li>按 relationshipId 取聊天记录（复用现有 {@link ChatMessageRepository}）；</li>
 *   <li>按 {@link AnalysisRange} 在内存中过滤时间范围；</li>
 *   <li>按 messageTime ASC, id ASC 排序；</li>
 *   <li>转换为 {@link AnalysisMessage}；</li>
 *   <li>返回不可修改的 List。</li>
 * </ol>
 * </p>
 * <p>
 * <b>不</b>负责：统计、判断冲突/暧昧/情绪、生成 EvidenceWindow、生成 PatternCandidate、
 * 调用 LLM。这些留给后续阶段。
 * </p>
 */
public class ChatProcessor {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int PAGE_SIZE = 500;

    private final ChatMessageRepository chatRepository;

    public ChatProcessor(ChatMessageRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    /**
     * 处理一次分析任务的消息输入。
     *
     * @param relationshipId 关系 ID，必须非 null
     * @param range          分析时间范围，必须非 null
     * @return 不可修改的、按时间正序排列的 AnalysisMessage 列表（永不返回 null）
     */
    public List<AnalysisMessage> process(Long relationshipId, AnalysisRange range) {
        System.out.println("[ChatProcessor] Start");
        if (relationshipId == null) {
            throw new IllegalArgumentException("relationshipId must not be null");
        }
        if (range == null) {
            throw new IllegalArgumentException("range must not be null");
        }

        System.out.println("[ChatProcessor] relationshipId=" + relationshipId);
        System.out.println("[ChatProcessor] rangeType=" + range.getKind());

        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDateTime[] bounds = resolveBounds(range, now);
        LocalDateTime start = bounds[0];
        LocalDateTime end = bounds[1];

        System.out.println("[ChatProcessor] analysisStart=" + start);
        System.out.println("[ChatProcessor] analysisEnd=" + end);

        try {
            // 1) 全量取该关系下 ACTIVE 消息（分页，ASC）
            List<ChatMessage> raw = loadAll(relationshipId);
            System.out.println("[ChatProcessor] Raw message count=" + raw.size());

            // 2) 时间过滤 [start, end]（含边界）
            List<ChatMessage> filtered = new ArrayList<>();
            for (ChatMessage m : raw) {
                if (start != null && (m.getMessageTime() == null || m.getMessageTime().isBefore(start))) {
                    continue;
                }
                if (end != null && (m.getMessageTime() == null || m.getMessageTime().isAfter(end))) {
                    continue;
                }
                filtered.add(m);
            }
            System.out.println("[ChatProcessor] Filtered message count=" + filtered.size());

            // 3) 排序：messageTime ASC, id ASC（同输入同顺序）
            filtered.sort(Comparator
                    .comparing(ChatMessage::getMessageTime, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(ChatMessage::getId, Comparator.nullsLast(Comparator.naturalOrder())));
            System.out.println("[ChatProcessor] Sorted message count=" + filtered.size());

            // 4) 转换
            List<AnalysisMessage> result = new ArrayList<>(filtered.size());
            for (ChatMessage m : filtered) {
                result.add(new AnalysisMessage(
                        m.getId(),
                        m.getRelationshipId(),
                        m.getSenderType(),
                        m.getMessageType(),
                        m.getMessageTime(),
                        m.getContent()
                ));
            }
            System.out.println("[ChatProcessor] Converted AnalysisMessage count=" + result.size());

            System.out.println("[ChatProcessor] Completed");
            return Collections.unmodifiableList(result);
        } catch (RuntimeException e) {
            System.out.println("[ChatProcessor] ERROR " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("[ChatProcessor] Exception: " + e);
            throw e;
        }
    }

    /**
     * 通过分页加载该关系下全部 ACTIVE 消息。
     * <p>
     * 现有 Repository 没有"一次性按关系取全部"的方法；这里复用 pageByRelationship 分页拼接。
     * 不修改 Repository。
     * </p>
     */
    private List<ChatMessage> loadAll(Long relationshipId) {
        List<ChatMessage> all = new ArrayList<>();
        int offset = 0;
        while (true) {
            List<ChatMessage> page = chatRepository.pageByRelationship(relationshipId, offset, PAGE_SIZE);
            if (page.isEmpty()) break;
            all.addAll(page);
            if (page.size() < PAGE_SIZE) break;
            offset += PAGE_SIZE;
        }
        return all;
    }

    /**
     * 根据 AnalysisRange 计算 [start, end]。
     * <p>
     * 返回数组：[start, end]，null 表示该侧不设限。
     * </p>
     */
    private LocalDateTime[] resolveBounds(AnalysisRange range, LocalDateTime now) {
        switch (range.getKind()) {
            case ALL:
                return new LocalDateTime[]{null, null};
            case CUSTOM:
                return new LocalDateTime[]{range.getStart(), range.getEnd()};
            case RECENT_7_DAYS:
                return new LocalDateTime[]{now.minusDays(7), now};
            case RECENT_3_MONTHS:
                return new LocalDateTime[]{now.minusMonths(3), now};
            case RECENT_YEAR:
                return new LocalDateTime[]{now.minusYears(1), now};
            case CURRENT_CONTEXT:
            default:
                // CURRENT_CONTEXT 的具体时间窗业务尚未定义；
                // 当前先回退为"全部历史"，并打印警告，不自行发明天数。
                System.out.println("[ChatProcessor] WARN CURRENT_CONTEXT window not defined; falling back to ALL");
                return new LocalDateTime[]{null, null};
        }
    }
}
