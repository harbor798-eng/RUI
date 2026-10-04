package com.harbor.relationshipassistant.domain.analysis;

/**
 * Fact 的来源类型。
 * <p>
 * 不同类型的 Fact 必须有不同的来源字段：
 * <ul>
 *   <li>{@link #CHAT}：来自原始聊天记录，必须能通过 evidenceIds 追溯。</li>
 *   <li>{@link #STATISTICS}：程序统计得到，通过 statisticKeys 关联统计指标。</li>
 *   <li>{@link #USER_CONFIRMED}：用户已确认的档案信息，关联 profileField。</li>
 * </ul>
 * </p>
 */
public enum FactType {
    CHAT,
    STATISTICS,
    USER_CONFIRMED
}
