package com.harbor.relationshipassistant.infrastructure.importer.wechat;

/**
 * 微信联系人 DTO（来自 chats.db.chats，仅个人聊天）。
 */
public class WechatContact {
    private final String wxid;
    private final String displayName;
    private final long messageCount;
    private final long firstMsgTime;
    private final long lastMsgTime;

    public WechatContact(String wxid, String displayName, long messageCount, long firstMsgTime, long lastMsgTime) {
        this.wxid = wxid;
        this.displayName = displayName;
        this.messageCount = messageCount;
        this.firstMsgTime = firstMsgTime;
        this.lastMsgTime = lastMsgTime;
    }

    public String getWxid() { return wxid; }
    public String getDisplayName() { return displayName; }
    public long getMessageCount() { return messageCount; }
    public long getFirstMsgTime() { return firstMsgTime; }
    public long getLastMsgTime() { return lastMsgTime; }
}
