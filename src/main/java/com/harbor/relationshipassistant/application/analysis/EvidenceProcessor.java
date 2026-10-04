package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.domain.analysis.AnalysisMessage;
import com.harbor.relationshipassistant.domain.analysis.EvidenceLevel;
import com.harbor.relationshipassistant.domain.analysis.EvidenceSignal;
import com.harbor.relationshipassistant.domain.analysis.EvidenceType;
import com.harbor.relationshipassistant.domain.analysis.EvidenceWindow;
import com.harbor.relationshipassistant.domain.analysis.interaction.ConversationSession;
import com.harbor.relationshipassistant.domain.analysis.interaction.InteractionStatistics;
import com.harbor.relationshipassistant.domain.analysis.statistics.MessageStatistics;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 对 {@link EvidenceDetector} 产出的候选 EvidenceWindow 做：
 * 重要性评分 → 等级划分 → 合并 → 时间覆盖 → 正负平衡筛选。
 * <p>
 * 纯本地确定性规则，<b>不</b>调用 AI、<b>不</b>访问数据库。
 * importance 是"证据对后续分析的价值"，<b>不是</b>关系/爱情评分。
 * </p>
 */
public class EvidenceProcessor {

    // 时间分段：相邻窗口时间间隔超过该值视为不同事件，不合并
    private static final long MERGE_GAP_HOURS = 12;
    // B 级保留比例（分层抽样，确定性）
    private static final double B_KEEP_RATIO = 0.5;

    public List<EvidenceWindow> process(List<EvidenceWindow> candidates,
                                        List<AnalysisMessage> messages,
                                        List<ConversationSession> sessions,
                                        MessageStatistics messageStatistics,
                                        InteractionStatistics interactionStatistics) {
        System.out.println("[EvidenceProcessor] Start. candidates=" + (candidates == null ? 0 : candidates.size()));
        if (candidates == null) throw new IllegalArgumentException("candidates must not be null");
        if (candidates.isEmpty()) {
            System.out.println("[EvidenceProcessor] Empty candidates; returning empty list");
            return Collections.emptyList();
        }
        for (EvidenceWindow w : candidates) {
            if (w == null) throw new IllegalArgumentException("candidates contains null");
        }

        long relationshipId = candidates.get(0).getRelationshipId();
        for (EvidenceWindow w : candidates) {
            if (w.getRelationshipId() != relationshipId) {
                throw new IllegalArgumentException("candidate relationshipId mismatch");
            }
        }

        // 1) 评分 + 等级
        List<Scored> scored = new ArrayList<>();
        for (EvidenceWindow w : candidates) {
            double imp = score(w, sessions, interactionStatistics);
            EvidenceLevel lvl = levelOf(imp);
            scored.add(new Scored(w, imp, lvl));
        }
        int s = 0, a = 0, b = 0, c = 0;
        for (Scored sc : scored) {
            switch (sc.level) {
                case S -> s++; case A -> a++; case B -> b++; case C -> c++;
            }
        }
        System.out.println("[EvidenceProcessor] Level distribution: S=" + s + ",A=" + a + ",B=" + b + ",C=" + c);

        // 2) 合并：按 startTime 排序，时间相邻（<=12h）且消息范围重叠/相邻的合并
        List<Scored> merged = merge(scored);
        System.out.println("[EvidenceProcessor] After merge: " + merged.size());

        // 3) 分层选择
        List<Scored> kept = new ArrayList<>();
        List<Scored> sList = new ArrayList<>(), aList = new ArrayList<>(), bList = new ArrayList<>();
        for (Scored sc : merged) {
            switch (sc.level) {
                case S -> sList.add(sc);
                case A -> aList.add(sc);
                case B -> bList.add(sc);
                default -> { /* C 默认丢弃 */ }
            }
        }
        kept.addAll(sList);
        kept.addAll(aList);
        kept.addAll(stratifiedSample(bList));

        // 4) 时间覆盖：把分析区间分三段统计（只日志，不伪造证据）
        if (messageStatistics != null
                && messageStatistics.getFirstMessageTime() != null
                && messageStatistics.getLastMessageTime() != null) {
            LocalDateTime aStart = messageStatistics.getFirstMessageTime();
            LocalDateTime aEnd = messageStatistics.getLastMessageTime();
            long total = java.time.Duration.between(aStart, aEnd).toMinutes();
            LocalDateTime mid = aStart.plusMinutes(total / 2);
            LocalDateTime third = aStart.plusMinutes(total / 3);
            LocalDateTime twoThird = aStart.plusMinutes(2 * total / 3);
            long seg1 = countInRange(kept, aStart, third);
            long seg2 = countInRange(kept, third, twoThird);
            long seg3 = countInRange(kept, twoThird, aEnd.plusSeconds(1));
            System.out.println("[EvidenceProcessor] Time coverage segments: seg1=" + seg1 + ",seg2=" + seg2 + ",seg3=" + seg3);
        }

        // 5) 正负计数日志
        int pos = 0, neg = 0;
        for (Scored sc : kept) {
            Sign sign = signOf(sc.window);
            if (sign == Sign.POSITIVE) pos++;
            else if (sign == Sign.NEGATIVE) neg++;
        }
        System.out.println("[EvidenceProcessor] Positive windows=" + pos + ", Negative windows=" + neg);

        // 6) 构造最终不可变窗口列表（重新生成 evidenceId，不参与排序）
        List<EvidenceWindow> result = new ArrayList<>();
        for (Scored sc : kept) {
            result.add(new EvidenceWindow(
                    "EV-" + UUID.randomUUID(),
                    relationshipId,
                    sc.window.getTypes(),
                    sc.importance,
                    sc.level,
                    sc.window.getStartTime(),
                    sc.window.getEndTime(),
                    sc.window.getMessageIds(),
                    sc.window.getTriggerMessageIds(),
                    sc.window.getSignals()
            ));
        }
        // 按 startTime 稳定排序（确定性）
        result.sort((x, y) -> {
            LocalDateTime tx = x.getStartTime(), ty = y.getStartTime();
            if (tx == null && ty == null) return 0;
            if (tx == null) return -1;
            if (ty == null) return 1;
            return tx.compareTo(ty);
        });

        System.out.println("[EvidenceProcessor] Final selected windows=" + result.size());
        System.out.println("[EvidenceProcessor] Completed");
        return result;
    }

