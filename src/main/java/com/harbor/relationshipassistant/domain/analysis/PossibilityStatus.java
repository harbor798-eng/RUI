package com.harbor.relationshipassistant.domain.analysis;

/**
 * {@link Possibility} 的用户确认状态。
 */
public enum PossibilityStatus {
    /** AI 当前只是提出可能性，用户尚未表态。 */
    UNCONFIRMED,
    /** 用户明确确认这个可能性。 */
    USER_CONFIRMED,
    /** 用户明确否定这个可能性。 */
    REJECTED
}
