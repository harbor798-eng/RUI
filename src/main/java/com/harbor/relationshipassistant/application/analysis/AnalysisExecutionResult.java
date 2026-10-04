package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.domain.analysis.AnalysisOutput;
import com.harbor.relationshipassistant.domain.analysis.AnalysisResult;
import com.harbor.relationshipassistant.domain.analysis.validation.ValidationIssue;
import com.harbor.relationshipassistant.domain.analysis.validation.ValidationResult;
import com.harbor.relationshipassistant.infrastructure.ai.AIResponse;

import java.util.List;

/**
 * 一次完整分析执行结果。统一持有 {@link AnalysisOutput}（QuickReply / Base / Deep）。
 */
public record AnalysisExecutionResult(boolean success,
                                     int attemptsUsed,
                                     String failureType,
                                     AIResponse rawResponse,
                                     AnalysisOutput output,
                                     ValidationResult validationResult) {

    public static AnalysisExecutionResult ok(int attempts, AIResponse r, AnalysisOutput res, ValidationResult vr) {
        return new AnalysisExecutionResult(true, attempts, null, r, res, vr);
    }

    public static AnalysisExecutionResult fail(int attempts, String failureType,
                                              AIResponse r, AnalysisOutput res, ValidationResult vr) {
        return new AnalysisExecutionResult(false, attempts, failureType, r, res, vr);
    }

    /** 便捷获取 Base AnalysisResult（仅 DETAIL_ANALYSIS / DEEP base）。 */
    public AnalysisResult baseResult() {
        if (output instanceof AnalysisResult ar) return ar;
        if (output instanceof com.harbor.relationshipassistant.domain.analysis.deep.DeepAnalysisResult d) return d.getBase();
        return null;
    }

    public List<ValidationIssue> issues() {
        return validationResult == null ? List.of() : validationResult.getIssues();
    }
}
