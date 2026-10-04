package com.harbor.relationshipassistant.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harbor.relationshipassistant.common.exception.DatabaseException;
import com.harbor.relationshipassistant.domain.observation.Observation;
import com.harbor.relationshipassistant.domain.observation.ObservationBatch;
import com.harbor.relationshipassistant.domain.observation.ObservationEvidence;
import com.harbor.relationshipassistant.domain.observation.ObservationBatchChatSnapshot;
import com.harbor.relationshipassistant.infrastructure.importer.wechat.MessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Observation 仓储（Phase 1：CRUD + 快照复制）。
 * Runner 只能读快照表，严禁通过 source_chat_message_id 回查 chat_message。
 */
public class ObservationRepository {

    private static final Logger log = LoggerFactory.getLogger(ObservationRepository.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final DataSourceFactory ds;

    public ObservationRepository(DataSourceFactory ds) { this.ds = ds; }

    /** 创建 Batch 并在同一事务内复制聊天窗口快照。 */
    public ObservationBatch createBatchWithSnapshot(ObservationBatch b) {
        String insertSql = "INSERT INTO observation_batch(relationship_id,targets_json,range_start,range_end," +
                "status,snapshot_chat_count,context_snapshot_json,started_at,created_at) VALUES(?,?,?,?,?,?,?,?,?)";
        String copySql = "INSERT INTO observation_batch_chat_snapshot" +
                "(batch_id,source_chat_message_id,sender_type,message_type,content,message_time) " +
                "SELECT ?, id, sender_type, message_type, content, message_time FROM chat_message " +
                "WHERE relationship_id=? AND status='ACTIVE' AND message_time BETWEEN ? AND ? ORDER BY message_time, id";
        LocalDateTime now = LocalDateTime.now(MessageMapper.WECHAT_ZONE);
        try (Connection c = ds.newConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, b.getRelationshipId());
                ps.setString(2, JSON.writeValueAsString(b.getTargets()));
                if (b.getRangeStart() == null) ps.setNull(3, Types.TIMESTAMP); else ps.setObject(3, b.getRangeStart());
                if (b.getRangeEnd() == null) ps.setNull(4, Types.TIMESTAMP); else ps.setObject(4, b.getRangeEnd());
                ps.setString(5, "RUNNING");
                ps.setInt(6, 0);
                ps.setString(7, b.getContextSnapshotJson());
                ps.setObject(8, now);
                ps.setObject(9, now);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) b.setId(rs.getLong(1)); }
            }
            int copied;
            try (PreparedStatement ps = c.prepareStatement(copySql)) {
                ps.setLong(1, b.getId());
                ps.setLong(2, b.getRelationshipId());
                ps.setObject(3, b.getRangeStart());
                ps.setObject(4, b.getRangeEnd());
                copied = ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE observation_batch SET snapshot_chat_count=? WHERE id=?")) {
                ps.setInt(1, copied);
                ps.setLong(2, b.getId());
                ps.executeUpdate();
            }
            c.commit();
            b.setSnapshotChatCount(copied);
            b.setStatus("RUNNING");
            b.setStartedAt(now);
            b.setCreatedAt(now);
            log.info("[OBSERVATION_BATCH_INSERT] batchId={} rel={} targets={} snapshotRows={}",
                    b.getId(), b.getRelationshipId(), b.getTargets(), copied);
            return b;
        } catch (Exception e) {
            throw new DatabaseException("创建 ObservationBatch 失败", "ObservationRepository.createBatchWithSnapshot", e);
        }
    }

    public ObservationBatch getBatch(long batchId) {
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM observation_batch WHERE id=?")) {
            ps.setLong(1, batchId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapBatch(rs);
                throw new DatabaseException("Batch 不存在 id=" + batchId, "getBatch", null);
            }
        } catch (SQLException e) {
            throw new DatabaseException("查询 Batch 失败", "getBatch", e);
        }
    }

    public List<ObservationBatch> listBatches(long relationshipId) {
        List<ObservationBatch> out = new ArrayList<>();
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM observation_batch WHERE relationship_id=? ORDER BY id DESC")) {
            ps.setLong(1, relationshipId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapBatch(rs));
            }
        } catch (SQLException e) {
            throw new DatabaseException("查询 Batch 失败", "listBatches", e);
        }
        return out;
    }

    /** Runner 唯一允许的聊天数据来源。 */
    public List<ObservationBatchChatSnapshot> listSnapshots(long batchId) {
        List<ObservationBatchChatSnapshot> out = new ArrayList<>();
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM observation_batch_chat_snapshot WHERE batch_id=? ORDER BY message_time, id")) {
            ps.setLong(1, batchId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ObservationBatchChatSnapshot s = new ObservationBatchChatSnapshot();
                    s.setId(rs.getLong("id"));
                    s.setBatchId(rs.getLong("batch_id"));
                    s.setSourceChatMessageId(rs.getLong("source_chat_message_id"));
                    s.setSenderType(rs.getString("sender_type"));
                    s.setMessageType(rs.getString("message_type"));
                    s.setContent(rs.getString("content"));
                    s.setMessageTime(rs.getObject("message_time", LocalDateTime.class));
                    out.add(s);
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("查询快照失败", "listSnapshots", e);
        }
        return out;
    }

    public Observation insertObservation(Observation o) {
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO observation(batch_id,subject,ai_raw_text,created_at) VALUES(?,?,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            LocalDateTime now = LocalDateTime.now(MessageMapper.WECHAT_ZONE);
            ps.setLong(1, o.getBatchId());
            ps.setString(2, o.getSubject());
            ps.setString(3, o.getAiRawText());
            ps.setObject(4, now);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) o.setId(rs.getLong(1)); }
            o.setCreatedAt(now);
            return o;
        } catch (SQLException e) {
            throw new DatabaseException("插入 Observation 失败", "insertObservation", e);
        }
    }

    public ObservationEvidence insertEvidence(ObservationEvidence e) {
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO observation_evidence(observation_id,evidence_type,evidence_snapshot,created_at) VALUES(?,?,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            LocalDateTime now = LocalDateTime.now(MessageMapper.WECHAT_ZONE);
            ps.setLong(1, e.getObservationId());
            ps.setString(2, e.getEvidenceType());
            ps.setString(3, e.getEvidenceSnapshot());
            ps.setObject(4, now);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) e.setId(rs.getLong(1)); }
            e.setCreatedAt(now);
            return e;
        } catch (SQLException ex) {
            throw new DatabaseException("插入 Evidence 失败", "insertEvidence", ex);
        }
    }

    public List<Observation> listObservations(long batchId) {
        List<Observation> out = new ArrayList<>();
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM observation WHERE batch_id=? ORDER BY id")) {
            ps.setLong(1, batchId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapObservation(rs));
            }
        } catch (SQLException e) {
            throw new DatabaseException("查询 Observation 失败", "listObservations", e);
        }
        return out;
    }

    public void finishBatch(long batchId, String status, long elapsedMs) {
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE observation_batch SET status=?,finished_at=?,elapsed_ms=? WHERE id=?")) {
            ps.setString(1, status);
            ps.setObject(2, LocalDateTime.now(MessageMapper.WECHAT_ZONE));
            ps.setLong(3, elapsedMs);
            ps.setLong(4, batchId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("更新 Batch 状态失败", "finishBatch", e);
        }
    }

    /** 开始 Batch 前的预检：时间窗内有效聊天行数（只读当前 chat_message）。 */
    public int countLiveChat(long relationshipId, LocalDateTime start, LocalDateTime end) {
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM chat_message WHERE relationship_id=? AND status='ACTIVE' AND message_time BETWEEN ? AND ?")) {
            ps.setLong(1, relationshipId);
            ps.setObject(2, start);
            ps.setObject(3, end);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("预检聊天数失败", "countLiveChat", e);
        }
    }

    /** 最近一次 Batch（状态轮询用）。 */
    public ObservationBatch latestBatch(long relationshipId) {
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM observation_batch WHERE relationship_id=? ORDER BY id DESC LIMIT 1")) {
            ps.setLong(1, relationshipId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapBatch(rs) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseException("查询最近 Batch 失败", "latestBatch", e);
        }
    }

    public List<ObservationEvidence> listEvidence(long observationId) {
        List<ObservationEvidence> out = new ArrayList<>();
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM observation_evidence WHERE observation_id=? ORDER BY id")) {
            ps.setLong(1, observationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ObservationEvidence e = new ObservationEvidence();
                    e.setId(rs.getLong("id"));
                    e.setObservationId(rs.getLong("observation_id"));
                    e.setEvidenceType(rs.getString("evidence_type"));
                    e.setEvidenceSnapshot(rs.getString("evidence_snapshot"));
                    e.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
                    out.add(e);
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("查询 Evidence 失败", "listEvidence", e);
        }
        return out;
    }

    /** 一次性修改申请守卫：edit_submitted_at 已存在则更新 0 行。 */
    public boolean submitEdit(long observationId, String editedText, String reason,
                              boolean includeLongterm, String longtermVersion) {
        String version = null;
        if (includeLongterm) {
            version = "USER_EDITED".equals(longtermVersion) ? "USER_EDITED" : "AI_RAW";
        }
        String sql = "UPDATE observation SET user_edited_text=?,edit_reason=?,include_in_longterm=?," +
                "longterm_version=?,edit_submitted_at=? WHERE id=? AND edit_submitted_at IS NULL";
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, editedText);
            ps.setString(2, reason);
            ps.setBoolean(3, includeLongterm);
            ps.setString(4, version);
            ps.setObject(5, LocalDateTime.now(MessageMapper.WECHAT_ZONE));
            ps.setLong(6, observationId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("提交修改申请失败", "submitEdit", e);
        }
    }

    /** 长期记忆：当前关系下所有纳入长期观察的 Observation。 */
    public List<Observation> listLongtermObservations(long relationshipId) {
        List<Observation> out = new ArrayList<>();
        String sql = "SELECT o.* FROM observation o JOIN observation_batch b ON o.batch_id=b.id " +
                "WHERE b.relationship_id=? AND o.include_in_longterm=1 AND o.longterm_version IS NOT NULL " +
                "ORDER BY o.id";
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, relationshipId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapObservation(rs));
            }
        } catch (SQLException e) {
            throw new DatabaseException("查询长期观察失败", "listLongtermObservations", e);
        }
        return out;
    }

    public void markFailed(long batchId, String errorCode, String errorMessage) {
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE observation_batch SET status='FAILED',finished_at=?,error_code=?,error_message=? WHERE id=?")) {
            ps.setObject(1, LocalDateTime.now(MessageMapper.WECHAT_ZONE));
            ps.setString(2, errorCode);
            ps.setString(3, errorMessage == null ? null : errorMessage.substring(0, Math.min(500, errorMessage.length())));
            ps.setLong(4, batchId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("标记 Batch 失败失败", "markFailed", e);
        }
    }

    private ObservationBatch mapBatch(ResultSet rs) throws SQLException {
        ObservationBatch b = new ObservationBatch();
        b.setId(rs.getLong("id"));
        b.setRelationshipId(rs.getLong("relationship_id"));
        try {
            String tj = rs.getString("targets_json");
            if (tj != null && !tj.isBlank()) {
                b.setTargets(JSON.readValue(tj, new TypeReference<List<String>>() {}));
            }
        } catch (Exception ignore) { }
        b.setRangeStart(rs.getObject("range_start", LocalDateTime.class));
        b.setRangeEnd(rs.getObject("range_end", LocalDateTime.class));
        b.setStatus(rs.getString("status"));
        b.setSnapshotChatCount(rs.getInt("snapshot_chat_count"));
        b.setContextSnapshotJson(rs.getString("context_snapshot_json"));
        b.setStartedAt(rs.getObject("started_at", LocalDateTime.class));
        b.setFinishedAt(rs.getObject("finished_at", LocalDateTime.class));
        long e = rs.getLong("elapsed_ms");
        b.setElapsedMs(rs.wasNull() ? null : e);
        b.setErrorCode(rs.getString("error_code"));
        b.setErrorMessage(rs.getString("error_message"));
        b.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        return b;
    }

    private Observation mapObservation(ResultSet rs) throws SQLException {
        Observation o = new Observation();
        o.setId(rs.getLong("id"));
        o.setBatchId(rs.getLong("batch_id"));
        o.setSubject(rs.getString("subject"));
        o.setAiRawText(rs.getString("ai_raw_text"));
        o.setUserEditedText(rs.getString("user_edited_text"));
        o.setEditReason(rs.getString("edit_reason"));
        o.setIncludeInLongterm(rs.getBoolean("include_in_longterm"));
        o.setLongtermVersion(rs.getString("longterm_version"));
        o.setEditSubmittedAt(rs.getObject("edit_submitted_at", LocalDateTime.class));
        o.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        return o;
    }
}
