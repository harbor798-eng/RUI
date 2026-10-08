package com.harbor.capturepoc.persist;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 直接复用主工程 chat_message 表。
 * 同时写入 message_observation，保证 Canonical Message + Observation 一致性。
 * 幂等：已存在 Canonical 时检查/补建 Observation，不重复创建 Canonical。
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
                "SELECT 1 FROM chat_message WHERE relationship_id=? AND source_message_id=? AND status='ACTIVE' LIMIT 1")) {
            ps.setLong(1, relationshipId);
            ps.setString(2, sourceMessageId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            System.out.println("[Persist][ERROR] exists: " + e.getMessage());
            return false;
        }
    }

    /** 落库一条消息（默认 REALTIME_OCR），返回 messageId。 */
    public long insert(long relationshipId, String sender, String content, String sourceMessageId,
                       LocalDateTime msgTime) {
        return insert(relationshipId, sender, content, sourceMessageId, msgTime, "REALTIME_OCR");
    }

    /**
     * 落库一条消息，可指定 source_type。同时写入 message_observation。
     *
     * 情况 A：Canonical 不存在 → INSERT Canonical + INSERT Observation
     * 情况 B：Canonical 已存在 + Observation 已存在 → 返回已有 id（幂等）
     * 情况 C：Canonical 已存在 + Observation 缺失 → 补建 Observation，返回已有 id
     */
    public long insert(long relationshipId, String sender, String content, String sourceMessageId,
                       LocalDateTime msgTime, String sourceType) {
        LocalDateTime now = LocalDateTime.now();
        String namespace = deriveNamespace(sourceType, sourceMessageId);
        Long externalLocalId = extractExternalLocalId(namespace, sourceMessageId);
        String externalAccountId = extractExternalAccount(sourceMessageId);
        String confidence = namespace.equals("RAPIDOCR") ? "LOW" : "HIGH";
        String resStatus = namespace.equals("RAPIDOCR") ? "PROVISIONAL" : "OBSERVED";

        Connection c = null;
        try {
            c = open();
            c.setAutoCommit(false);

            // Look up existing Canonical Message
            long existingId = findActiveMessageId(c, relationshipId, sourceMessageId);

            if (existingId > 0) {
                // Canonical exists — check Observation
                boolean hasObs = observationExists(c, existingId);
                if (hasObs) {
                    System.out.println("[Dedup] sourceMessageId=" + sourceMessageId + " canonical+obs exist, skip");
                    c.commit();
                    return existingId;
                }
                // Case C: backfill observation for existing Canonical
                System.out.println("[Persist] backfill observation for existing canonical id=" + existingId);
                insertObservation(c, existingId, sourceType, namespace, sourceMessageId,
                        externalAccountId, externalLocalId, content, sender,
                        msgTime == null ? now : msgTime, confidence, resStatus, now);
                c.commit();
                return existingId;
            }

            // Case A: new Canonical
            String msgSql = "INSERT INTO chat_message(relationship_id,sender_type,message_type,content,message_time," +
                    "source_type,source_message_id,source_content,created_at,updated_at,status) " +
                    "VALUES(?,?,?,?,?,?,?,?,?,?, 'ACTIVE')";
            long messageId;
            try (PreparedStatement ps = c.prepareStatement(msgSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, relationshipId);
                ps.setString(2, sender);
                ps.setString(3, "TEXT");
                ps.setString(4, content);
                ps.setObject(5, msgTime == null ? now : msgTime);
                ps.setString(6, sourceType);
                ps.setString(7, sourceMessageId);
                ps.setString(8, content);
                ps.setObject(9, now);
                ps.setObject(10, now);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    messageId = rs.next() ? rs.getLong(1) : -1;
                }
            }
            insertObservation(c, messageId, sourceType, namespace, sourceMessageId,
                    externalAccountId, externalLocalId, content, sender,
                    msgTime == null ? now : msgTime, confidence, resStatus, now);
            c.commit();
            System.out.println("[Persist] created id=" + messageId + " rel=" + relationshipId + " sender=" + sender
                    + " src=" + sourceType + " obs=" + namespace);
            return messageId;
        } catch (SQLException e) {
            if (c != null) try { c.rollback(); } catch (SQLException ignored) {}
            System.out.println("[Persist][ERROR] insert: " + e.getMessage());
            return -1;
        } finally {
            if (c != null) try { c.close(); } catch (SQLException ignored) {}
        }
    }

    private long findActiveMessageId(Connection c, long relationshipId, String sourceMessageId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM chat_message WHERE relationship_id=? AND source_message_id=? AND status='ACTIVE' LIMIT 1")) {
            ps.setLong(1, relationshipId);
            ps.setString(2, sourceMessageId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    private boolean observationExists(Connection c, long messageId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM message_observation WHERE message_id=? LIMIT 1")) {
            ps.setLong(1, messageId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private void insertObservation(Connection c, long messageId, String sourceType, String namespace,
                                   String sourceMessageId, String externalAccountId, Long externalLocalId,
                                   String content, String sender, LocalDateTime observedTime,
                                   String confidence, String resStatus, LocalDateTime now) throws SQLException {
        String obsSql = "INSERT OR IGNORE INTO message_observation" +
                "(message_id,source_type,source_namespace,source_message_id," +
                "external_account_id,external_chat_id,external_local_id," +
                "raw_content,observed_sender,observed_time,confidence,resolution_status," +
                "observed_at,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(obsSql)) {
            ps.setLong(1, messageId);
            ps.setString(2, sourceType);
            ps.setString(3, namespace);
            ps.setString(4, sourceMessageId);
            ps.setString(5, externalAccountId);
            ps.setNull(6, Types.VARCHAR);
            if (externalLocalId != null) ps.setLong(7, externalLocalId);
            else ps.setNull(7, Types.INTEGER);
            ps.setString(8, content);
            ps.setString(9, sender);
            ps.setObject(10, observedTime);
            ps.setString(11, confidence);
            ps.setString(12, resStatus);
            ps.setObject(13, observedTime);
            ps.setObject(14, now);
            ps.setObject(15, now);
            ps.executeUpdate();
        }
    }

    private static String deriveNamespace(String sourceType, String sourceMessageId) {
        if (sourceMessageId != null && sourceMessageId.startsWith("db-")) return "WECHAT_DB";
        if (sourceMessageId != null && sourceMessageId.startsWith("ocr-")) return "RAPIDOCR";
        if ("IMPORTED".equals(sourceType)) return "WECHAT_HTML";
        return "UNKNOWN";
    }

    private static Long extractExternalLocalId(String namespace, String sourceMessageId) {
        if (sourceMessageId == null) return null;
        if ("WECHAT_DB".equals(namespace)) {
            Matcher m = Pattern.compile("-([0-9]+)$").matcher(sourceMessageId);
            return m.find() ? Long.parseLong(m.group(1)) : null;
        }
        if ("WECHAT_HTML".equals(namespace) && sourceMessageId.contains(":")) {
            Matcher m = Pattern.compile(":([0-9]+)$").matcher(sourceMessageId);
            return m.find() ? Long.parseLong(m.group(1)) : null;
        }
        return null;
    }

    private static String extractExternalAccount(String sourceMessageId) {
        if (sourceMessageId == null || !sourceMessageId.startsWith("db-")) return null;
        String[] parts = sourceMessageId.split("-");
        return parts.length >= 3 ? parts[1] : null;
    }

    /** 用户确认后：把 NEEDS_CONFIRM 候选落库。 */
    public long confirm(MessageCandidate c, String sender) {
        return insert(c.relationshipId, sender, c.content, c.sourceMessageId,
                LocalDateTime.now());
    }

    /** 数据库已存在消息（只读，供 HistoryAnchor 序列匹配用）。 */
    public static class DbMessage {
        public long id;
        public String sender;
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
