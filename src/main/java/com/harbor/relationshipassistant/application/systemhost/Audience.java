package com.harbor.relationshipassistant.application.systemhost;

/**
 * 结果受众：这条文本最终是给谁看的。
 *
 * <ul>
 *   <li>{@link #OTHER} — 聊天对方。文本必须是可发送的话，不能混入给 RUI 用户的建议/分析。</li>
 *   <li>{@link #USER} — RUI 用户本人。可以是分析、判断、点评。</li>
 * </ul>
 */
public enum Audience {
    OTHER,
    USER
}