    // ---------- 评分 ----------

    private double score(EvidenceWindow w, List<ConversationSession> sessions,
                         InteractionStatistics stats) {
        double rel = 0, behavior = 0, explicitness = 0, persistence = 0, emotion = 0, downstream = 0, context = 0;

        Set<EvidenceType> types = w.getTypes();

        // relationship relevance (0~20)：按类型基础分叠加，封顶 20
        Map<EvidenceType, Double> base = new LinkedHashMap<>();
        base.put(EvidenceType.RELATIONSHIP_STATUS_CHANGE, 18.0);
        base.put(EvidenceType.RELATIONSHIP_EXPRESSION, 14.0);
        base.put(EvidenceType.CONFLICT, 12.0);
        base.put(EvidenceType.REPAIR, 15.0);
        base.put(EvidenceType.IMPORTANT_ACTION, 14.0);
        base.put(EvidenceType.BOUNDARY_OR_REJECTION, 15.0);
        base.put(EvidenceType.EXPLICIT_EMOTION, 10.0);
        base.put(EvidenceType.INTERACTION_PATTERN_CHANGE, 11.0);
        base.put(EvidenceType.PERSISTENT_BEHAVIOR, 13.0);
        base.put(EvidenceType.REPRESENTATIVE_POSITIVE_INTERACTION, 8.0);
        for (EvidenceType t : types) rel += base.getOrDefault(t, 0.0);
        rel = Math.min(rel, 20.0);

        // behavior impact (0~15)
        if (types.contains(EvidenceType.IMPORTANT_ACTION)) behavior += 10;
        if (types.contains(EvidenceType.BOUNDARY_OR_REJECTION)) behavior += 4;
        if (types.contains(EvidenceType.RELATIONSHIP_STATUS_CHANGE)) behavior += 5;
        behavior = Math.min(behavior, 15);

        // explicitness (0~10)
        if (types.contains(EvidenceType.RELATIONSHIP_STATUS_CHANGE)) explicitness += 10;
        else if (types.contains(EvidenceType.REPAIR) || types.contains(EvidenceType.BOUNDARY_OR_REJECTION)) explicitness += 7;
        else if (types.contains(EvidenceType.RELATIONSHIP_EXPRESSION)) explicitness += 5;
        else if (types.contains(EvidenceType.REPRESENTATIVE_POSITIVE_INTERACTION)) explicitness += 3;

        // persistence (0~10)
        if (types.contains(EvidenceType.PERSISTENT_BEHAVIOR)) persistence += 8;
        if (types.size() >= 3) persistence += 2;
        persistence = Math.min(persistence, 10);

        // emotion intensity (0~15)：EXPLICIT_EMOTION + 多类型
        if (types.contains(EvidenceType.EXPLICIT_EMOTION)) emotion += 8;
        if (types.contains(EvidenceType.CONFLICT)) emotion += 4;
        emotion = Math.min(emotion, 15);

        // downstream impact (0~10)：INTERACTION_PATTERN_CHANGE 说明后续互动有变化
        if (types.contains(EvidenceType.INTERACTION_PATTERN_CHANGE)) downstream += 8;
        downstream = Math.min(downstream, 10);

        // context (0~5)：窗口消息数越多上下文越完整
        int msgCount = w.getMessageIds() == null ? 0 : w.getMessageIds().size();
        context = Math.min(5, msgCount / 3.0);

        double total = rel + behavior + explicitness + persistence + emotion + downstream + context;
        return Math.max(0, Math.min(100, total));
    }

    private EvidenceLevel levelOf(double imp) {
        if (imp >= 80) return EvidenceLevel.S;
        if (imp >= 60) return EvidenceLevel.A;
        if (imp >= 30) return EvidenceLevel.B;
        return EvidenceLevel.C;
    }

    // ---------- 合并 ----------

