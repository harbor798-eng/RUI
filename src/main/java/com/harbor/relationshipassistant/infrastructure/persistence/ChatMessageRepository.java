package com.harbor.relationshipassistant.infrastructure.persistence;

import com.harbor.relationshipassistant.common.exception.DatabaseException;
import com.harbor.relationshipassistant.domain.chat.ChatMessage;
import com.harbor.relationshipassistant.domain.chat.ChatMessageRevision;
import com.harbor.relationshipassistant.domain.chat.MessageSourceType;
import com.harbor.relationshipassistant.domain.chat.MessageType;
import com.harbor.relationshipassistant.domain.chat.SenderType;
import com.harbor.relationshipassistant.infrastructure.importer.wechat.MessageMapper;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ChatMessageRepository {

    private final DataSourceFactory ds;

    public ChatMessageRepository(DataSourceFactory ds) { this.ds = ds; }

    /** 在给定连接上插入一条消息并回填 id（用于事务内导入/发送）。 */
    public ChatMessage insert(Connection c, ChatMessage m) throws SQLException {
        String sql = "INSERT INTO chat_message(relationship_id, sender_type, message_type, content, message_time, " +
                "source_type, source_message_id, source_hash, source_content, source_ai_message_id, metadata, created_at, updated_at) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)";
        // 审计时间也按 Asia/Shanghai；LocalDateTime 经 setObject 直写墙钟，驱动不做时区换算
        LocalDateTime now = LocalDateTime.now(MessageMapper.WECHAT_ZONE);
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, m.getRelationshipId());
            ps.setString(2, m.getSenderType().name());
            ps.setString(3, m.getMessageType().name());
            ps.setString(4, m.getContent());
            // setObject(LocalDateTime)：原样写入 DATETIME 墙钟字段，不做 JVM/服务器时区转换
            ps.setObject(5, m.getMessageTime() == null ? now : m.getMessageTime());
            ps.setString(6, m.getSourceType().name());
            ps.setString(7, m.getSourceMessageId());
            ps.setString(8, m.getSourceHash());
            ps.setString(9, m.getSourceContent() != null ? m.getSourceContent() : m.getContent());
            if (m.getSourceAiMessageId() == null) ps.setNull(10, Types.BIGINT);
            else ps.setLong(10, m.getSourceAiMessageId());
            ps.setString(11, m.getMetadataJson());
            ps.setObject(12, now);
            ps.setObject(13, now);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) m.setId(rs.getLong(1));
            }
            m.setCreatedAt(now);
            m.setUpdatedAt(now);
            return m;
        }
    }

    /** 独立连接插入（手动新增消息）。 */
    public ChatMessage insertAutoCommit(ChatMessage m) {
        try (Connection c = ds.newConnection()) {
            c.setAutoCommit(true);
            return insert(c, m);
        } catch (SQLException e) {
            throw new DatabaseException("插入消息失败", "ChatMessageRepository.insertAutoCommit", e);
        }
    }

    /** 幂等检查：同一关系下 (source_type, source_message_id) 是否已存在。 */
    public boolean existsBySource(Connection c, Long relationshipId, MessageSourceType sourceType,
                                  String sourceMessageId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM chat_message WHERE relationship_id=? AND source_type=? AND source_message_id=? LIMIT 1")) {
            ps.setLong(1, relationshipId);
            ps.setString(2, sourceType.name());
            ps.setString(3, sourceMessageId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<ChatMessage> listByRelationship(Long relationshipId, int limit) {
        String sql = "SELECT * FROM chat_message WHERE relationship_id=? AND status='ACTIVE' " +
                "ORDER BY message_time DESC LIMIT ?";
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, relationshipId);
            ps.setInt(2, limit);
            List<ChatMessage> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new DatabaseException("查询聊天记录失败", "ChatMessageRepository.listByRelationship", e);
        }
    }

    /** 关系下可见（未软删除）消息总数（分页用）。 */
    public int countByRelationship(Long relationshipId) {
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM chat_message WHERE relationship_id=? AND status='ACTIVE'")) {
            ps.setLong(1, relationshipId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("统计消息失败", "ChatMessageRepository.countByRelationship", e);
        }
    }

    /**
     * 分页查询（ASC，微信时间正序），只返回未软删除消息；同秒按 id 稳定排序。
     */
    /** 最新 N 条（DESC），前端再 reverse。 */
    public List<ChatMessage> findLatest(Long relationshipId, int limit) {
        String sql = "SELECT * FROM chat_message WHERE relationship_id=? AND status='ACTIVE' " +
                "ORDER BY message_time DESC, id DESC LIMIT ?";
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, relationshipId); ps.setInt(2, limit);
            List<ChatMessage> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(map(rs)); }
            return out;
        } catch (SQLException e) {
            throw new DatabaseException("最新消息查询失败", "findLatest", e);
        }
    }

    /** 比 (beforeTime, beforeId) 更早的 N 条（DESC）。 */
    public List<ChatMessage> findOlder(Long relationshipId, java.time.LocalDateTime beforeTime, long beforeId, int limit) {
        String sql = "SELECT * FROM chat_message WHERE relationship_id=? AND status='ACTIVE' " +
                "AND (message_time < ? OR (message_time = ? AND id < ?)) " +
                "ORDER BY message_time DESC, id DESC LIMIT ?";
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, relationshipId);
            ps.setObject(2, beforeTime); ps.setObject(3, beforeTime);
            ps.setLong(4, beforeId); ps.setInt(5, limit);
            List<ChatMessage> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(map(rs)); }
            return out;
        } catch (SQLException e) {
            throw new DatabaseException("历史消息查询失败", "findOlder", e);
        }
    }
    public List<ChatMessage> pageByRelationship(Long relationshipId, int offset, int limit) {
        String sql = "SELECT * FROM chat_message WHERE relationship_id=? AND status='ACTIVE' " +
                "ORDER BY message_time ASC, id ASC LIMIT ? OFFSET ?";
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, relationshipId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            List<ChatMessage> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new DatabaseException("分页查询消息失败", "ChatMessageRepository.pageByRelationship", e);
        }
    }

    /** 按 id 查询单条消息（含已软删除，用于编辑前校验与日志）。 */
    public ChatMessage findById(Long messageId) {
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM chat_message WHERE id=?")) {
            ps.setLong(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new DatabaseException("消息不存在: " + messageId, "findById", null);
                return map(rs);
            }
        } catch (SQLException e) {
            throw new DatabaseException("查询消息失败", "ChatMessageRepository.findById", e);
        }
    }

    /**
     * 某条可见消息在 (message_time ASC, id ASC) 序列中的 0 基下标（定位分页用）。
     */
    public int rankOf(Long relationshipId, Long messageId) {
        String sql = "SELECT COUNT(*) FROM chat_message cm WHERE cm.relationship_id=? AND cm.status='ACTIVE' " +
                "AND (cm.message_time < (SELECT message_time FROM chat_message WHERE id=?) " +
                "OR (cm.message_time = (SELECT message_time FROM chat_message WHERE id=?) AND cm.id < ?))";
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, relationshipId);
            ps.setLong(2, messageId);
            ps.setLong(3, messageId);
            ps.setLong(4, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("计算消息位置失败", "ChatMessageRepository.rankOf", e);
        }
    }

    /**
     * 全量编辑当前有效消息：事务内先写 revision（修改前状态），再更新
     * content / sender_type / message_type / message_time（阶段2）。
     * 原始 source_* 不修改。返回更新后的消息。
     */
    public ChatMessage editMessage(Long messageId, SenderType senderType, MessageType messageType,
                                   String newContent, LocalDateTime newTime) {
        Connection c = null;
        try {
            c = ds.newConnection();
            c.setAutoCommit(false);
            ChatMessage current;
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM chat_message WHERE id=?")) {
                ps.setLong(1, messageId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new DatabaseException("消息不存在: " + messageId, "editMessage", null);
                    current = map(rs);
                }
            }
            LocalDateTime now = LocalDateTime.now(MessageMapper.WECHAT_ZONE);
            try (PreparedStatement rev = c.prepareStatement(
                    "INSERT INTO chat_message_revision(message_id, content, sender_type, message_type, message_time, operation, edited_by, created_at) " +
                            "VALUES(?,?,?,?,?,?,?,?)")) {
                rev.setLong(1, messageId);
                rev.setString(2, current.getContent());
                rev.setString(3, current.getSenderType().name());
                rev.setString(4, current.getMessageType().name());
                rev.setObject(5, current.getMessageTime());
                rev.setString(6, "EDIT");
                rev.setString(7, "USER");
                rev.setObject(8, now);
                rev.executeUpdate();
            }
            try (PreparedStatement up = c.prepareStatement(
                    "UPDATE chat_message SET content=?, sender_type=?, message_type=?, message_time=?, updated_at=? WHERE id=?")) {
                up.setString(1, newContent);
                up.setString(2, senderType.name());
                up.setString(3, messageType.name());
                up.setObject(4, newTime);
                up.setObject(5, now);
                up.setLong(6, messageId);
                up.executeUpdate();
            }
            c.commit();
            ChatMessage updated = new ChatMessage();
            updated.setId(messageId);
            updated.setContent(newContent);
            updated.setSenderType(senderType);
            updated.setMessageType(messageType);
            updated.setMessageTime(newTime);
            return updated;
        } catch (SQLException e) {
            try { if (c != null) c.rollback(); } catch (SQLException ignored) {}
            throw new DatabaseException("修改消息失败", "editMessage", e);
        } finally {
            if (c != null) try { c.close(); } catch (SQLException ignored) {}
        }
    }

    /** 软删除：不物理删除，写 DELETE revision 后置 status=DELETED（原始 source_* 保留）。 */
    public void softDelete(Long messageId) {
        Connection c = null;
        try {
            c = ds.newConnection();
            c.setAutoCommit(false);
            ChatMessage current;
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM chat_message WHERE id=?")) {
                ps.setLong(1, messageId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new DatabaseException("消息不存在: " + messageId, "softDelete", null);
                    current = map(rs);
                }
            }
            LocalDateTime now = LocalDateTime.now(MessageMapper.WECHAT_ZONE);
            try (PreparedStatement rev = c.prepareStatement(
                    "INSERT INTO chat_message_revision(message_id, content, sender_type, message_type, message_time, operation, edited_by, created_at) " +
                            "VALUES(?,?,?,?,?,?,?,?)")) {
                rev.setLong(1, messageId);
                rev.setString(2, current.getContent());
                rev.setString(3, current.getSenderType().name());
                rev.setString(4, current.getMessageType().name());
                rev.setObject(5, current.getMessageTime());
                rev.setString(6, "DELETE");
                rev.setString(7, "USER");
                rev.setObject(8, now);
                rev.executeUpdate();
            }
            try (PreparedStatement up = c.prepareStatement(
                    "UPDATE chat_message SET status='DELETED', updated_at=? WHERE id=?")) {
                up.setObject(1, now);
                up.setLong(2, messageId);
                up.executeUpdate();
            }
            c.commit();
        } catch (SQLException e) {
            try { if (c != null) c.rollback(); } catch (SQLException ignored) {}
            throw new DatabaseException("软删除消息失败", "softDelete", e);
        } finally {
            if (c != null) try { c.close(); } catch (SQLException ignored) {}
        }
    }

    /** 单条消息修改历史（含软删除记录），按修改时间倒序。 */
    public List<ChatMessageRevision> listRevisions(Long messageId) {
        String sql = "SELECT id, message_id, content, sender_type, message_type, message_time, operation, edited_by, created_at " +
                "FROM chat_message_revision WHERE message_id=? ORDER BY created_at DESC, id DESC";
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, messageId);
            List<ChatMessageRevision> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ChatMessageRevision r = new ChatMessageRevision();
                    r.setId(rs.getLong("id"));
                    r.setMessageId(rs.getLong("message_id"));
                    r.setContent(rs.getString("content"));
                    r.setSenderType(SenderType.valueOf(rs.getString("sender_type")));
                    r.setMessageType(MessageType.valueOf(rs.getString("message_type")));
                    r.setMessageTime(rs.getObject("message_time", LocalDateTime.class));
                    r.setOperation(rs.getString("operation"));
                    r.setEditedBy(rs.getString("edited_by"));
                    r.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
                    out.add(r);
                }
            }
            return out;
        } catch (SQLException e) {
            throw new DatabaseException("查询修改历史失败", "ChatMessageRepository.listRevisions", e);
        }
    }

    /**
     * AI Context 专用：取 since 之后 TEXT/EMOJI 消息，正序、最多 limit 条。
     */
    public List<ChatMessage> listRecentForContext(Long relationshipId, LocalDateTime since, int limit) {
        String sql = "SELECT * FROM chat_message WHERE relationship_id=? AND status='ACTIVE' " +
                "AND message_type IN ('TEXT','EMOJI') AND message_time>=? " +
                "ORDER BY message_time ASC, id ASC LIMIT ?";
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, relationshipId);
            ps.setObject(2, since);
            ps.setInt(3, limit);
            List<ChatMessage> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new DatabaseException("查询 Context 消息失败", "listRecentForContext", e);
        }
    }

    /** 最近 N 条 TEXT/EMOJI（24h 无消息时的回退窗口），正序。 */
    public List<ChatMessage> listRecentEmojiText(Long relationshipId, int limit) {
        String sql = "SELECT * FROM (SELECT * FROM chat_message WHERE relationship_id=? AND status='ACTIVE' " +
                "AND message_type IN ('TEXT','EMOJI') ORDER BY message_time DESC, id DESC LIMIT ?) t " +
                "ORDER BY message_time ASC, id ASC";
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, relationshipId);
            ps.setInt(2, limit);
            List<ChatMessage> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new DatabaseException("查询回退聊天失败", "listRecentEmojiText", e);
        }
    }

    /** 统计某关系在最近 N 小时内的可见消息数（用于完整度计算 §12，V1 以有消息的时间窗近似）。 */
    public int countSince(Long relationshipId, LocalDateTime since) {
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM chat_message WHERE relationship_id=? AND message_time>=? AND status='ACTIVE'")) {
            ps.setLong(1, relationshipId);
            ps.setObject(2, since);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("统计消息数失败", "countSince", e);
        }
    }

    /**
     * 回复目标判定用：最新一条 ME/OTHER 的 TEXT/EMOJI 消息。
     * SYSTEM 等消息不作为“我已回复”的依据。
     */
    public ChatMessage findLatestMeaningful(Long relationshipId) {
        String sql = "SELECT * FROM chat_message WHERE relationship_id=? AND status='ACTIVE' " +
                "AND sender_type IN ('ME','OTHER') AND message_type IN ('TEXT','EMOJI') " +
                "ORDER BY message_time DESC, id DESC LIMIT 1";
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, relationshipId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseException("查询最新对话消息失败", "findLatestMeaningful", e);
        }
    }

    private ChatMessage map(ResultSet rs) throws SQLException {
        ChatMessage m = new ChatMessage();
        m.setId(rs.getLong("id"));
        m.setRelationshipId(rs.getLong("relationship_id"));
        m.setSenderType(SenderType.valueOf(rs.getString("sender_type")));
        m.setMessageType(MessageType.valueOf(rs.getString("message_type")));
        m.setContent(rs.getString("content"));
        // getObject(LocalDateTime.class)：直接读 DATETIME 墙钟字段，不做时区转换
        m.setMessageTime(rs.getObject("message_time", LocalDateTime.class));
        m.setSourceType(MessageSourceType.valueOf(rs.getString("source_type")));
        m.setSourceMessageId(rs.getString("source_message_id"));
        m.setSourceHash(rs.getString("source_hash"));
        m.setSourceContent(rs.getString("source_content"));
        long aiId = rs.getLong("source_ai_message_id");
        m.setSourceAiMessageId(rs.wasNull() ? null : aiId);
        m.setMetadataJson(rs.getString("metadata"));
        m.setStatus(rs.getString("status"));
        m.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        m.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
        return m;
    }


    /** 返回 [firstMessageTime, lastMessageTime]；无数据时为 [null, null]。 */
    public LocalDateTime[] getMessageTimeRange(long relationshipId) {
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT MIN(message_time) AS ft, MAX(message_time) AS lt FROM chat_message WHERE relationship_id=?")) {
            ps.setLong(1, relationshipId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new LocalDateTime[]{
                            rs.getObject("ft", LocalDateTime.class),
                            rs.getObject("lt", LocalDateTime.class)};
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to load message time range: " + e.getMessage(), "getMessageTimeRange", e);
        }
        return new LocalDateTime[]{null, null};
    }
}
