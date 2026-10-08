package com.harbor.capturepoc.recognize;

import com.harbor.capturepoc.CaptureDiag;

import java.util.*;

/**
 * 跨稳定快照的消息实例跟踪器。
 * 负责：Snapshot Alignment → DP 全局匹配 → MessageInstance 状态更新。
 */
public class InstanceTracker {
    // DP params (Phase 1 experimental)
    private static final int MAX_CY_ERR = 50;
    private static final int MAX_CY_ERR_LOW = 80;
    private static final int MAX_PAIR_COST = 100;
    private static final double GAP_MISSING = 30;
    private static final double GAP_ORPHAN = 80;
    private static final double AMBIGUITY_GAP = 15;
    private static final int MISSING_TO_OFFSCREEN = 3;
    private static final int ORPHAN_FRAMES_TO_CREATE = 2;

    public enum Result { OK, AMBIGUOUS, TOO_FEW }

    private long relId;
    private final List<MessageInstance> instances = new ArrayList<>();
    private int seq = 0;

    // pending orphan tracking: key = sender + "|" + normText + "|" + cyBucket
    private final Map<String, Integer> pendingOrphans = new LinkedHashMap<>();

    public void setRelationshipId(long relId) {
        if (this.relId != relId) {
            this.relId = relId;
            instances.clear();
            pendingOrphans.clear();
        }
    }

    public void reset() {
        instances.clear();
        pendingOrphans.clear();
    }

    /**
     * Process a stable snapshot of candidates. Returns OK / AMBIGUOUS / TOO_FEW.
     */
    public Result processSnapshot(List<MessageRecognizer.Candidate> newCands) {
        seq++;
        List<MessageRecognizer.Candidate> news = new ArrayList<>(newCands);
        news.sort(Comparator.comparingInt(c -> (c.y1 + c.y2) / 2));

        List<MessageInstance> oldActive = new ArrayList<>();
        for (MessageInstance inst : instances) {
            if (inst.state != MessageInstance.State.OFF_SCREEN) oldActive.add(inst);
        }

        CaptureDiag.log("[InstanceTracker] snapshot seq=" + seq
                + " old=" + oldActive.size() + " new=" + news.size());

        if (oldActive.size() < 3 || news.size() < 3) {
            // Not enough for alignment; just treat all as fresh (first frame)
            if (oldActive.isEmpty() && !news.isEmpty()) {
                for (MessageRecognizer.Candidate c : news) {
                    if (c.sender == MessageRecognizer.Sender.UNKNOWN) continue;
                    MessageInstance inst = new MessageInstance(relId, c.sender,
                            TextNorm.norm(c.text), (c.y1 + c.y2) / 2, seq);
                    instances.add(inst);
                    CaptureDiag.log("[InstanceCreate] instanceId=" + inst.instanceId
                            + " sender=" + inst.sender + " cy=" + inst.cy + " text=\""
                            + trunc(inst.normText) + "\"");
                }
            }
            return Result.TOO_FEW;
        }

        // Phase 1: coarse shift search
        List<ShiftScore> coarse = new ArrayList<>();
        for (int shift = -600; shift <= 600; shift += 10) {
            int[] r = scoreShift(oldActive, news, shift);
            int matched = r[0];
            double avgDist = r[1];
            double coverage = (double) matched / oldActive.size();
            double composite = coverage * 100 - avgDist;
            coarse.add(new ShiftScore(shift, coverage, avgDist, composite));
        }
        coarse.sort((a, b) -> Double.compare(b.composite, a.composite));

        // Phase 2: refine best shift ±10 step=1
        int bestShift = coarse.get(0).shift;
        double bestComposite = coarse.get(0).composite;
        for (int shift = bestShift - 10; shift <= bestShift + 10; shift++) {
            int[] r = scoreShift(oldActive, news, shift);
            int matched = r[0];
            double avgDist = r[1];
            double coverage = (double) matched / oldActive.size();
            double composite = coverage * 100 - avgDist;
            if (composite > bestComposite) {
                bestComposite = composite;
                bestShift = shift;
            }
        }

        // Re-score top 5 around best for confidence
        List<ShiftScore> top5 = new ArrayList<>();
        for (ShiftScore s : coarse.subList(0, Math.min(5, coarse.size()))) {
            int[] r = scoreShift(oldActive, news, s.shift);
            top5.add(new ShiftScore(s.shift, (double) r[0] / oldActive.size(), r[1],
                    (double) r[0] / oldActive.size() * 100 - r[1]));
        }
        top5.sort((a, b) -> Double.compare(b.composite, a.composite));

        double bestCov = top5.get(0).coverage;
        double peakMargin = top5.size() > 1 ? top5.get(0).composite - top5.get(1).composite : 999;
        String confidence;
        if (bestCov >= 0.8 && peakMargin >= 5) confidence = "HIGH";
        else if (bestCov >= 0.5) confidence = "MEDIUM";
        else confidence = "LOW";

        CaptureDiag.log("[SnapshotAlignment] bestShift=" + bestShift
                + " confidence=" + confidence
                + " coverage=" + String.format("%.2f", bestCov)
                + " avgDist=" + String.format("%.1f", top5.get(0).avgDist));

        // Run DP for top 5 shifts, pick best
        double bestCost = Double.MAX_VALUE;
        int bestDpShift = bestShift;
        int[][] bestParent = null;
        List<DPResult> dpResults = new ArrayList<>();
        for (ShiftScore s : top5) {
            DPResult dr = runDP(oldActive, news, s.shift, confidence);
            dpResults.add(dr);
            CaptureDiag.log("[DP] shift=" + s.shift + " cost=" + String.format("%.1f", dr.cost));
        }
        dpResults.sort(Comparator.comparingDouble(d -> d.cost));
        bestCost = dpResults.get(0).cost;
        bestDpShift = dpResults.get(0).shift;
        bestParent = dpResults.get(0).parent;
        double secondCost = dpResults.size() > 1 ? dpResults.get(1).cost : bestCost + 999;
        double costGap = secondCost - bestCost;

        CaptureDiag.log("[DP] bestCost=" + String.format("%.1f", bestCost)
                + " secondCost=" + String.format("%.1f", secondCost)
                + " costGap=" + String.format("%.1f", costGap));

        if (costGap < AMBIGUITY_GAP) {
            CaptureDiag.log("[InstanceAmbiguous] costGap=" + String.format("%.1f", costGap));
            return Result.AMBIGUOUS;
        }

        // Backtrack using bestParent
        applyDPResult(oldActive, news, bestParent, bestDpShift);
        return Result.OK;
    }

