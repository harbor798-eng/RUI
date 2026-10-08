package com.harbor.relationshipassistant.application.systemhost;

import java.util.List;

/**
 * ResultSpec：一次执行的权威交付契约。
 *
 * <p>由 {@link SystemHostPlanner} 根据 (Function, ActiveSkill) 产出，
 * 是 Runtime / Prompt / Parser / API / UI 共同遵守的唯一事实来源。</p>
 *
 * <p>LLM JSON 里可以带 type/audience/strategyKey 字段，但
 * {@code ResultJsonParser} 必须以本 ResultSpec 为准校验；
 * LLM 不能通过 JSON 改写 type、audience、数量或 strategy。</p>
 */
public record ResultSpec(List<ResultSlot> slots) {

    public ResultSpec {
        if (slots == null) throw new IllegalArgumentException("slots must not be null");
        slots = List.copyOf(slots);
    }

    /** 期望产出的 item 总数（所有 slot.count 求和）。 */
    public int expectedCount() {
        int n = 0;
        for (ResultSlot s : slots) n += s.count();
        return n;
    }

    // ─── 产品契约工厂（封版，不要散落到代码各处） ───────────────────────────────

    /** QUICK_REPLY + default-three-strategy → SENDABLE_REPLY × 9（3 策略 × 3）。 */
    public static ResultSpec quickReplyDefaultThreeStrategy() {
        return new ResultSpec(List.of(
                ResultSlot.ofStrategy(ResultType.SENDABLE_REPLY, Audience.OTHER, 3, "NATURAL"),
                ResultSlot.ofStrategy(ResultType.SENDABLE_REPLY, Audience.OTHER, 3, "PROACTIVE"),
                ResultSlot.ofStrategy(ResultType.SENDABLE_REPLY, Audience.OTHER, 3, "LIGHT_FLIRT")
        ));
    }

    /** QUICK_REPLY + 任意 User Skill（goutoujunshi / quick-reply）→ SENDABLE_REPLY × 1。 */
    public static ResultSpec quickReplySingleSendable() {
        return new ResultSpec(List.of(
                ResultSlot.of(ResultType.SENDABLE_REPLY, Audience.OTHER, 1)
        ));
    }

    /** QUICK_REPLY + tong-jincheng → SENDABLE_REPLY × 1 + SHORT_ANALYSIS × 1。 */
    public static ResultSpec quickReplyTongJincheng() {
        return new ResultSpec(List.of(
                ResultSlot.of(ResultType.SENDABLE_REPLY, Audience.OTHER, 1),
                ResultSlot.of(ResultType.SHORT_ANALYSIS, Audience.USER, 1)
        ));
    }

    /** DETAILED_ANALYSIS + goutoujunshi → ANALYSIS_REPORT × 1。 */
    public static ResultSpec detailAnalysisReport() {
        return new ResultSpec(List.of(
                ResultSlot.of(ResultType.ANALYSIS_REPORT, Audience.USER, 1)
        ));
    }

    /** DETAILED_ANALYSIS + tong-jincheng → SHORT_ANALYSIS × 1。 */
    public static ResultSpec detailShortAnalysis() {
        return new ResultSpec(List.of(
                ResultSlot.of(ResultType.SHORT_ANALYSIS, Audience.USER, 1)
        ));
    }

    /** DEEP_OBSERVATION：当前未在 Result Contract 内，单结果占位。 */
    public static ResultSpec passthroughSingle() {
        return new ResultSpec(List.of(
                ResultSlot.of(ResultType.SHORT_ANALYSIS, Audience.USER, 1)
        ));
    }
}
