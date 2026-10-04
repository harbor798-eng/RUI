package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.domain.analysis.AnalysisMessage;
import com.harbor.relationshipassistant.domain.analysis.EvidenceLevel;
import com.harbor.relationshipassistant.domain.analysis.EvidenceSignal;
import com.harbor.relationshipassistant.domain.analysis.EvidenceType;
import com.harbor.relationshipassistant.domain.analysis.EvidenceWindow;
import com.harbor.relationshipassistant.domain.analysis.interaction.ConversationSession;
import com.harbor.relationshipassistant.domain.analysis.interaction.InteractionStatistics;
import com.harbor.relationshipassistant.domain.analysis.statistics.MessageStatistics;
import com.harbor.relationshipassistant.domain.chat.SenderType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 候选证据检测器：从已切分好的聊天消息/Session/统计中，用确定性本地规则
 * 发现"值得交给后续 AI 观察的聊天片段"。
 * <p>
 * 本类只产出 {@link EvidenceWindow} 候选，<b>不做</b>关系判断、情绪推断、
 * 不分 importance（V1 统一 0.0 / level=C，留到下一批评分）。
 * <b>不</b>调用 AI、<b>不</b>访问数据库。
 * </p>
 */
public class EvidenceDetector {

    private static final int CONTEXT_BEFORE = 5;
    private static final int CONTEXT_AFTER = 5;

    // ---------- 关键词表（V1 保守候选，仅作为触发信号，不作为结论） ----------

    private static final String[] RELATIONSHIP_STATUS_CHANGE = {
            "我们在一起", "我们试试", "做我男女朋友", "做我女朋友", "做我男朋友",
            "我们分手", "我们还是做朋友", "重新开始", "先冷静一下", "我们先分开",
            "复合", "和好", "我们结束吧", "别再联系了", "不要再联系"
    };

    private static final String[] RELATIONSHIP_EXPRESSION = {
            "我很想你", "我好想你", "我喜欢你", "我在乎你", "我不想失去你",
            "我们最近", "越来越远", "希望我们", "多聊聊", "多沟通",
            "对你有感觉", "心动", "我喜欢你的", "我在意你"
    };

    private static final String[] CONFLICT = {
            "你为什么总是", "你根本不", "你从来都不", "随便你", "算了吧",
            "不想跟你说", "别说了", "我已经说过", "你根本不在乎", "你能不能",
            "我受够了", "吵够了", "不可理喻"
    };

    private static final String[] REPAIR = {
            "对不起", "抱歉", "我刚才语气", "我们好好说", "我不想跟你吵",
            "要不我们聊聊", "刚才是我不对", "我错了", "别生气", "我们冷静下来",
            "我不想因为这个"
    };

    private static final String[] IMPORTANT_ACTION = {
            "周末一起", "一起吃饭", "一起看电影", "见面吧", "出来见", "约个时间",
            "我来找你", "去找你", "送你", "给你买", "帮你", "我们一起去",
            "下次一起", "改天一起", "不去了", "取消", "临时有事"
    };

    private static final String[] BOUNDARY_OR_REJECTION = {
            "不了吧", "不想去", "暂时不想", "别这样", "需要一点空间",
            "我不想见面", "这件事我不接受", "我做不到", "我还没准备好",
            "我们先不要", "别逼我", "我拒绝", "不太方便"
    };

    private static final String[] EXPLICIT_EMOTION = {
            "我很开心", "我好开心", "我好难过", "我真的生气", "我有点失望",
            "我很紧张", "我好害怕", "我很委屈", "我太高兴", "我烦死了",
            "我好焦虑", "我心情很差", "我很高兴"
    };

    private static final String[] POSITIVE_INTERACTION = {
            "哈哈", "哈哈哈", "这个好笑", "你真好", "谢谢你", "谢谢你哦",
            "有你真好", "我也觉得", "一起加油", "晚安", "早安",
            "注意休息", "早点睡", "别太累"
    };

