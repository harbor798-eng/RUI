package com.harbor.relationshipassistant.domain.analysis.interaction;

/**
 * Session 发起方。
 * <p>
 * Session 的第一条"有效互动消息"（ME 或 OTHER）的发送者即为发起方。
 * 若 Session 开头是 SYSTEM 消息，则发起方为 {@link #NONE}——
 * SYSTEM 参与 Session 时间连续性，但不视为 ME 或 OTHER 发起。
 * </p>
 */
public enum SessionInitiator {
    ME,
    OTHER,
    NONE
}
