package com.harbor.relationshipassistant.infrastructure.importer;

import java.util.ArrayList;
import java.util.List;

/** 一次导入请求。 */
public class ImportRequest {

    private Long relationshipId;
    private String importerType;   // HTML / WECHAT_SQLITE
    private String sourceLocation; // 文件或目录路径
    private String selfNickname;   // 用于判定 ME / OTHER
    // ---- 微信 SQLite 专用 ----
    private String targetChatName; // chats.display_name，例如 "T高改芸"
    private String selfWxid;      // 本人 wxid，例如 wxid_8v7pja8a7nqf22

    public Long getRelationshipId() { return relationshipId; }
    public void setRelationshipId(Long relationshipId) { this.relationshipId = relationshipId; }
    public String getImporterType() { return importerType; }
    public void setImporterType(String importerType) { this.importerType = importerType; }
    public String getSourceLocation() { return sourceLocation; }
    public void setSourceLocation(String sourceLocation) { this.sourceLocation = sourceLocation; }
    public String getSelfNickname() { return selfNickname; }
    public void setSelfNickname(String selfNickname) { this.selfNickname = selfNickname; }
    public String getTargetChatName() { return targetChatName; }
    public void setTargetChatName(String targetChatName) { this.targetChatName = targetChatName; }
    public String getSelfWxid() { return selfWxid; }
    public void setSelfWxid(String selfWxid) { this.selfWxid = selfWxid; }
}