    public List<EvidenceWindow> detect(List<AnalysisMessage> messages,
                                       List<ConversationSession> sessions,
                                       MessageStatistics messageStatistics,
                                       InteractionStatistics interactionStatistics) {
        System.out.println("[EvidenceDetector] Start detection. messages=" + (messages == null ? 0 : messages.size())
                + ", sessions=" + (sessions == null ? 0 : sessions.size()));

        if (messages == null || sessions == null) {
            throw new IllegalArgumentException("messages and sessions must not be null");
        }
        if (messages.isEmpty()) {
            System.out.println("[EvidenceDetector] Empty messages; returning empty windows");
            System.out.println("[EvidenceDetector] Detection finished. rawCandidates=0, mergedWindows=0");
            return Collections.emptyList();
        }

        // 排序校验
        LocalDateTime prev = null;
        for (AnalysisMessage m : messages) {
            LocalDateTime t = m.getMessageTime();
            if (t == null) continue;
            if (prev != null && t.isBefore(prev)) {
                throw new IllegalArgumentException("[EvidenceDetector] ERROR messages not sorted by messageTime");
            }
            prev = t;
        }

        long relationshipId = messages.get(0).getRelationshipId() == null ? 0L
                : messages.get(0).getRelationshipId();

        // 1) 逐消息打标签
        List<Candidate> candidates = new ArrayList<>();
        // PERSISTENT_BEHAVIOR：简单统计同一类拒绝/边界短语出现次数
        Map<String, Integer> phraseHits = new LinkedHashMap<>();

        for (int i = 0; i < messages.size(); i++) {
            AnalysisMessage m = messages.get(i);
            if (m.getMessageTime() == null || m.getMessageId() == null) continue;
            String content = m.getContent();
            if (content == null || content.isBlank()) continue;
            String lower = content.toLowerCase(Locale.ROOT);

            Set<EvidenceType> types = EnumSet.noneOf(EvidenceType.class);
            List<EvidenceSignal> signals = new ArrayList<>();

            checkPhrases(lower, RELATIONSHIP_STATUS_CHANGE, EvidenceType.RELATIONSHIP_STATUS_CHANGE,
                    "明确的关系状态表达", types, signals);
            checkPhrases(lower, RELATIONSHIP_EXPRESSION, EvidenceType.RELATIONSHIP_EXPRESSION,
                    "明确的关系/情感表达", types, signals);
            checkPhrases(lower, IMPORTANT_ACTION, EvidenceType.IMPORTANT_ACTION,
                    "对关系有直接影响的行为", types, signals);
            checkPhrases(lower, BOUNDARY_OR_REJECTION, EvidenceType.BOUNDARY_OR_REJECTION,
                    "明确的边界或拒绝表达", types, signals);
            checkPhrases(lower, EXPLICIT_EMOTION, EvidenceType.EXPLICIT_EMOTION,
                    "明确的情绪表达", types, signals);
            checkPhrases(lower, POSITIVE_INTERACTION, EvidenceType.REPRESENTATIVE_POSITIVE_INTERACTION,
                    "积极/有温度的互动", types, signals);

            // CONFLICT：关键词命中
            boolean conflictHit = checkPhrases(lower, CONFLICT, EvidenceType.CONFLICT,
                    "冲突/争执上下文", types, signals);

            // REPAIR：必须附近（前后 10 条）出现过 CONFLICT 才算修复候选
            if (containsAny(lower, REPAIR)) {
                boolean nearbyConflict = false;
                int lo = Math.max(0, i - 10);
                int hi = Math.min(messages.size() - 1, i + 10);
                for (int j = lo; j <= hi; j++) {
                    String c = messages.get(j).getContent();
                    if (c != null && containsAny(c.toLowerCase(Locale.ROOT), CONFLICT)) {
                        nearbyConflict = true;
                        break;
                    }
                }
                if (nearbyConflict) {
                    types.add(EvidenceType.REPAIR);
                    signals.add(new EvidenceSignal(EvidenceType.REPAIR.name(),
                            "冲突上下文附近出现修复/道歉行为", null));
                }
            }

            // PERSISTENT_BEHAVIOR：记录拒绝类短语重复
            if (types.contains(EvidenceType.BOUNDARY_OR_REJECTION)) {
                for (String phrase : BOUNDARY_OR_REJECTION) {
                    if (lower.contains(phrase)) {
                        phraseHits.merge(phrase, 1, Integer::sum);
                        break;
                    }
                }
            }

            if (!types.isEmpty()) {
                candidates.add(new Candidate(i, m, types, signals));
            }
        }

        // PERSISTENT_BEHAVIOR：同一拒绝短语出现 >=3 次，把所有命中该短语的消息标记为候选
        for (Map.Entry<String, Integer> e : phraseHits.entrySet()) {
            if (e.getValue() >= 3) {
                for (int i = 0; i < messages.size(); i++) {
                    AnalysisMessage m = messages.get(i);
                    String c = m.getContent();
                    if (c == null) continue;
                    if (c.toLowerCase(Locale.ROOT).contains(e.getKey())) {
                        Candidate existing = findCandidateAt(candidates, i);
                        if (existing != null) {
                            existing.types.add(EvidenceType.PERSISTENT_BEHAVIOR);
                            existing.signals.add(new EvidenceSignal(EvidenceType.PERSISTENT_BEHAVIOR.name(),
                                    "相同边界/拒绝短语在分析期内重复出现 " + e.getValue() + " 次", null));
                        } else if (m.getMessageTime() != null && m.getMessageId() != null) {
                            Set<EvidenceType> types = EnumSet.of(EvidenceType.PERSISTENT_BEHAVIOR);
                            List<EvidenceSignal> signals = new ArrayList<>();
                            signals.add(new EvidenceSignal(EvidenceType.PERSISTENT_BEHAVIOR.name(),
                                    "相同边界/拒绝短语重复出现 " + e.getValue() + " 次", null));
                            candidates.add(new Candidate(i, m, types, signals));
                        }
                    }
                }
            }
        }

        // INTERACTION_PATTERN_CHANGE：V1 简单比较 Session 前半段 vs 后半段发起数
        if (interactionStatistics != null && sessions != null && sessions.size() >= 4) {
            int half = sessions.size() / 2;
            long firstHalfMe = 0, firstHalfOther = 0;
            long secondHalfMe = 0, secondHalfOther = 0;
            for (int i = 0; i < half; i++) {
                SessionInitiatorResult r = initiatorOf(sessions.get(i));
                if (r == SessionInitiatorResult.ME) firstHalfMe++;
                else if (r == SessionInitiatorResult.OTHER) firstHalfOther++;
            }
            for (int i = half; i < sessions.size(); i++) {
                SessionInitiatorResult r = initiatorOf(sessions.get(i));
                if (r == SessionInitiatorResult.ME) secondHalfMe++;
                else if (r == SessionInitiatorResult.OTHER) secondHalfOther++;
            }
            double ratio = Math.max(firstHalfMe + firstHalfOther, 1)
                    / (double) Math.max(secondHalfMe + secondHalfOther, 1);
            double reverse = Math.max(secondHalfMe + secondHalfOther, 1)
                    / (double) Math.max(firstHalfMe + firstHalfOther, 1);
            if (ratio >= 3.0 || reverse >= 3.0) {
                // 在分界附近找一条消息打标
                ConversationSession boundary = sessions.get(half);
                if (!boundary.getMessages().isEmpty()) {
                    AnalysisMessage bm = boundary.getMessages().get(0);
                    if (bm.getMessageTime() != null && bm.getMessageId() != null) {
                        Candidate existing = findCandidateByMessageId(candidates, bm.getMessageId());
                        EvidenceSignal sig = new EvidenceSignal(EvidenceType.INTERACTION_PATTERN_CHANGE.name(),
                                "Session 发起频率前后半段出现明显变化（前半段 sessions="
                                        + (firstHalfMe + firstHalfOther) + "，后半段="
                                        + (secondHalfMe + secondHalfOther) + "）", null);
                        if (existing != null) {
                            existing.types.add(EvidenceType.INTERACTION_PATTERN_CHANGE);
                            existing.signals.add(sig);
                        } else {
                            Set<EvidenceType> types = EnumSet.of(EvidenceType.INTERACTION_PATTERN_CHANGE);
                            List<EvidenceSignal> signals = new ArrayList<>();
                            signals.add(sig);
                            candidates.add(new Candidate(indexOf(messages, bm), bm, types, signals));
                        }
                    }
                }
            }
        }

        System.out.println("[EvidenceDetector] rawCandidates=" + candidates.size());

        // 2) 局部合并：重叠/相邻窗口合并为一个 EvidenceWindow
        List<EvidenceWindow> windows = mergeCandidates(messages, relationshipId, candidates);

        // 统计每类数量
        Map<EvidenceType, Integer> counts = new LinkedHashMap<>();
        for (EvidenceType t : EvidenceType.values()) counts.put(t, 0);
        for (EvidenceWindow w : windows) {
            for (EvidenceType t : w.getTypes()) counts.merge(t, 1, Integer::sum);
        }
        for (Map.Entry<EvidenceType, Integer> e : counts.entrySet()) {
            if (e.getValue() > 0) {
                System.out.println("[EvidenceDetector] type=" + e.getKey() + " windows=" + e.getValue());
            }
        }
        System.out.println("[EvidenceDetector] Detection finished. rawCandidates=" + candidates.size()
                + ", mergedWindows=" + windows.size());
        return windows;
    }

