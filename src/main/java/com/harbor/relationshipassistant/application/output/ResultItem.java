package com.harbor.relationshipassistant.application.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.harbor.relationshipassistant.application.systemhost.Audience;
import com.harbor.relationshipassistant.application.systemhost.ResultType;

/**
 * 一次执行解析后产出的一条独立结果。
 *
 * <p>由 {@link ResultJsonParser} 根据 {@code ResultSpec} 展开得到。
 * 字段（type/audience/strategyKey）以 ResultSpec 为准，LLM JSON 里的值被忽略或校验。</p>
 *
 * <p>{@code payload} 用于 ANALYSIS_REPORT 等结构化结果类型，
 * 保留 LLM item 节点上除 type/audience/strategyKey/text 外的兄弟字段
 * （summary/emotions/facts/inferences/possibilities/recommendation/selfCare）。
 * 普通 SENDABLE_REPLY / SHORT_ANALYSIS 的 payload 为 null。</p>
 */
public record ResultItem(
        ResultType type,
        Audience audience,
        String strategyKey,
        int order,
        String text,
        JsonNode payload
) {
    public ResultItem {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        if (audience == null) throw new IllegalArgumentException("audience must not be null");
        if (text == null) text = "";
    }

    public ResultItem(ResultType type, Audience audience, String strategyKey, int order, String text) {
        this(type, audience, strategyKey, order, text, null);
    }
}
