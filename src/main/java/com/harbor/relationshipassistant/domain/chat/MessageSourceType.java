package com.harbor.relationshipassistant.domain.chat;

/**
 * 消息来源（PRD §17 来源标签 / 技术设计 §9）。
 * IMPORTED=导入原始；USER_ADDED=用户手动补录；
 * MANUAL=手动新增历史消息（阶段2）；APP=本软件本地输入（不发送真实微信）；
 * AI_GENERATED=AI 原文直发；AI_GENERATED_EDITED=AI 生成后被用户修改发送。
 */
public enum MessageSourceType {
    IMPORTED, USER_ORIGINAL, USER_ADDED, MANUAL, APP, AI_GENERATED, AI_GENERATED_EDITED, AI_ASSISTED, REALTIME_OCR, UNKNOWN
}
