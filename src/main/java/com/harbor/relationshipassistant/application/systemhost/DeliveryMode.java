package com.harbor.relationshipassistant.application.systemhost;

/**
 * 一次 RUI 执行的交付形态。
 *
 * <p>THREE_BY_THREE = 默认三策略 System Skill 在 Quick Reply 下的专属交付：
 * NATURAL / PROACTIVE / LIGHT_FLIRT 三策略 × 3 条 = 9 条候选。</p>
 *
 * <p>SINGLE_RESULT = 其他所有情况（User Skill 或非 Quick Reply Function）：
 * 一条最终结果，不套用 3×3。</p>
 *
 * <p>这是工程层的交付契约标记，不是 UI 渲染规则。</p>
 */
public enum DeliveryMode {
    THREE_BY_THREE,
    SINGLE_RESULT
}