    private List<Scored> merge(List<Scored> scored) {
        // 按 startTime 排序
        scored.sort((x, y) -> {
            LocalDateTime tx = x.window.getStartTime(), ty = y.window.getStartTime();
            if (tx == null && ty == null) return 0;
            if (tx == null) return -1;
            if (ty == null) return 1;
            return tx.compareTo(ty);
        });

        List<Scored> out = new ArrayList<>();
        Scored current = null;
        for (Scored sc : scored) {
            if (current == null) { current = sc; continue; }
            if (shouldMerge(current, sc)) {
                current = mergeTwo(current, sc);
            } else {
                out.add(current);
                current = sc;
            }
        }
        if (current != null) out.add(current);
        return out;
    }

    private boolean shouldMerge(Scored a, Scored b) {
        LocalDateTime ta = a.window.getEndTime(), tb = b.window.getStartTime();
        if (ta == null || tb == null) return false;
        long hours = java.time.Duration.between(ta, tb).toHours();
        // 时间相邻（<=12h）或消息 ID 范围重叠
        if (hours <= MERGE_GAP_HOURS) return true;
        return rangesOverlap(a.window.getMessageIds(), b.window.getMessageIds());
    }

    private boolean rangesOverlap(List<Long> x, List<Long> y) {
        if (x == null || y == null || x.isEmpty() || y.isEmpty()) return false;
        long xMax = Collections.max(x), yMin = Collections.min(y);
        return xMax >= yMin;
    }

    private Scored mergeTwo(Scored a, Scored b) {
        Set<EvidenceType> types = EnumSet.copyOf(a.window.getTypes());
        types.addAll(b.window.getTypes());
        List<Long> msgIds = new ArrayList<>(a.window.getMessageIds());
        for (Long id : b.window.getMessageIds()) if (!msgIds.contains(id)) msgIds.add(id);
        msgIds.sort(Long::compareTo);
        List<Long> trig = new ArrayList<>(a.window.getTriggerMessageIds());
        for (Long id : b.window.getTriggerMessageIds()) if (!trig.contains(id)) trig.add(id);
        List<EvidenceSignal> sigs = new ArrayList<>(a.window.getSignals());
        sigs.addAll(b.window.getSignals());
        LocalDateTime start = a.window.getStartTime();
        LocalDateTime end = a.window.getEndTime();
        if (b.window.getStartTime() != null && (start == null || b.window.getStartTime().isBefore(start))) start = b.window.getStartTime();
        if (b.window.getEndTime() != null && (end == null || b.window.getEndTime().isAfter(end))) end = b.window.getEndTime();

        EvidenceWindow merged = new EvidenceWindow(
                "EV-" + UUID.randomUUID(),
                a.window.getRelationshipId(),
                types, 0.0, EvidenceLevel.C,
                start, end, msgIds, trig, sigs);
        // 合并后取更高分
        double imp = Math.max(a.importance, b.importance);
        return new Scored(merged, imp, levelOf(imp));
    }

    // ---------- B 级分层抽样（确定性） ----------

    private List<Scored> stratifiedSample(List<Scored> bList) {
        // 简单确定性：按 startTime 排序后保留前 K 个（K = ceil(size * ratio)），保证可复现
        if (bList.isEmpty()) return Collections.emptyList();
        List<Scored> sorted = new ArrayList<>(bList);
        sorted.sort((x, y) -> {
            LocalDateTime tx = x.window.getStartTime(), ty = y.window.getStartTime();
            if (tx == null && ty == null) return 0;
            if (tx == null) return -1;
            if (ty == null) return 1;
            return tx.compareTo(ty);
        });
        int keep = (int) Math.ceil(sorted.size() * B_KEEP_RATIO);
        return new ArrayList<>(sorted.subList(0, keep));
    }

    // ---------- 正负标签（仅用于选择日志/平衡） ----------

    private enum Sign { POSITIVE, NEGATIVE, NEUTRAL }

    private Sign signOf(EvidenceWindow w) {
        Set<EvidenceType> t = w.getTypes();
        boolean pos = t.contains(EvidenceType.REPAIR) || t.contains(EvidenceType.REPRESENTATIVE_POSITIVE_INTERACTION);
        boolean neg = t.contains(EvidenceType.CONFLICT) || t.contains(EvidenceType.BOUNDARY_OR_REJECTION);
        if (pos && !neg) return Sign.POSITIVE;
        if (neg && !pos) return Sign.NEGATIVE;
        return Sign.NEUTRAL;
    }

    private long countInRange(List<Scored> list, LocalDateTime start, LocalDateTime end) {
        long n = 0;
        for (Scored sc : list) {
            if (sc.window.getStartTime() == null) continue;
            LocalDateTime t = sc.window.getStartTime();
            if (!t.isBefore(start) && t.isBefore(end)) n++;
        }
        return n;
    }

    private static final class Scored {
        final EvidenceWindow window;
        final double importance;
        final EvidenceLevel level;
        Scored(EvidenceWindow window, double importance, EvidenceLevel level) {
            this.window = window;
            this.importance = importance;
            this.level = level;
        }
    }
}
