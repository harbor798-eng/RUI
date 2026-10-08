package com.harbor.capturepoc.recognize;

import java.util.*;

/**
 * 活跃气泡指纹 dedup。
 *
 * 同一微信气泡在连续 frame 中反复被 OCR 捕获，且 OCR 文本可能有轻微波动（末尾多识别一个字符等）。
 * 本类维护最近活跃的气泡指纹，命中则复用 sourceMessageId，并更新 lastSeenAt / cy / normText。
 *
 * 硬条件：relationshipId 相同、sender 相同（ME/OTHER/UNKNOWN 不参与模糊匹配）。
 * 软条件：15 秒窗口、气泡 cy 接近（≤60px）、文本相似度 ≥0.85。
 */
public class ActiveDedup {

    public static class Fingerprint {
        public long relationshipId;
        public MessageRecognizer.Sender sender;
        public String normText;
        public int cy;
        public long lastSeenAt;
        public String sourceMessageId;
    }

    private static final long ACTIVE_WINDOW_MS = 15_000;
    private static final int Y_TOLERANCE_PX = 60;
    private static final double SIMILARITY_THRESHOLD = 0.85;
    private static final int SHORT_TEXT_LEN = 6;

    private final List<Fingerprint> active = new ArrayList<>();

    public enum Decision { NEW, DEDUP }

    public static class Result {
        public Decision decision;
        public String sourceMessageId;
        public double similarity;
        public int dy;
        public String reason;
    }

    public synchronized void reset() {
        active.clear();
    }

    /**
     * @return 决策（NEW 或 DEDUP）。若 DEDUP，复用 fp.sourceMessageId 并更新活跃指纹。
     */
    public synchronized Result check(long relId, MessageRecognizer.Sender sender,
                                      String rawText, int cy, long nowMs) {
        Result r = new Result();
        String norm = TextNorm.norm(rawText);

        // UNKNOWN 不参与模糊 dedup
        if (sender == MessageRecognizer.Sender.UNKNOWN) {
            r.decision = Decision.NEW;
            r.sourceMessageId = newSourceId(relId, sender, norm);
            r.reason = "unknown_sender_new";
            return r;
        }

        // 清理过期指纹
        active.removeIf(fp -> nowMs - fp.lastSeenAt > ACTIVE_WINDOW_MS);

        // 找匹配
        Fingerprint best = null; double bestSim = 0; int bestDy = 0;
        for (Fingerprint fp : active) {
            if (fp.relationshipId != relId) continue;
            if (fp.sender != sender) continue;
            int dy = Math.abs(fp.cy - cy);
            if (dy > Y_TOLERANCE_PX) continue;
            double sim = similarity(fp.normText, norm);
            // 短文本（≤6字）必须精确相等
            if (norm.length() <= SHORT_TEXT_LEN && fp.normText.length() <= SHORT_TEXT_LEN) {
                if (!fp.normText.equals(norm)) continue;
                sim = 1.0;
            } else if (sim < SIMILARITY_THRESHOLD) {
                continue;
            }
            if (sim > bestSim) { best = fp; bestSim = sim; bestDy = dy; }
        }

        if (best != null) {
            // 命中：复用 sourceMessageId，更新 lastSeenAt / cy / normText
            best.lastSeenAt = nowMs;
            best.cy = cy;
            best.normText = norm;
            r.decision = Decision.DEDUP;
            r.sourceMessageId = best.sourceMessageId;
            r.similarity = bestSim;
            r.dy = bestDy;
            r.reason = "match";
            return r;
        }

        // 新消息
        Fingerprint fp = new Fingerprint();
        fp.relationshipId = relId;
        fp.sender = sender;
        fp.normText = norm;
        fp.cy = cy;
        fp.lastSeenAt = nowMs;
        fp.sourceMessageId = newSourceId(relId, sender, norm);
        active.add(fp);
        r.decision = Decision.NEW;
        r.sourceMessageId = fp.sourceMessageId;
        r.similarity = 1.0;
        r.dy = 0;
        r.reason = "new";
        return r;
    }

    /**
     * 稳定 Message Identity：基于 relId + sender + 归一化文本。
     * 不包含 cy / now，因此同一条逻辑消息无论滚动、OCR 抖动、TTL 过期，
     * 都生成同一 sourceMessageId，使 writer.exists() 真正起到 DB 幂等保护。
     */
    private String newSourceId(long relId, MessageRecognizer.Sender sender, String norm) {
        return "ocr-" + Integer.toHexString(
                (relId + "|" + sender.name() + "|" + norm).hashCode());
    }

    /** Levenshtein-based similarity in [0,1]. */
    static double similarity(String a, String b) {
        if (a.equals(b)) return 1.0;
        int dist = levenshtein(a, b);
        int max = Math.max(a.length(), b.length());
        return max == 0 ? 1.0 : 1.0 - (double) dist / max;
    }

    private static int levenshtein(String a, String b) {
        int m = a.length(), n = b.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[m][n];
    }
}
