package com.harbor.relationshipassistant.domain.analysis;

/**
 * JEVE 一次分析任务的产品类型。
 * <p>
 * 与数据库表 {@code analysis_task.analysis_type}（OBJECTIVE_DATA / MY_PROFILE / ...）
 * 不是同一个概念：本枚举描述的是"用户在 JEVE 里点了哪个入口"，
 * 后者描述的是"DB 中历史遗留的指标分类"。两者不要混用。
 * </p>
 */
public enum AnalysisTaskType {
    /** 快速回复：基于当前会话生成三种策略的回复候选。 */
    QUICK_REPLY,
    /** 详细分析：在主窗口 center 内展示情绪/状态/分析依据。 */
    DETAIL_ANALYSIS,
    /** 深度观察：跨时间窗口的长期行为/关系模式分析。 */
    DEEP_OBSERVATION
}