    /** scoreShift: one-to-one greedy pairing, sorted by cy. */
    private int[] scoreShift(List<MessageInstance> old, List<MessageRecognizer.Candidate> news, int shift) {
        int i = 0, j = 0, matched = 0, totalDist = 0;
        while (i < old.size() && j < news.size()) {
            MessageInstance o = old.get(i);
            MessageRecognizer.Candidate n = news.get(j);
            if (o.sender != n.sender) {
                if (o.cy + shift < cy(n)) i++; else j++;
                continue;
            }
            int err = Math.abs(cy(n) - (o.cy + shift));
            if (err <= 25) {
                matched++;
                totalDist += err;
                i++; j++;
            } else {
                if (o.cy + shift < cy(n)) i++; else j++;
            }
        }
        double avgDist = matched > 0 ? (double) totalDist / matched : 999;
        return new int[]{matched, (int) Math.round(avgDist)};
    }

    private static int cy(MessageRecognizer.Candidate c) { return (c.y1 + c.y2) / 2; }

    private static double textSim(String a, String b) {
        String x = TextNorm.norm(a), y = TextNorm.norm(b);
        if (x.equals(y)) return 1.0;
        int m = x.length(), n = y.length();
        if (m == 0 || n == 0) return 0;
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;
        for (int i = 1; i <= m; i++)
            for (int j = 1; j <= n; j++) {
                int cost = x.charAt(i-1) == y.charAt(j-1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i-1][j] + 1, dp[i][j-1] + 1), dp[i-1][j-1] + cost);
            }
        return 1.0 - (double) dp[m][n] / Math.max(m, n);
    }

    private static class ShiftScore {
        int shift; double coverage, avgDist, composite;
        ShiftScore(int s, double c, double a, double comp) { shift=s; coverage=c; avgDist=a; composite=comp; }
    }

    private static class DPResult {
        int shift; double cost; int[][] parent;
        DPResult(int s, double c, int[][] p) { shift=s; cost=c; parent=p; }
    }

    private DPResult runDP(List<MessageInstance> old, List<MessageRecognizer.Candidate> news,
                           int shift, String confidence) {
        int m = old.size(), n = news.size();
        double[][] dp = new double[m+1][n+1];
        int[][] parent = new int[m+1][n+1]; // 0=unset, 1=PAIR, 2=SKIP_OLD, 3=SKIP_NEW
        for (int i = 0; i <= m; i++) { dp[i][0] = i * GAP_MISSING; if (i>0) parent[i][0] = 2; }
        for (int j = 0; j <= n; j++) { dp[0][j] = j * GAP_ORPHAN; if (j>0) parent[0][j] = 3; }

        int maxErr = confidence.equals("LOW") ? MAX_CY_ERR_LOW : MAX_CY_ERR;

        for (int i = 1; i <= m; i++) {
            MessageInstance o = old.get(i-1);
            for (int j = 1; j <= n; j++) {
                MessageRecognizer.Candidate nc = news.get(j-1);
                double skipOld = dp[i-1][j] + GAP_MISSING;
                double skipNew = dp[i][j-1] + GAP_ORPHAN;
                double best = Math.min(skipOld, skipNew);
                int bestParent = skipOld <= skipNew ? 2 : 3;

                if (o.sender == nc.sender) {
                    int cyErr = Math.abs((o.cy + shift) - cy(nc));
                    if (cyErr <= maxErr) {
                        double ts = textSim(o.normText, nc.text);
                        double pairCost = cyErr * 0.5 + (1 - ts) * 200;
                        if (pairCost <= MAX_PAIR_COST) {
                            double pc = dp[i-1][j-1] + pairCost;
                            if (pc < best) { best = pc; bestParent = 1; }
                        }
                    }
                }
                dp[i][j] = best;
                parent[i][j] = bestParent;
            }
        }
        return new DPResult(shift, dp[m][n], parent);
    }

    private void applyDPResult(List<MessageInstance> old, List<MessageRecognizer.Candidate> news,
                               int[][] parent, int shift) {
        int i = old.size(), j = news.size();
        while (i > 0 || j > 0) {
            int p = parent[i][j];
            if (i > 0 && j > 0 && p == 1) {
                MessageInstance o = old.get(i-1);
                MessageRecognizer.Candidate nc = news.get(j-1);
                o.cy = cy(nc);
                o.normText = TextNorm.norm(nc.text);
                o.state = MessageInstance.State.ACTIVE;
                o.missedCount = 0;
                o.seenCount++;
                CaptureDiag.log("[InstanceMatch] OLD " + o.instanceId
                        + " sender=" + o.sender + " -> NEW cy=" + o.cy
                        + " text=\"" + trunc(o.normText) + "\"");
                i--; j--;
            } else if (i > 0 && (p == 2 || j == 0)) {
                MessageInstance o = old.get(i-1);
                o.missedCount++;
                if (o.missedCount >= MISSING_TO_OFFSCREEN) {
                    o.state = MessageInstance.State.OFF_SCREEN;
                } else {
                    o.state = MessageInstance.State.TEMP_MISSING;
                }
                CaptureDiag.log("[InstanceMissing] instance=" + o.instanceId
                        + " sender=" + o.sender + " state=" + o.state
                        + " missed=" + o.missedCount);
                i--;
            } else {
                // orphan new candidate
                MessageRecognizer.Candidate nc = news.get(j-1);
                if (nc.sender == MessageRecognizer.Sender.UNKNOWN) { j--; continue; }
                String key = nc.sender + "|" + TextNorm.norm(nc.text) + "|" + (cy(nc)/50);
                int cnt = pendingOrphans.getOrDefault(key, 0) + 1;
                pendingOrphans.put(key, cnt);
                CaptureDiag.log("[InstanceOrphan] cy=" + cy(nc)
                        + " text=\"" + trunc(nc.text) + "\" pendingCount=" + cnt);
                if (cnt >= ORPHAN_FRAMES_TO_CREATE) {
                    MessageInstance inst = new MessageInstance(relId, nc.sender,
                            TextNorm.norm(nc.text), cy(nc), seq);
                    instances.add(inst);
                    pendingOrphans.remove(key);
                    CaptureDiag.log("[InstanceCreate] instanceId=" + inst.instanceId
                            + " sender=" + inst.sender + " cy=" + inst.cy
                            + " text=\"" + trunc(inst.normText) + "\"");
                }
                j--;
            }
        }
    }

    /** Return all currently-known instances (ACTIVE + TEMP_MISSING) for downstream. */
    public List<MessageInstance> activeInstances() {
        List<MessageInstance> out = new ArrayList<>();
        for (MessageInstance inst : instances) {
            if (inst.state != MessageInstance.State.OFF_SCREEN) out.add(inst);
        }
        out.sort(Comparator.comparingInt(o -> o.cy));
        return out;
    }

    private static String trunc(String s) {
        if (s == null) return "";
        return s.length() > 30 ? s.substring(0, 30) + "..." : s;
    }
}
