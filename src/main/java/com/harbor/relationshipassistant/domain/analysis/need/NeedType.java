package com.harbor.relationshipassistant.domain.analysis.need;

/**
 * 本次分析任务需要关注的分析方向。
 * <p>
 * 与 {@code EvidenceType}（发现了什么信号）、{@code PatternType}（程序发现了什么重复/变化）
 * 不同，NeedType 表达"接下来应该分析/解释什么"。
 * </p>
 */
public enum NeedType {
    /** 关系状态/阶段变化。 */
    RELATIONSHIP_STATUS_CHANGE,
    /** 关系表达 / 关系态度表达。 */
    RELATIONSHIP_EXPRESSION,
    /** 冲突。 */
    CONFLICT,
    /** 修复。 */
    REPAIR,
    /** 边界 / 拒绝。 */
    BOUNDARY_OR_REJECTION,
    /** 情绪。 */
    EMOTION,
    /** 互动主动性。 */
    INTERACTION_INITIATIVE,
    /** 回复模式。 */
    RESPONSE_PATTERN,
    /** 会话频率。 */
    SESSION_FREQUENCY,
    /** 跨时间持续行为。 */
    PERSISTENT_BEHAVIOR,
    /** 正向互动。 */
    POSITIVE_INTERACTION,
    /** 重要关系事件。 */
    RELATIONSHIP_EVENT,
    /** 用户自身行为。 */
    USER_BEHAVIOR,
    /** 关系变化。 */
    RELATIONSHIP_CHANGE,
    /** 证据不足 / 未知。 */
    UNKNOWN_OR_INSUFFICIENT_EVIDENCE
}
