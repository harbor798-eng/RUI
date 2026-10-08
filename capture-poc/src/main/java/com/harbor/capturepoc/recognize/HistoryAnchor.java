package com.harbor.capturepoc.recognize;

import com.harbor.capturepoc.persist.ChatMessageWriter;

import java.util.*;

/**
 * 第二层历史已见消息识别（仅在 ScrollDetector 判定 rolled=true 时启用）。
 *
 * 加载数据库该 relationship 最近 N 条消息（按 id/messageTime 升序），
 * 与当前 visibleList 做连续序列匹配。命中段内的消息标记 ROLLBACK_SEEN，
 * 复用 db 中对应 sourceMessageId，不入库；段外的标记 UNCERTAIN，进入 pending 等下一帧确认。
 *
 * 匹配条件：sender 相同 + 文本相似度 >= 0.8。
 * 不使用"sender+text 相同 = 历史消息"这种单点判断。
 */
public class HistoryAnchor {

    public enum Verdict { ROLLBACK_SEEN, UNCERTAIN }

    public static class Outcome {
        public Verdict verdict;
        public String reuseSourceMessageId; // ROLLBACK_SEEN 时复用
    }

    private static final int LOAD_LIMIT = 50;
    private static final double SIM = 0.80;
    private static final int MIN_RUN = 2;

    private final ChatMessageWriter writer;
    private long relationshipId = 2L;

    // pending: key = candidate 标识（sender|normText），value = cy
    private final Map<String, Integer> pending = new LinkedHashMap<>();

    public HistoryAnchor(ChatMessageWriter writer) { this.writer = writer; }

    public void setRelationshipId(long relId) { this.relationshipId = relId; }

    public void reset() { pending.clear(); }

    private static String norm(String s) { return TextNorm.norm(s); }
    private static int cy(MessageRecognizer.Candidate c) { return (c.y1 + c.y2) / 2; }
    private static String key(MessageRecognizer.Candidate c) {
        return c.sender + "|" + norm(c.text);
    }

    /**
     * @param visibleList 当前帧完整可见 candidates（按 cy 升序）
     * @return 与 visibleList 等长的 Outcome 列表
     */
    public List<Outcome> anchor(List<MessageRecognizer.Candidate> visibleList) {
        List<Outcome> out = new ArrayList<>();
        List<ChatMessageWriter.DbMessage> db =
                writer.findRecentMessages(relationshipId, LOAD_LIMIT);

        // 为每条 visible candidate 找 db 匹配位置
        boolean[] anchored = new boolean[visibleList.size()];
        String[] reuseId = new String[visibleList.size()];

        // 在 db 升序序列里找连续匹配段：
        // db[i] 与 visible[j] 若 sender+text 相似则记为匹配，要求连续 run >= MIN_RUN
        for (int j = 0; j < visibleList.size(); j++) {
            MessageRecognizer.Candidate vc = visibleList.get(j);
            int bestI = -1; double bestSim = 0;
            for (int i = 0; i < db.size(); i++) {
                ChatMessageWriter.DbMessage dm = db.get(i);
                if (!dm.sender.equals(vc.sender.name())) continue;
                double s = ScrollDetector.sim(dm.content, vc.text);
                if (s >= SIM && s > bestSim) { bestSim = s; bestI = i; }
            }
            if (bestI >= 0) {
                // 标记为"候选匹配"，稍后检查是否在连续段内
                anchored[j] = true;
                reuseId[j] = db.get(bestI).sourceMessageId;
            }
        }

        // 找连续段（anchored[j]==true 的连续 run），短于 MIN_RUN 的不算
        boolean[] inRun = new boolean[visibleList.size()];
        int runStart = -1;
        for (int j = 0; j <= visibleList.size(); j++) {
            boolean on = (j < visibleList.size()) && anchored[j];
            if (on) { if (runStart < 0) runStart = j; }
            else {
                if (runStart >= 0) {
                    int runLen = j - runStart;
                    if (runLen >= MIN_RUN) {
                        for (int k = runStart; k < j; k++) inRun[k] = true;
                    }
                    runStart = -1;
                }
            }
        }

        // 产出 Outcome
        for (int j = 0; j < visibleList.size(); j++) {
            Outcome o = new Outcome();
            if (inRun[j]) {
                o.verdict = Verdict.ROLLBACK_SEEN;
                o.reuseSourceMessageId = reuseId[j];
            } else {
                o.verdict = Verdict.UNCERTAIN;
            }
            out.add(o);
        }

        // 更新 pending：本帧 UNCERTAIN 的 key 记录下来，供下一帧静止时判断
        pending.clear();
        for (int j = 0; j < visibleList.size(); j++) {
            if (out.get(j).verdict == Verdict.UNCERTAIN) {
                pending.put(key(visibleList.get(j)), cy(visibleList.get(j)));
            }
        }
        return out;
    }

    /** 静止帧（rolled=false）调用：判断一条 NEW candidate 是否上一帧 UNCERTAIN、本帧仍存在。
     *  若是，则说明滚动停止后它仍在，转为 NEW（由调用方决定 INSERT）。
     */
    public boolean wasPending(MessageRecognizer.Candidate c) {
        return pending.containsKey(key(c));
    }

    public void clearPending() { pending.clear(); }
}
