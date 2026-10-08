package com.harbor.relationshipassistant.application.output;

import com.harbor.relationshipassistant.application.skill.context.AnalysisFunction;
import com.harbor.relationshipassistant.application.systemhost.ResultSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * System Host 权威结果：LLM 原始输出已按 {@link ResultSpec} 解析成 {@link ResultItem} 列表。
 *
 * <p>这是新 Runtime 的权威结果模型。旧的 {@code AnalysisResult}（单字符串）仍保留，
 * 仅作为 JavaFX / 旧 endpoint 的兼容桥接。</p>
 *
 * @param items          解析出的结果；数量可能少于 spec.expectedCount()，缺失即缺失
 * @param parseDegraded  JSON 解析失败 / 数量不足 / 类型不匹配时为 true
 * @param rawContent     LLM 原始文本（调试用，不进入 UI）
 */
public record PlannedResult(
        AnalysisFunction function,
        String skillName,
        ResultSpec spec,
        List<ResultItem> items,
        boolean parseDegraded,
        String rawContent,
        String model,
        LocalDateTime createdAt
) {
    public PlannedResult {
        if (function == null) throw new IllegalArgumentException("function must not be null");
        if (spec == null) throw new IllegalArgumentException("spec must not be null");
        items = items == null ? List.of() : List.copyOf(items);
        rawContent = rawContent == null ? "" : rawContent;
    }
}
