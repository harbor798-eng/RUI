package com.harbor.relationshipassistant.infrastructure.persistence;

import com.harbor.relationshipassistant.common.exception.DatabaseException;
import com.harbor.relationshipassistant.domain.ai.GenerationRecord;
import com.harbor.relationshipassistant.infrastructure.importer.wechat.MessageMapper;

import java.sql.*;
import java.time.LocalDateTime;

/** GenerationRecord 仓储（Phase 3）。 */
public class GenerationRecordRepository {

    private final DataSourceFactory ds;

    public GenerationRecordRepository(DataSourceFactory ds) { this.ds = ds; }

    public GenerationRecord insert(GenerationRecord r) {
        String sql = "INSERT INTO ai_generation_record(relationship_id, request_id, stage, provider, model, " +
                "candidate_count, candidates_snapshot, context_snapshot, created_at) VALUES(?,?,?,?,?,?,?,?,?)";
        LocalDateTime now = LocalDateTime.now(MessageMapper.WECHAT_ZONE);
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, r.getRelationshipId());
            ps.setString(2, r.getRequestId());
            ps.setString(3, r.getStage());
            ps.setString(4, r.getProvider());
            ps.setString(5, r.getModel());
            ps.setInt(6, r.getCandidateCount());
            ps.setString(7, r.getCandidatesSnapshot());
            ps.setString(8, r.getContextSnapshot());
            ps.setObject(9, now);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) r.setId(rs.getLong(1));
            }
            r.setCreatedAt(now);
            return r;
        } catch (SQLException e) {
            throw new DatabaseException("创建 GenerationRecord 失败", "GenerationRecordRepository.insert", e);
        }
    }

    /** 用户点击「使用这条」：写入选择。 */
    public void markSelected(Long id, String strategy, int index, String originalText) {
        String sql = "UPDATE ai_generation_record SET selected_strategy=?, selected_candidate_index=?, " +
                "selected_original_text=?, selected_at=? WHERE id=?";
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, strategy);
            ps.setInt(2, index);
            ps.setString(3, originalText);
            ps.setObject(4, LocalDateTime.now(MessageMapper.WECHAT_ZONE));
            ps.setLong(5, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("更新选择失败", "markSelected", e);
        }
    }

    /** 发送前/离开前持久化最终文本与修改标记。 */
    public void updateFinalText(Long id, String finalText, boolean modified) {
        String sql = "UPDATE ai_generation_record SET final_text=?, modified=? WHERE id=?";
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, finalText);
            ps.setBoolean(2, modified);
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("更新 final_text 失败", "updateFinalText", e);
        }
    }

    /** 发送完成：关联 ChatMessage。 */
    public void markSent(Long id, Long chatMessageId) {
        String sql = "UPDATE ai_generation_record SET sent_message_id=?, sent_at=? WHERE id=?";
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, chatMessageId);
            ps.setObject(2, LocalDateTime.now(MessageMapper.WECHAT_ZONE));
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("标记发送失败", "markSent", e);
        }
    }
}
