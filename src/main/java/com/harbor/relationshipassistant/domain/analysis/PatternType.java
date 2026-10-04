package com.harbor.relationshipassistant.domain.analysis;

/**
 * V1 行为模式类型。
 * <p>
 * 这些只是"程序检测到的重复/持续/变化现象"，不是心理诊断或关系判断。
 * </p>
 */
public enum PatternType {
    /** 同类行为在分析范围内重复出现。 */
    REPEATED_BEHAVIOR,
    /** 拒绝/边界行为在多个时间点持续出现。 */
    PERSISTENT_REJECTION,
    /** Session 发起分布在前后阶段出现明显变化。 */
    INTERACTION_INITIATIVE_CHANGE,
    /** 回复时间在前后阶段出现明显变化。 */
    RESPONSE_TIME_CHANGE,
    /** 单位时间内 Session 数量发生明显变化。 */
    SESSION_FREQUENCY_CHANGE,
    /** 消息量在前后阶段出现明显变化。 */
    MESSAGE_FREQUENCY_CHANGE,
    /** 多个关系相关重要事件在短时间内集中出现。 */
    RELATIONSHIP_EVENT_CLUSTER,
    /** 多次出现冲突后修复的互动序列。 */
    REPAIR_AFTER_CONFLICT,
    /** 多个时间点出现具有关系互动意义的正向互动。 */
    POSITIVE_INTERACTION_PATTERN
}
