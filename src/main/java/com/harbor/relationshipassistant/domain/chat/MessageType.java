package com.harbor.relationshipassistant.domain.chat;

/** 消息类型（PRD §8，含预留扩展）。 */
public enum MessageType {
    TEXT, IMAGE, EMOJI, VIDEO, VOICE, FILE, CALL, TRANSFER, SYSTEM,
    LOCATION, RED_PACKET, LINK, STICKER, OTHER
}
