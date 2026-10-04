package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.domain.analysis.AnalysisMessage;
import com.harbor.relationshipassistant.domain.analysis.EvidenceType;
import com.harbor.relationshipassistant.domain.analysis.EvidenceWindow;
import com.harbor.relationshipassistant.domain.analysis.PatternCandidate;
import com.harbor.relationshipassistant.domain.analysis.PatternType;
import com.harbor.relationshipassistant.domain.analysis.interaction.ConversationSession;
import com.harbor.relationshipassistant.domain.analysis.interaction.InteractionStatistics;
import com.harbor.relationshipassistant.domain.analysis.statistics.MessageStatistics;
import com.harbor.relationshipassistant.domain.chat.SenderType;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从 EvidenceWindow / Statistics / Session 中检测"重复、持续、变化"的行为模式候选。
 * <p>
 * 纯本地确定性规则，<b>不</b>调用 AI、<b>不</b>访问数据库、<b>不</b>做关系判断。
 * 输出 {@link PatternCandidate} 只描述"现象"，不解释"原因"。
 * </p>
 */
public class PatternDetector {

    private static final double CHANGE_RATIO = 2.0;
    private static final int MIN_SESSIONS_FOR_CHANGE = 4;

    public List<PatternCandidate> detect(List<AnalysisMessage> messages,
                                        List<ConversationSession> sessions,
                                        MessageStatistics messageStatistics,
                                        InteractionStatistics interactionStatistics,
                                        List<EvidenceWindow> evidenceWindows) {
        System.out.println("[PatternDetector] Start. messages=" + (messages == null ? 0 : messages.size())
                + ", sessions=" + (sessions == null ? 0 : sessions.size())
                + ", evidenceWindows=" + (evidenceWindows == null ? 0 : evidenceWindows.size()));

        if (messages == null || sessions == null || messageStatistics == null
                || interactionStatistics == null || evidenceWindows == null) {
            throw new IllegalArgumentException("all inputs must not be null");
        }
        if (evidenceWindows.isEmpty() && sessions.isEmpty()) {
            System.out.println("[PatternDetector] Nothing to detect; returning empty list");
            return Collections.emptyList();
        }

        // relationshipId 一致性
        long relId = -1L;
        for (EvidenceWindow w : evidenceWindows) {
            if (relId < 0) relId = w.getRelationshipId();
            else if (w.getRelationshipId() != relId) throw new IllegalArgumentException("evidenceWindow relationshipId mismatch");
        }
        for (ConversationSession s : sessions) {
            if (relId < 0) relId = s.getRelationshipId();
            else if (s.getRelationshipId() != relId) throw new IllegalArgumentException("session relationshipId mismatch");
        }

        List<PatternCandidate> out = new ArrayList<>();

        // ---------- 基于 EvidenceWindow 的重复/集中模式 ----------
        detectRejectedRepetition(evidenceWindows, out);
        detectRepairAfterConflict(evidenceWindows, out);
        detectPositiveInteraction(evidenceWindows, out);
        detectEventCluster(evidenceWindows, out);

        // ---------- 基于 Session/Statistics 的前后变化 ----------
        if (sessions.size() >= MIN_SESSIONS_FOR_CHANGE) {
            detectInitiativeChange(sessions, out);
            detectSessionFrequencyChange(sessions, out);
            detectResponseTimeChange(sessions, out);
        }
        detectMessageFrequencyChange(messageStatistics, out);

        // 去重 + 稳定排序
        List<PatternCandidate> dedup = deduplicate(out);
        dedup.sort((a, b) -> {
            int c = a.getType().compareTo(b.getType());
            if (c != 0) return c;
            return a.getPatternId().compareTo(b.getPatternId());
        });

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PatternType t : PatternType.values()) counts.put(t.name(), 0);
        for (PatternCandidate p : dedup) counts.merge(p.getType(), 1, Integer::sum);
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > 0) System.out.println("[PatternDetector] " + e.getKey() + "=" + e.getValue());
        }
        System.out.println("[PatternDetector] Pattern detection finished. total=" + dedup.size());
        return dedup;
    }

    // ---------- 1. PERSISTENT_REJECTION ----------
    private void detectRejectedRepetition(List<EvidenceWindow> windows, List<PatternCandidate> out) {
        List<EvidenceWindow> rej = new ArrayList<>();
        for (EvidenceWindow w : windows) {
            if (w.getTypes().contains(EvidenceType.BOUNDARY_OR_REJECTION)) rej.add(w);
        }
        if (rej.size() < 3) return;
        // 至少 3 个不同时间点（按日期去重）
        Set<LocalDate> days = new LinkedHashSet<>();
        for (EvidenceWindow w : rej) if (w.getStartTime() != null) days.add(w.getStartTime().toLocalDate());
        if (days.size() < 3) return;

        List<String> ids = new ArrayList<>();
        for (EvidenceWindow w : rej) ids.add(w.getEvidenceId());
        double conf = Math.min(1.0, 0.5 + 0.1 * days.size());
        out.add(new PatternCandidate("P-PERSISTENT_REJECTION-" + days.size(),
                PatternType.PERSISTENT_REJECTION.name(),
                "分析范围内多个时间点出现边界/拒绝类互动。",
                ids, conf));
    }

    // ---------- 2. REPAIR_AFTER_CONFLICT ----------
    private void detectRepairAfterConflict(List<EvidenceWindow> windows, List<PatternCandidate> out) {
        // 找同时包含 CONFLICT 和 REPAIR 的窗口，>=2 个不同时间点
        List<EvidenceWindow> pairs = new ArrayList<>();
        for (EvidenceWindow w : windows) {
            if (w.getTypes().contains(EvidenceType.CONFLICT) && w.getTypes().contains(EvidenceType.REPAIR)) {
                pairs.add(w);
            }
        }
        if (pairs.size() < 2) return;
        Set<LocalDate> days = new LinkedHashSet<>();
        for (EvidenceWindow w : pairs) if (w.getStartTime() != null) days.add(w.getStartTime().toLocalDate());
        if (days.size() < 2) return;

        List<String> ids = new ArrayList<>();
        for (EvidenceWindow w : pairs) ids.add(w.getEvidenceId());
        out.add(new PatternCandidate("P-REPAIR_AFTER_CONFLICT-" + days.size(),
                PatternType.REPAIR_AFTER_CONFLICT.name(),
                "分析范围内多次出现冲突后修复的互动序列。",
                ids, Math.min(1.0, 0.5 + 0.1 * days.size())));
    }

    // ---------- 3. POSITIVE_INTERACTION_PATTERN ----------
    private void detectPositiveInteraction(List<EvidenceWindow> windows, List<PatternCandidate> out) {
        List<EvidenceWindow> pos = new ArrayList<>();
        for (EvidenceWindow w : windows) {
            if (w.getTypes().contains(EvidenceType.REPRESENTATIVE_POSITIVE_INTERACTION)) pos.add(w);
        }
        if (pos.size() < 3) return;
        Set<LocalDate> days = new LinkedHashSet<>();
        for (EvidenceWindow w : pos) if (w.getStartTime() != null) days.add(w.getStartTime().toLocalDate());
        if (days.size() < 3) return;

        List<String> ids = new ArrayList<>();
        for (EvidenceWindow w : pos) ids.add(w.getEvidenceId());
        out.add(new PatternCandidate("P-POSITIVE-" + days.size(),
                PatternType.POSITIVE_INTERACTION_PATTERN.name(),
                "多个时间点出现具有关系互动意义的正向互动。",
                ids, Math.min(1.0, 0.5 + 0.05 * days.size())));
    }

    // ---------- 4. RELATIONSHIP_EVENT_CLUSTER ----------
    private void detectEventCluster(List<EvidenceWindow> windows, List<PatternCandidate> out) {
        List<EvidenceWindow> rel = new ArrayList<>();
        for (EvidenceWindow w : windows) {
            Set<EvidenceType> t = w.getTypes();
            if (t.contains(EvidenceType.RELATIONSHIP_STATUS_CHANGE)
                    || t.contains(EvidenceType.RELATIONSHIP_EXPRESSION)
                    || t.contains(EvidenceType.CONFLICT)
                    || t.contains(EvidenceType.REPAIR)
                    || t.contains(EvidenceType.IMPORTANT_ACTION)) {
                rel.add(w);
            }
        }
        if (rel.size() < 3) return;
        rel.sort((a, b) -> {
            LocalDateTime ta = a.getStartTime(), tb = b.getStartTime();
            if (ta == null && tb == null) return 0;
            if (ta == null) return -1;
            if (tb == null) return 1;
            return ta.compareTo(tb);
        });
        // 滑动窗口：7 天内 >=3 个窗口且 >=2 种不同类型
        for (int i = 0; i < rel.size(); i++) {
            List<EvidenceWindow> group = new ArrayList<>();
            LocalDateTime anchor = rel.get(i).getStartTime();
            if (anchor == null) continue;
            for (int j = i; j < rel.size(); j++) {
                LocalDateTime t = rel.get(j).getStartTime();
                if (t == null) continue;
                if (Duration.between(anchor, t).toDays() <= 7) group.add(rel.get(j));
                else break;
            }
            if (group.size() >= 3) {
                Set<EvidenceType> types = EnumSet.noneOf(EvidenceType.class);
                for (EvidenceWindow w : group) types.addAll(w.getTypes());
                long relTypes = types.stream().filter(t ->
                        t == EvidenceType.RELATIONSHIP_STATUS_CHANGE
                        || t == EvidenceType.RELATIONSHIP_EXPRESSION
                        || t == EvidenceType.CONFLICT
                        || t == EvidenceType.REPAIR).count();
                if (relTypes >= 2) {
                    List<String> ids = new ArrayList<>();
                    for (EvidenceWindow w : group) ids.add(w.getEvidenceId());
                    out.add(new PatternCandidate("P-EVENT_CLUSTER-" + anchor.toLocalDate(),
                            PatternType.RELATIONSHIP_EVENT_CLUSTER.name(),
                            "分析范围内多个关系相关事件在较短时间窗口内集中出现。",
                            ids, 0.7));
                    return; // 只报一个 cluster
                }
            }
        }
    }

    // ---------- 5. INTERACTION_INITIATIVE_CHANGE ----------
    private void detectInitiativeChange(List<ConversationSession> sessions, List<PatternCandidate> out) {
        int half = sessions.size() / 2;
        int fMe = 0, fOther = 0, sMe = 0, sOther = 0;
        for (int i = 0; i < half; i++) {
            SenderType t = firstMeaningfulSender(sessions.get(i));
            if (t == SenderType.ME) fMe++;
            else if (t == SenderType.OTHER) fOther++;
        }
        for (int i = half; i < sessions.size(); i++) {
            SenderType t = firstMeaningfulSender(sessions.get(i));
            if (t == SenderType.ME) sMe++;
            else if (t == SenderType.OTHER) sOther++;
        }
        int fTotal = fMe + fOther;
        int sTotal = sMe + sOther;
        if (fTotal < 2 || sTotal < 2) return;
        boolean changed = ratioChanged(fMe, sMe) || ratioChanged(fOther, sOther);
        if (!changed) return;
        out.add(new PatternCandidate("P-INITIATIVE_CHANGE",
                PatternType.INTERACTION_INITIATIVE_CHANGE.name(),
                "Session 发起分布在前后阶段出现明显变化（前半段 ME=" + fMe + "/OTHER=" + fOther
                        + "，后半段 ME=" + sMe + "/OTHER=" + sOther + "）。",
                Collections.emptyList(), 0.7));
    }

    // ---------- 6. SESSION_FREQUENCY_CHANGE ----------
    private void detectSessionFrequencyChange(List<ConversationSession> sessions, List<PatternCandidate> out) {
        int half = sessions.size() / 2;
        int f = half;
        int s = sessions.size() - half;
        if (f < 3 && s < 3) return;
        if (!ratioChanged(f, s)) return;
        out.add(new PatternCandidate("P-SESSION_FREQ",
                PatternType.SESSION_FREQUENCY_CHANGE.name(),
                "单位时间内 Session 数量在前后阶段出现明显变化（前半段=" + f + "，后半段=" + s + "）。",
                Collections.emptyList(), 0.6));
    }

    // ---------- 7. RESPONSE_TIME_CHANGE ----------
    private void detectResponseTimeChange(List<ConversationSession> sessions, List<PatternCandidate> out) {
        int half = sessions.size() / 2;
        List<Duration> f = medianLatencies(sessions.subList(0, half));
        List<Duration> s = medianLatencies(sessions.subList(half, sessions.size()));
        if (f.size() < 2 || s.size() < 2) return;
        double fMed = medianMillis(f);
        double sMed = medianMillis(s);
        if (fMed <= 0 || sMed <= 0) return;
        double ratio = Math.max(fMed / sMed, sMed / fMed);
        if (ratio < CHANGE_RATIO) return;
        out.add(new PatternCandidate("P-RESP_TIME",
                PatternType.RESPONSE_TIME_CHANGE.name(),
                "回复时间中位数在前后阶段出现明显变化（前半段中位数=" + (long) fMed + "ms，后半段=" + (long) sMed + "ms）。",
                Collections.emptyList(), 0.6));
    }

    // ---------- 8. MESSAGE_FREQUENCY_CHANGE ----------
    private void detectMessageFrequencyChange(MessageStatistics stats, List<PatternCandidate> out) {
        Map<LocalDate, Integer> byDay = stats.getMessageCountByDay();
        if (byDay == null || byDay.size() < 4) return;
        List<LocalDate> days = new ArrayList<>(byDay.keySet());
        Collections.sort(days);
        int half = days.size() / 2;
        int f = 0, s = 0;
        for (int i = 0; i < half; i++) f += byDay.get(days.get(i));
        for (int i = half; i < days.size(); i++) s += byDay.get(days.get(i));
        if (f < 5 || s < 5) return;
        if (!ratioChanged(f, s)) return;
        out.add(new PatternCandidate("P-MSG_FREQ",
                PatternType.MESSAGE_FREQUENCY_CHANGE.name(),
                "消息量在前后阶段出现明显变化（前半段=" + f + "，后半段=" + s + "）。",
                Collections.emptyList(), 0.6));
    }

    // ---------- 辅助 ----------

    private SenderType firstMeaningfulSender(ConversationSession s) {
        for (AnalysisMessage m : s.getMessages()) {
            if (m.getSenderType() == SenderType.ME) return SenderType.ME;
            if (m.getSenderType() == SenderType.OTHER) return SenderType.OTHER;
        }
        return null;
    }

    private boolean ratioChanged(int a, int b) {
        if (a < 2 || b < 2) return false;
        double max = Math.max(a, b);
        double min = Math.min(a, b);
        return max / min >= CHANGE_RATIO;
    }

    /** 计算一个 session 内所有 response latency（Block 规则）。 */
    private List<Duration> medianLatencies(List<ConversationSession> sessions) {
        List<Duration> result = new ArrayList<>();
        for (ConversationSession s : sessions) {
            SenderType prevSender = null;
            LocalDateTime prevEnd = null;
            for (AnalysisMessage m : s.getMessages()) {
                if (m.getSenderType() == SenderType.SYSTEM) continue;
                if (m.getMessageTime() == null) continue;
                SenderType st = m.getSenderType();
                if (prevSender == null || prevSender == st) {
                    prevSender = st;
                    prevEnd = m.getMessageTime();
                } else {
                    Duration lat = Duration.between(prevEnd, m.getMessageTime());
                    if (!lat.isNegative()) result.add(lat);
                    prevSender = st;
                    prevEnd = m.getMessageTime();
                }
            }
        }
        return result;
    }

    private double medianMillis(List<Duration> durs) {
        if (durs.isEmpty()) return 0;
        List<Long> ms = new ArrayList<>();
        for (Duration d : durs) ms.add(d.toMillis());
        Collections.sort(ms);
        int n = ms.size();
        if (n % 2 == 1) return ms.get(n / 2);
        return (ms.get(n / 2 - 1) + ms.get(n / 2)) / 2.0;
    }

    private List<PatternCandidate> deduplicate(List<PatternCandidate> in) {
        Map<String, PatternCandidate> byType = new LinkedHashMap<>();
        for (PatternCandidate p : in) {
            if (byType.containsKey(p.getType())) {
                PatternCandidate existing = byType.get(p.getType());
                Set<String> ids = new LinkedHashSet<>(existing.getEvidenceIds());
                ids.addAll(p.getEvidenceIds());
                PatternCandidate merged = new PatternCandidate(existing.getPatternId(),
                        existing.getType(), existing.getDescription(), new ArrayList<>(ids),
                        Math.max(existing.getConfidence(), p.getConfidence()));
                byType.put(p.getType(), merged);
            } else {
                byType.put(p.getType(), p);
            }
        }
        return new ArrayList<>(byType.values());
    }
}