    // ---------- 私有辅助 ----------

    private boolean checkPhrases(String lower, String[] phrases, EvidenceType type,
                                 String desc, Set<EvidenceType> types, List<EvidenceSignal> signals) {
        boolean hit = false;
        for (String p : phrases) {
            if (lower.contains(p)) {
                types.add(type);
                signals.add(new EvidenceSignal(type.name(), desc + "（命中短语: " + p + "）", null));
                hit = true;
            }
        }
        return hit;
    }

    private boolean containsAny(String lower, String[] phrases) {
        for (String p : phrases) if (lower.contains(p)) return true;
        return false;
    }

    private Candidate findCandidateAt(List<Candidate> list, int idx) {
        for (Candidate c : list) if (c.index == idx) return c;
        return null;
    }

    private Candidate findCandidateByMessageId(List<Candidate> list, Long messageId) {
        for (Candidate c : list) if (c.message.getMessageId().equals(messageId)) return c;
        return null;
    }

    private int indexOf(List<AnalysisMessage> msgs, AnalysisMessage target) {
        for (int i = 0; i < msgs.size(); i++) if (msgs.get(i) == target) return i;
        return 0;
    }

    private enum SessionInitiatorResult { ME, OTHER, NONE }

    private SessionInitiatorResult initiatorOf(ConversationSession s) {
        for (AnalysisMessage m : s.getMessages()) {
            if (m.getSenderType() == SenderType.ME) return SessionInitiatorResult.ME;
            if (m.getSenderType() == SenderType.OTHER) return SessionInitiatorResult.OTHER;
        }
        return SessionInitiatorResult.NONE;
    }

