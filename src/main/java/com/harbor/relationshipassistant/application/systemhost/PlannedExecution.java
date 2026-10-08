package com.harbor.relationshipassistant.application.systemhost;

import com.harbor.relationshipassistant.application.skill.context.AnalysisFunction;

/**
 * System Host 规划结果：一次执行的完整上下文。
 *
 * <p>System Host 不负责恋爱分析方法论，只负责在工程上把：
 * Function（WHAT）+ Active Skill（HOW）+ Delivery（FINAL DELIVERY）
 * 绑定成一个不可变的执行单元。</p>
 *
 * <p>Authority 层级（仅工程概念，不进入 LLM Prompt）：
 * 5 = SystemHost / 4 = Function+Delivery / 3 = ActiveSkill / 2 = Knowledge / 1 = Context。</p>
 *
 * <p>Stage 5-2：新增 {@link #resultSpec()}（权威交付契约）和 {@link #notice()}
 * （产品层提示文案，例如"默认三策略不提供详细分析，本次采用狗头军师"）。
 * {@link #deliveryMode()} 保留为派生标签。</p>
 */
public record PlannedExecution(
        AnalysisFunction function,
        ActiveSkill activeSkill,
        DeliveryMode deliveryMode,
        ResultSpec resultSpec,
        String notice
) {

    /** 兼容旧调用方：ResultSpec 退化为 passthrough single，notice 为空。 */
    public PlannedExecution(AnalysisFunction function, ActiveSkill activeSkill, DeliveryMode deliveryMode) {
        this(function, activeSkill, deliveryMode, ResultSpec.passthroughSingle(), "");
    }

    public PlannedExecution(AnalysisFunction function, ActiveSkill activeSkill,
                            DeliveryMode deliveryMode, ResultSpec resultSpec) {
        this(function, activeSkill, deliveryMode, resultSpec, "");
    }

    public String notice() { return notice == null ? "" : notice; }

    public boolean hasNotice() { return !notice().isBlank(); }
}
