package com.harbor.relationshipassistant.infrastructure.importer.wechat;

/**
 * 微信 message_0.db 中 Msg_&lt;hash&gt; 表的原始行（只读快照，不回写）。
 *
 * <p>字段含义全部来自对真实备份的结构探测（wx_probe_report.md），
 * 未确认的 local_type 业务含义不在这里写死，只透传原始值。</p>
 */
public class WechatRawMessage {

    /** 来源分片表名，例如 Msg_7fc2e5c4... */
    private String tableName;
    /** 表内自增主键 */
    private long localId;
    /** 服务器消息 ID */
    private long serverId;
    /** 微信 local_type 原始码（1=文本已核验；其余码值含义未完全确认） */
    private long localType;
    /** 毫秒排序戳，导入时按它保证消息顺序 */
    private long sortSeq;
    /** 发送者，对应 message_0.db Name2Id.rowid */
    private long realSenderId;
    /** Unix 秒 */
    private long createTime;
    /** WCDB origin_source，未确认含义，透传 */
    private long originSource;
    /** WCDB_CT_message_content：0=明文，4=zstd BLOB */
    private int contentCompressFlag;
    /** 原始 message_content：可能是 String 或 byte[]，原样保留 */
    private Object rawContent;
    /** 探测后翻译出的发送者 wxid（Name2Id.rowid -> user_name） */
    private String senderWxid;

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public long getLocalId() { return localId; }
    public void setLocalId(long localId) { this.localId = localId; }
    public long getServerId() { return serverId; }
    public void setServerId(long serverId) { this.serverId = serverId; }
    public long getLocalType() { return localType; }
    public void setLocalType(long localType) { this.localType = localType; }
    public long getSortSeq() { return sortSeq; }
    public void setSortSeq(long sortSeq) { this.sortSeq = sortSeq; }
    public long getRealSenderId() { return realSenderId; }
    public void setRealSenderId(long realSenderId) { this.realSenderId = realSenderId; }
    public long getCreateTime() { return createTime; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }
    public long getOriginSource() { return originSource; }
    public void setOriginSource(long originSource) { this.originSource = originSource; }
    public int getContentCompressFlag() { return contentCompressFlag; }
    public void setContentCompressFlag(int contentCompressFlag) { this.contentCompressFlag = contentCompressFlag; }
    public Object getRawContent() { return rawContent; }
    public void setRawContent(Object rawContent) { this.rawContent = rawContent; }
    public String getSenderWxid() { return senderWxid; }
    public void setSenderWxid(String senderWxid) { this.senderWxid = senderWxid; }
}
