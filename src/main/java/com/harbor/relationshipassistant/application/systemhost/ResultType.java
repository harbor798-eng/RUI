package com.harbor.relationshipassistant.application.systemhost;

/**
 * 一次 RUI 执行产出的结果类型。
 *
 * <p>封版集合：不允许在 Runtime 外新增类型。</p>
 * <ul>
 *   <li>{@link #SENDABLE_REPLY} — 给聊天对方、可直接复制粘贴发送的短回复。</li>
 *   <li>{@link #SHORT_ANALYSIS} — 给 RUI 用户、保留 Skill 人格的简短点评。</li>
 *   <li>{@link #ANALYSIS_REPORT} — 给 RUI 用户的完整详细分析报告。</li>
 * </ul>
 */
public enum ResultType {
    SENDABLE_REPLY,
    SHORT_ANALYSIS,
    ANALYSIS_REPORT
}
