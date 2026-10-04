package com.harbor.capturepoc.persist;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 直接复用主工程 chat_message 表（不新建表/不新 migration）。
 * source_type 写 "REALTIME_OCR"（VARCHAR 字段，无需结构变更）。
 * source_message_id 存指纹做幂等：同一条消息重复识别不会重复入库。
 */
public class ChatMessageWriter {
    private final String url, user, pass;

    public ChatMessageWriter(String url, String user, String pass) {
        this.url = url; this.user = user; this.pass = pass;
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, pass);
    }

    /** 幂等：同一关系下 source_message_id 是否已存在。 */
    public boolean exists(long relationshipId, String sourceMessageId) {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM chat_message WHERE relationship_id=? AND source_message_id=? LIMIT 1")) {
            ps.setLong(1, relationshipId);
            ps.setString(2, sourceMessageId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            System.out.println("[Persist][ERROR] exists: " + e.getMessage());
            return false;
        }
    }

    /** 落库一条消息，返回 messageId（已存在则返回 -1）。 */
    public long insert(long relationshipId, String sender, String content, String sourceMessageId,
                       LocalDateTime msgTime) {
        if (exists(relationshipId, sourceMessageId)) {
            System.out.println("[Dedup] sourceMessageId=" + sourceMessageId + " already exists, skip");
            return -1;
        }
        String sql = "INSERT INTO chat_message(relationship_id,sender_type,message_type,content,message_time," +
                "source_type,source_message_id,source_content,created_at,updated_at,status) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?, 'ACTIVE')";
        LocalDateTime now = LocalDateTime.now();
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, relationshipId);
            ps.setString(2, sender);
            ps.setString(3, "TEXT");
            ps.setString(4, content);
            ps.setObject(5, msgTime == null ? now : msgTime);
            ps.setString(6, "REALTIME_OCR");
            ps.setString(7, sourceMessageId);
            ps.setString(8, content);
            ps.setObject(9, now);
            ps.setObject(10, now);
            ps.executeUpdate();
            long id = -1;
            try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) id = rs.getLong(1); }
            System.out.println("[Persist] created id=" + id + " rel=" + relationshipId + " sender=" + sender);
            return id;
        } catch (SQLException e) {
            System.out.println("[Persist][ERROR] insert: " + e.getMessage());
            return -1;
        }
    }

    /** 用户确认后：把 NEEDS_CONFIRM 候选落库。 */
    public long confirm(MessageCandidate c, String sender) {
        return insert(c.relationshipId, sender, c.content, c.sourceMessageId,
                LocalDateTime.now());
    }

    /** 数据库已存在消息（只读，供 HistoryAnchor 序列匹配用）。 */
    public static class DbMessage {
        public long id;
        public String sender;     // ME / OTHER
        public String content;
        public String sourceMessageId;
    }

    /** 按 message_time/id 升序加载最近 limit 条已入库消息（供历史锚定）。 */
    public List<DbMessage> findRecentMessages(long relationshipId, int limit) {
        List<DbMessage> out = new ArrayList<>();
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                "SELECT id,sender_type,content,source_message_id FROM chat_message " +
                "WHERE relationship_id=? AND status='ACTIVE' AND source_type='REALTIME_OCR' " +
                "ORDER BY message_time DESC, id DESC LIMIT ?")) {
            ps.setLong(1, relationshipId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<DbMessage> tmp = new ArrayList<>();
                while (rs.next()) {
                    DbMessage m = new DbMessage();
                    m.id = rs.getLong(1);
                    m.sender = rs.getString(2);
                    m.content = rs.getString(3);
                    m.sourceMessageId = rs.getString(4);
                    tmp.add(m);
                }
                // 反转为升序（旧->新），便于序列匹配
                Collections.reverse(tmp);
                out.addAll(tmp);
            }
        } catch (SQLException e) {
            System.out.println("[Persist][ERROR] findRecentMessages: " + e.getMessage());
        }
        return out;
    }

    public List<String> listRecentTexts(long relationshipId, int limit) {
        List<String> out = new ArrayList<>();
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                "SELECT content FROM chat_message WHERE relationship_id=? AND status='ACTIVE' " +
                "AND message_type IN ('TEXT','EMOJI') ORDER BY message_time DESC,id DESC LIMIT ?")) {
            ps.setLong(1, relationshipId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        } catch (SQLException e) {
            System.out.println("[Context][ERROR] " + e.getMessage());
        }
        return out;
    }
}