    /** 局部合并：按消息下标排序，重叠或相邻（间隔 <=2 条）的窗口合并为一个。 */
    private List<EvidenceWindow> mergeCandidates(List<AnalysisMessage> messages,
                                                long relationshipId,
                                                List<Candidate> candidates) {
        if (candidates.isEmpty()) return Collections.emptyList();
        candidates.sort((a, b) -> Integer.compare(a.index, b.index));

        List<Merged> merged = new ArrayList<>();
        Merged current = null;

        for (Candidate c : candidates) {
            int lo = Math.max(0, c.index - CONTEXT_BEFORE);
            int hi = Math.min(messages.size() - 1, c.index + CONTEXT_AFTER);
            if (current == null || lo > current.hi + 2) {
                if (current != null) merged.add(current);
                current = new Merged(lo, hi, c);
            } else {
                current.hi = Math.max(current.hi, hi);
                current.lo = Math.min(current.lo, lo);
                current.absorb(c);
            }
        }
        if (current != null) merged.add(current);

        List<EvidenceWindow> out = new ArrayList<>();
        for (Merged m : merged) {
            List<Long> msgIds = new ArrayList<>();
            LocalDateTime start = null, end = null;
            for (int i = m.lo; i <= m.hi; i++) {
                AnalysisMessage am = messages.get(i);
                if (am.getMessageId() != null) msgIds.add(am.getMessageId());
                if (am.getMessageTime() != null) {
                    if (start == null || am.getMessageTime().isBefore(start)) start = am.getMessageTime();
                    if (end == null || am.getMessageTime().isAfter(end)) end = am.getMessageTime();
                }
            }
            List<Long> triggerIds = new ArrayList<>();
            for (Candidate c : m.triggers) {
                if (c.message.getMessageId() != null) triggerIds.add(c.message.getMessageId());
            }
            EvidenceWindow w = new EvidenceWindow(
                    "EV-" + UUID.randomUUID(),
                    relationshipId,
                    m.types,
                    0.0,            // importance 留到下一批
                    EvidenceLevel.C, // level 留到下一批
                    start, end,
                    msgIds, triggerIds,
                    m.signals
            );
            out.add(w);
        }
        return out;
    }

    private static final class Candidate {
        final int index;
        final AnalysisMessage message;
        final Set<EvidenceType> types = EnumSet.noneOf(EvidenceType.class);
        final List<EvidenceSignal> signals = new ArrayList<>();
        Candidate(int index, AnalysisMessage message, Set<EvidenceType> types, List<EvidenceSignal> signals) {
            this.index = index;
            this.message = message;
            this.types.addAll(types);
            this.signals.addAll(signals);
        }
    }

    private static final class Merged {
        int lo, hi;
        final Set<EvidenceType> types = EnumSet.noneOf(EvidenceType.class);
        final List<EvidenceSignal> signals = new ArrayList<>();
        final List<Candidate> triggers = new ArrayList<>();
        Merged(int lo, int hi, Candidate c) {
            this.lo = lo; this.hi = hi;
            absorb(c);
        }
        void absorb(Candidate c) {
            types.addAll(c.types);
            signals.addAll(c.signals);
            triggers.add(c);
        }
    }
}
