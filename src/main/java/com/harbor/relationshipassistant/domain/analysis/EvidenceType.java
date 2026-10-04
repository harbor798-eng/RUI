package com.harbor.relationshipassistant.domain.analysis;

/**
 * 证据窗口的类型标签。
 * <p>
 * 一个 EvidenceWindow 可以同时拥有多个类型（例如既是冲突又是修复）。
 * </p>
 */
public enum EvidenceType {
    /** 关系状态/阶段发生变化。 */
    RELATIONSHIP_STATUS_CHANGE,
    /** 明确的关系表达（表白、确认关系、疏远暗示等）。 */
    RELATIONSHIP_EXPRESSION,
    /** 冲突事件。 */
    CONFLICT,
    /** 冲突后的修复行为。 */
    REPAIR,
    /** 重要行动（邀约、兑现、失约、见面等）。 */
    IMPORTANT_ACTION,
    /** 边界、拒绝或被拒绝。 */
    BOUNDARY_OR_REJECTION,
    /** 明确的情绪表达。 */
    EXPLICIT_EMOTION,
    /** 互动模式发生变化。 */
    INTERACTION_PATTERN_CHANGE,
    /** 跨时间持续出现的行为。 */
    PERSISTENT_BEHAVIOR,
    /** 代表性的正向互动。 */
    REPRESENTATIVE_POSITIVE_INTERACTION
}
