package com.harbor.relationshipassistant.application.ai;

import com.harbor.relationshipassistant.domain.ai.ModificationType;

/**
 * 修改程度计算（PRD §16 / 技术设计 §23）。
 *
 * V1 算法：基于 Levenshtein 编辑距离。
 *   similarity      = 1 - dist / max(lenA, lenB)
 *   modificationRate = 1 - similarity
 * 算法版本固定为 v1 并落库；未来升级算法时新数据写新版本号，历史数据可解释。
 */
public class ModificationRateCalculator {

    public static final String ALGORITHM_VERSION = "v1-levenshtein";

    public record Result(double similarity, double modificationRate, ModificationType type) {}

    public Result calculate(String original, String finalText) {
        String a = original == null ? "" : original;
        String b = finalText == null ? "" : finalText;
        if (a.isEmpty() && b.isEmpty()) {
            return new Result(1.0, 0.0, ModificationType.NONE);
        }
        int maxLen = Math.max(a.length(), b.length());
        int dist = levenshtein(a, b);
        double similarity = maxLen == 0 ? 1.0 : 1.0 - (double) dist / maxLen;
        similarity = Math.max(0.0, Math.min(1.0, similarity));
        double rate = 1.0 - similarity;
        return new Result(similarity, rate, bucket(rate));
    }

    private ModificationType bucket(double rate) {
        if (rate < 0.05) return ModificationType.NONE;
        if (rate < 0.35) return ModificationType.SMALL;
        if (rate < 0.65) return ModificationType.MEDIUM;
        if (rate < 0.85) return ModificationType.HEAVY;
        return ModificationType.REWRITE;
    }

    static int levenshtein(String a, String b) {
        int[] dp = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) dp[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int prev = dp[0];
            dp[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int temp = dp[j];
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[j] = Math.min(Math.min(dp[j] + 1, dp[j - 1] + 1), prev + cost);
                prev = temp;
            }
        }
        return dp[b.length()];
    }
}
