package com.harbor.relationshipassistant.application.systemhost;

/**
 * 一个交付槽位：本次执行"期望"产出多少条什么类型、给谁、按什么策略分组。
 *
 * <p>{@code count} 是<strong>期望数量</strong>，不是最终结果。
 * 最终 {@code ResultItem} 由 {@code ResultJsonParser} 在 LLM 返回后展开得到；
 * 如果 LLM 数量不足，缺失槽位保持缺失，并标记 {@code parseDegraded=true}，
 * <strong>不</strong>用未知文本冒充 SENDABLE_REPLY。</p>
 *
 * @param type        结果类型（权威，以 ResultSpec 为准）
 * @param audience    受众（权威，以 ResultSpec 为准）
 * @param count       期望数量
 * @param strategyKey  仅 THREE_BY_THREE 用：NATURAL / PROACTIVE / LIGHT_FLIRT；其它为 null
 */
public record ResultSlot(ResultType type, Audience audience, int count, String strategyKey) {

    public ResultSlot {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        if (audience == null) throw new IllegalArgumentException("audience must not be null");
        if (count < 0) throw new IllegalArgumentException("count must be >= 0");
    }

    public static ResultSlot of(ResultType type, Audience audience, int count) {
        return new ResultSlot(type, audience, count, null);
    }

    public static ResultSlot ofStrategy(ResultType type, Audience audience, int count, String strategyKey) {
        return new ResultSlot(type, audience, count, strategyKey);
    }
}
