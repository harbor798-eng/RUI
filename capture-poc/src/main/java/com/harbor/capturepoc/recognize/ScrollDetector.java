package com.harbor.capturepoc.recognize;

import java.util.*;

/**
 * 第二层前置：检测整个聊天窗口是否发生了明显的垂直滚动。
 *
 * 使用上一帧完整 visibleList（未经 ActiveDedup 过滤）与当前帧完整 visibleList，
 * 通过 sender + 文本相似度做跨帧匹配，计算匹配消息的 dy。
 *
 * 判定滚动：
 *   - 匹配数 >= 3
 *   - 匹配消息 dy 方向基本一致
 *   - median(|dy|) >= 40px
 * dy < 0 表示向上滚动（翻历史），dy > 0 表示向下滚动（回新消息）。
 *
 * 注意：本类不做去重，只判断"整屏是否平移"。
 */
public class ScrollDetector {

    public static class Result {
        public boolean rolled;
        /** -1 向上, +1 向下, 0 无滚动 */
        public int direction;
        public int matchedCount;
        public double medianAbsDy;
    }

    private static final int MIN_MATCH = 3;
    private static final double MIN_MEDIAN_DY = 40.0;
    private static final double SIM = 0.80;

    private List<MessageRecognizer.Candidate> prev = null;

    /** 会话切换时清空上一帧。 */
    public void reset() { prev = null; }

    public Result detect(List<MessageRecognizer.Candidate> current) {
        Result r = new Result();
        if (prev == null || current == null || current.size() < MIN_MATCH) {
            prev = new ArrayList<>(current);
            r.rolled = false; r.direction = 0;
            return r;
        }

        // 一对一匹配：收集所有 (prev, curr) 可行对，按 cy 距离排序，贪心分配。
        // 这样相同文本的多条消息不会被同一个 prev 重复消费。
        boolean[] usedPrev = new boolean[prev.size()];
        boolean[] usedCurr = new boolean[current.size()];
        List<int[]> pairs = new ArrayList<>();
        for (int ci = 0; ci < current.size(); ci++) {
            MessageRecognizer.Candidate cur = current.get(ci);
            for (int pi = 0; pi < prev.size(); pi++) {
                MessageRecognizer.Candidate p = prev.get(pi);
                if (p.sender != cur.sender) continue;
                double s = sim(p.text, cur.text);
                if (s < SIM) continue;
                int dist = Math.abs(cy(cur) - cy(p));
                pairs.add(new int[]{pi, ci, dist, (int)(s * 1000)});
            }
        }
        // 按 cy 距离升序，距离相同按相似度降序
        pairs.sort((a, b) -> {
            if (a[2] != b[2]) return Integer.compare(a[2], b[2]);
            return Integer.compare(b[3], a[3]);
        });

        List<Integer> dys = new ArrayList<>();
        int up = 0, down = 0;
        for (int[] pair : pairs) {
            int pi = pair[0], ci = pair[1];
            if (usedPrev[pi] || usedCurr[ci]) continue;
            usedPrev[pi] = true;
            usedCurr[ci] = true;
            MessageRecognizer.Candidate p = prev.get(pi);
            MessageRecognizer.Candidate cur = current.get(ci);
            int dy = cy(cur) - cy(p);
            dys.add(dy);
            if (dy < -5) up++;
            else if (dy > 5) down++;
            com.harbor.capturepoc.CaptureDiag.log("[ScrollMatch] prev sender=" + p.sender
                    + " cy=" + cy(p) + " text=\"" + trunc(p.text) + "\""
                    + " -> curr sender=" + cur.sender
                    + " cy=" + cy(cur) + " text=\"" + trunc(cur.text) + "\""
                    + " dy=" + dy + " dist=" + pair[2]);
        }
        for (int ci = 0; ci < current.size(); ci++) {
            if (!usedCurr[ci]) {
                MessageRecognizer.Candidate cur = current.get(ci);
                com.harbor.capturepoc.CaptureDiag.log("[ScrollUnmatchedCurr] sender=" + cur.sender
                        + " cy=" + cy(cur) + " text=\"" + trunc(cur.text) + "\"");
            }
        }
        for (int pi = 0; pi < prev.size(); pi++) {
            if (!usedPrev[pi]) {
                MessageRecognizer.Candidate p = prev.get(pi);
                com.harbor.capturepoc.CaptureDiag.log("[ScrollUnmatchedPrev] sender=" + p.sender
                        + " cy=" + cy(p) + " text=\"" + trunc(p.text) + "\"");
            }
        }

        r.matchedCount = dys.size();
        if (dys.size() < MIN_MATCH) {
            prev = new ArrayList<>(current);
            r.rolled = false; r.direction = 0;
            return r;
        }

        List<Integer> abs = new ArrayList<>();
        for (int d : dys) abs.add(Math.abs(d));
        Collections.sort(abs);
        r.medianAbsDy = abs.get(abs.size() / 2);

        // 方向一致：多数同向，且反向占比小
        boolean dirConsistent = (up >= down * 2) || (down >= up * 2);
        if (r.medianAbsDy < MIN_MEDIAN_DY || !dirConsistent) {
            prev = new ArrayList<>(current);
            r.rolled = false; r.direction = 0;
            return r;
        }

        r.rolled = true;
        r.direction = up > down ? -1 : +1;
        prev = new ArrayList<>(current);
        return r;
    }

    private static int cy(MessageRecognizer.Candidate c) { return (c.y1 + c.y2) / 2; }

    private static String trunc(String s) {
        if (s == null) return "";
        String flat = s.replaceAll("\\s+", " ");
        return flat.length() > 24 ? flat.substring(0, 24) + "..." : flat;
    }

    static double sim(String a, String b) {
        if (a == null || b == null) return 0;
        String x = a.replaceAll("\\s+", "");
        String y = b.replaceAll("\\s+", "");
        if (x.equals(y)) return 1.0;
        int dist = lev(x, y);
        int max = Math.max(x.length(), y.length());
        return max == 0 ? 1.0 : 1.0 - (double) dist / max;
    }

    private static int lev(String a, String b) {
        int m = a.length(), n = b.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;
        for (int i = 1; i <= m; i++)
            for (int j = 1; j <= n; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        return dp[m][n];
    }
}
