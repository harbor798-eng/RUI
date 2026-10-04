package com.harbor.relationshipassistant.infrastructure.persistence;

import com.harbor.relationshipassistant.common.exception.DatabaseException;
import com.harbor.relationshipassistant.domain.ai.AiMessageStatus;
import com.harbor.relationshipassistant.domain.ai.AiGeneratedMessage;
import com.harbor.relationshipassistant.domain.ai.ModificationType;
import com.harbor.relationshipassistant.domain.chat.MessageSourceType;
import com.harbor.relationshipassistant.domain.chat.SenderType;
import com.harbor.relationshipassistant.domain.chat.MessageType;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 回复链路持久化（技术设计 §21-§23）。
 * 关键约束：最终 chat_message.source_ai_message_id 显式关联，不靠文本相似度猜测。
 */
public class AiMessageRepository {

    private final DataSourceFactory ds;
    private final ChatMessageRepository chatMessages;

    public AiMessageRepository(DataSourceFactory ds, ChatMessageRepository chatMessages) {
        this.ds = ds;
        this.chatMessages = chatMessages;
    }

    /** 批量写入一次生成的候选（3 策略 × 3 条 = 9 条）。 */
    public void insertCandidates(Connection c, List<AiGeneratedMessage> candidates) throws SQLException {
        String sql = "INSERT INTO ai_generated_message(relationship_id, context_id, strategy, original_text, " +
                "provider, model, status, created_at) VALUES(?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (AiGeneratedMessage g : candidates) {
                ps.setLong(1, g.getRelationshipId());
                ps.setString(2, g.getContextId());
                ps.setString(3, g.getStrategy().name());
                ps.setString(4, g.getOriginalText());
                ps.setString(5, g.getProvider());
                ps.setString(6, g.getModel());
                ps.setString(7, AiMessageStatus.GENERATED.name());
                ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public AiGeneratedMessage findById(Long id) {
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM ai_generated_message WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                AiGeneratedMessage g = new AiGeneratedMessage();
                g.setId(rs.getLong("id"));
                g.setRelationshipId(rs.getLong("relationship_id"));
                g.setContextId(rs.getString("context_id"));
                g.setStrategy(com.harbor.relationshipassistant.domain.ai.ReplyStrategy.valueOf(rs.getString("strategy")));
                g.setOriginalText(rs.getString("original_text"));
                g.setProvider(rs.getString("provider"));
                g.setModel(rs.getString("model"));
                g.setStatus(AiMessageStatus.valueOf(rs.getString("status")));
                Timestamp t = rs.getTimestamp("selected_at");
                if (t != null) g.setSelectedAt(t.toLocalDateTime());
                t = rs.getTimestamp("sent_at");
                if (t != null) g.setSentAt(t.toLocalDateTime());
                return g;
            }
        } catch (SQLException e) {
            throw new DatabaseException("查询 AI 消息失败", "AiMessageRepository.findById", e);
        }
    }

    /**
     * 发送最终消息（技术设计 §38 事务）：
     * 同一事务内更新 AI 消息状态 + 写 chat_message + 写 edit_analysis。
     */
    public Long sendFinal(Long aiMessageId, String finalText, String algorithmVersion,
                          double similarity, double modificationRate, ModificationType modType) {
        AiGeneratedMessage ai = findById(aiMessageId);
        if (ai == null) throw new DatabaseException("AI 候选不存在: " + aiMessageId, "sendFinal", null);
        LocalDateTime now = LocalDateTime.now();

        try (Connection c = ds.newConnection()) {
            c.setAutoCommit(false);
            try {
                // 1) AI 候选 -> SENT
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE ai_generated_message SET status=?, selected_at=COALESCE(selected_at,?), sent_at=? WHERE id=?")) {
                    ps.setString(1, AiMessageStatus.SENT.name());
                    ps.setTimestamp(2, Timestamp.valueOf(now));
                    ps.setTimestamp(3, Timestamp.valueOf(now));
                    ps.setLong(4, aiMessageId);
                    ps.executeUpdate();
                }
                // 2) 最终聊天消息，显式 source_ai_message_id
                com.harbor.relationshipassistant.domain.chat.ChatMessage finalMsg =
                        new com.harbor.relationshipassistant.domain.chat.ChatMessage();
                finalMsg.setRelationshipId(ai.getRelationshipId());
                finalMsg.setSenderType(SenderType.ME);
                finalMsg.setMessageType(MessageType.TEXT);
                finalMsg.setContent(finalText);
                finalMsg.setMessageTime(now);
                boolean edited = !finalText.equals(ai.getOriginalText());
                finalMsg.setSourceType(edited ? MessageSourceType.AI_GENERATED_EDITED : MessageSourceType.AI_GENERATED);
                finalMsg.setSourceContent(ai.getOriginalText());
                finalMsg.setSourceAiMessageId(aiMessageId);
                chatMessages.insert(c, finalMsg);

                // 3) 修改程度分析，固定 algorithm_version
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO ai_message_edit_analysis(ai_message_id, final_message_id, original_text, final_text, " +
                                "similarity_score, modification_rate, modification_type, algorithm_version, created_at) " +
                                "VALUES(?,?,?,?,?,?,?,?,?)")) {
                    ps.setLong(1, aiMessageId);
                    ps.setLong(2, finalMsg.getId());
                    ps.setString(3, ai.getOriginalText());
                    ps.setString(4, finalText);
                    ps.setDouble(5, similarity);
                    ps.setDouble(6, modificationRate);
                    ps.setString(7, modType.name());
                    ps.setString(8, algorithmVersion);
                    ps.setTimestamp(9, Timestamp.valueOf(now));
                    ps.executeUpdate();
                }
                c.commit();
                return finalMsg.getId();
            } catch (SQLException e) {
                c.rollback();
                throw new DatabaseException("发送最终消息事务失败", "sendFinal", e);
            }
        } catch (SQLException e) {
            throw new DatabaseException("发送最终消息失败", "sendFinal", e);
        }
    }

    /** 用户选择某条候选（进入输入框但尚未发送）。 */
    public void markSelected(Long aiMessageId) {
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE ai_generated_message SET status=?, selected_at=? WHERE id=?")) {
            ps.setString(1, AiMessageStatus.SELECTED.name());
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            ps.setLong(3, aiMessageId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("标记候选选中失败", "markSelected", e);
        }
    }
}
