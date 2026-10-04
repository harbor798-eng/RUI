package com.harbor.relationshipassistant.application.analysis;

/**
 * Retry 策略。V1：最多 3 次总尝试（首次 + 最多 2 次重试）。
 */
public class AnalysisRetryPolicy {

    private final int maxAttempts;

    public AnalysisRetryPolicy() {
        this(3);
    }

    public AnalysisRetryPolicy(int maxAttempts) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
        this.maxAttempts = maxAttempts;
    }

    public int getMaxAttempts() { return maxAttempts; }

    public boolean shouldRetry(int attempt, Throwable parseError,
                               com.harbor.relationshipassistant.domain.analysis.validation.ValidationResult vr) {
        if (attempt >= maxAttempts) return false;
        // Provider 层异常（AIException 等）由调用方决定；本方法只覆盖 parse/validation。
        if (parseError != null) return true;
        if (vr != null && !vr.isValid()) return true;
        return false;
    }
}
