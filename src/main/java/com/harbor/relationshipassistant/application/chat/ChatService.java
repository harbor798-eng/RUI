package com.harbor.relationshipassistant.application.chat;

import com.harbor.relationshipassistant.common.exception.ValidationException;
import com.harbor.relationshipassistant.domain.chat.ChatMessage;
import com.harbor.relationshipassistant.domain.chat.MessageSourceType;
import com.harbor.relationshipassistant.domain.chat.MessageType;
import com.harbor.relationshipassistant.domain.chat.SenderType;
import com.harbor.relationshipassistant.infrastructure.importer.wechat.MessageMapper;
import com.harbor.relationshipassistant.infrastructure.persistence.AuditLogRepository;
import com.harbor.relationshipassistant.infrastructure.persistence.ChatMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 聊天业务：手动新增 / 修改 / 列表 / 分页查看 / 完整度（PRD §9/§12）。 */
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatMessageRepository messages;
    private final AuditLogRepository audit;

    public ChatService(ChatMessageRepository messages, AuditLogRepository audit) {
        this.messages = messages;
        this.audit = audit;
    }

    /** 用户手动补录一条历史消息（时间/发送者/类型/内容齐全），source_type=MANUAL（阶段2）。 */
    public ChatMessage addManualMessage(Long relationshipId, SenderType sender, MessageType type,
                                        String content, LocalDateTime time) {
        if (relationshipId == null || content == null || content.isBlank()) {
            throw new ValidationException("关系与内容不能为空", "addManualMessage");
        }
        ChatMessage m = new ChatMessage();
        m.setRelationshipId(relationshipId);
        m.setSenderType(sender == null ? SenderType.OTHER : sender);
        m.setMessageType(type == null ? MessageType.TEXT : type);
        m.setContent(content);
        m.setMessageTime(time == null ? LocalDateTime.now(MessageMapper.WECHAT_ZONE) : time);
        m.setSourceType(MessageSourceType.MANUAL);
        m.setSourceContent(content);
        messages.insertAutoCommit(m);
        audit.log(relationshipId, "CHAT_MANUAL_ADD", "type=" + m.getMessageType());
        log.info("[Chat] 新增消息 id={} rel={} sender={} type={} time={} source=MANUAL",
                m.getId(), relationshipId, m.getSenderType(), m.getMessageType(), m.getMessageTime());
        return m;
    }

    /** 本地聊天输入：只写本软件数据库，不调用微信；sender=ME，source_type=APP，时间为当前（Asia/Shanghai）。 */
    public ChatMessage addLocalMessage(Long relationshipId, String content) {
        if (relationshipId == null || content == null || content.isBlank()) {
            throw new ValidationException("关系与内容不能为空", "addLocalMessage");
        }
        ChatMessage m = new ChatMessage();
        m.setRelationshipId(relationshipId);
        m.setSenderType(SenderType.ME);
        m.setMessageType(MessageType.TEXT);
        m.setContent(content);
        m.setMessageTime(LocalDateTime.now(MessageMapper.WECHAT_ZONE));
        m.setSourceType(MessageSourceType.APP);
        m.setSourceContent(content);
        messages.insertAutoCommit(m);
        audit.log(relationshipId, "CHAT_LOCAL_SEND", "type=TEXT");
        log.info("[Chat] 新增消息 id={} rel={} sender=ME source=APP time={}",
                m.getId(), relationshipId, m.getMessageTime());
        return m;
    }

    /**
     * AI 辅助发送（Phase 3）：source_type=AI_ASSISTED，关联 generationRecordId。
     * 仍只写入本软件数据库，不发送微信。
     */
    public ChatMessage addAiAssistedMessage(Long relationshipId, String content, Long generationRecordId) {
        if (relationshipId == null || content == null || content.isBlank()) {
            throw new ValidationException("关系与内容不能为空", "addAiAssistedMessage");
        }
        ChatMessage m = new ChatMessage();
        m.setRelationshipId(relationshipId);
        m.setSenderType(SenderType.ME);
        m.setMessageType(MessageType.TEXT);
        m.setContent(content);
        m.setMessageTime(LocalDateTime.now(MessageMapper.WECHAT_ZONE));
        m.setSourceType(MessageSourceType.AI_ASSISTED);
        m.setSourceContent(content);
        m.setSourceAiMessageId(generationRecordId);
        messages.insertAutoCommit(m);
        audit.log(relationshipId, "CHAT_AI_SEND", "generationId=" + generationRecordId);
        log.info("[Chat] 新增消息 id={} rel={} sender=ME source=AI_ASSISTED generationId={}",
                m.getId(), relationshipId, generationRecordId);
        return m;
    }

    /**
     * 编辑当前有效消息（阶段2）：先写 revision 再更新，全程事务。
     * 原始 source_* 不修改；UI 按新 message_time + id 重新排序。
     */
    public ChatMessage editMessage(Long messageId, SenderType sender, MessageType type,
                                   String content, LocalDateTime time) {
        if (messageId == null) throw new ValidationException("消息 ID 不能为空", "editMessage");
        log.info("[Chat] 修改开始 messageId={}", messageId);
        ChatMessage before = messages.findById(messageId);
        log.info("[Chat] 原始消息 ID={} 修改前 sender={} type={} time={} content={}",
                before.getId(), before.getSenderType(), before.getMessageType(),
                before.getMessageTime(), truncate(before.getContent()));
        ChatMessage after = messages.editMessage(messageId,
                sender == null ? before.getSenderType() : sender,
                type == null ? before.getMessageType() : type,
                content, time == null ? before.getMessageTime() : time);
        audit.log(before.getRelationshipId(), "CHAT_EDIT",
                "messageId=" + messageId + " before=" + before.getSenderType() + "/" + before.getMessageType() +
                        " after=" + after.getSenderType() + "/" + after.getMessageType());
        log.info("[Chat] revision 创建 messageId={}（修改前状态已入 chat_message_revision）", messageId);
        log.info("[Chat] 修改成功 messageId={} 修改后 sender={} type={} time={} content={}",
                messageId, after.getSenderType(), after.getMessageType(),
                after.getMessageTime(), truncate(after.getContent()));
        return after;
    }

    /** 软删除：UI 默认不显示，原始 source_* 与 revision 保留，可恢复。 */
    public void softDeleteMessage(Long messageId) {
        if (messageId == null) throw new ValidationException("消息 ID 不能为空", "softDeleteMessage");
        log.info("[Chat] 删除消息 messageId={}", messageId);
        ChatMessage before = messages.findById(messageId);
        messages.softDelete(messageId);
        audit.log(before.getRelationshipId(), "CHAT_SOFT_DELETE", "messageId=" + messageId);
        log.info("[Chat] 删除消息完成 messageId={} status=DELETED（原始来源与 revision 保留）", messageId);
    }

    /** 单条消息修改历史（修改时间/修改前内容/发送者/时间/类型/操作）。 */
    public List<ChatMessageRevisionView> listRevisions(Long messageId) {
        if (messageId == null) throw new ValidationException("消息 ID 不能为空", "listRevisions");
        List<ChatMessageRevisionView> views = new java.util.ArrayList<>();
        for (com.harbor.relationshipassistant.domain.chat.ChatMessageRevision r : messages.listRevisions(messageId)) {
            views.add(new ChatMessageRevisionView(r));
        }
        log.info("[Chat] 查看修改历史 messageId={} 共 {} 条", messageId, views.size());
        return views;
    }

    /** 单条消息在 (message_time ASC, id ASC) 序列中的 0 基下标（定位分页用）。 */
    public int rankOf(Long relationshipId, Long messageId) {
        return messages.rankOf(relationshipId, messageId);
    }

    private static String truncate(String s) {
        if (s == null) return "null";
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }

    public List<ChatMessage> listRecent(Long relationshipId, int limit) {
        return messages.listByRelationship(relationshipId, limit);
    }

    /**
     * 分页加载聊天记录（数据来自 MySQL chat_message.content —— 当前有效内容，
     * 不重读 SQLite；source_content 保持只读不被覆盖）。
     */
    public ChatPage loadPage(Long relationshipId, int offset, int limit) {
        try {
            log.info("[Chat] 开始加载 relationship={} offset={} limit={}", relationshipId, offset, limit);
            int total = messages.countByRelationship(relationshipId);
            log.info("[Chat] 查询消息数量 total={}", total);
            int safeOffset = Math.max(0, Math.min(offset, total));
            List<ChatMessage> rows = messages.pageByRelationship(relationshipId, safeOffset, limit);
            if (!rows.isEmpty()) {
                log.info("[Chat] 首条消息时间={}", rows.get(0).getMessageTime());
                log.info("[Chat] 末条消息时间={}", rows.get(rows.size() - 1).getMessageTime());
            }
            return new ChatPage(relationshipId, total, safeOffset, limit, rows);
        } catch (Exception e) {
            log.error("[Chat] 查询异常 relationship={} : {}", relationshipId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 聊天完整度（PRD §12）：V1 以各时间窗内是否有消息做近似指标。
     * 真实算法（按应有消息密度）后续迭代；此处结构先预留。
     */
    public Map<String, Integer> completeness(Long relationshipId) {
        Map<String, Integer> out = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        out.put("1h", messages.countSince(relationshipId, now.minusHours(1)) > 0 ? 100 : 0);
        out.put("24h", messages.countSince(relationshipId, now.minusHours(24)) > 0 ? 100 : 0);
        out.put("3d", messages.countSince(relationshipId, now.minusDays(3)) > 0 ? 80 : 0);
        out.put("7d", messages.countSince(relationshipId, now.minusDays(7)) > 0 ? 60 : 0);
        return out;
    }
}
