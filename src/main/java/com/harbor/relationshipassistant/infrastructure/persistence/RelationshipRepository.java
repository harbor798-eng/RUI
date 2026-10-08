package com.harbor.relationshipassistant.infrastructure.persistence;

import com.harbor.relationshipassistant.common.exception.DatabaseException;
import com.harbor.relationshipassistant.domain.relationship.Relationship;
import com.harbor.relationshipassistant.domain.relationship.RelationshipStage;
import com.harbor.relationshipassistant.domain.relationship.RelationshipStatus;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RelationshipRepository {

    private final DataSourceFactory ds;

    public RelationshipRepository(DataSourceFactory ds) { this.ds = ds; }

    public Relationship insert(Relationship r) {
        String sql = "INSERT INTO relationship(name, my_name, current_stage, avatar_path, status, goal_note, wechat_wxid, created_at, updated_at) " +
                "VALUES(?,?,?,?,?,?,?,?,?)";
        LocalDateTime now = LocalDateTime.now(
                com.harbor.relationshipassistant.infrastructure.importer.wechat.MessageMapper.WECHAT_ZONE);
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, r.getName());
            ps.setString(2, r.getMyName());
            ps.setString(3, r.getCurrentStage().name());
            ps.setString(4, r.getAvatarPath());
            ps.setString(5, r.getStatus().name());
            ps.setString(6, r.getGoalNote());
            ps.setString(7, r.getWechatWxid());
            ps.setObject(8, now);
            ps.setObject(9, now);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) r.setId(rs.getLong(1));
            }
            r.setCreatedAt(now);
            r.setUpdatedAt(now);
            return r;
        } catch (SQLException e) {
            throw new DatabaseException("创建关系失败", "RelationshipRepository.insert", e);
        }
    }

    public Relationship findById(Long id) {
        String sql = "SELECT * FROM relationship WHERE id=?";
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseException("查询关系失败", "RelationshipRepository.findById", e);
        }
    }

    public List<Relationship> listActive() {
        String sql = "SELECT * FROM relationship WHERE status<>? ORDER BY updated_at DESC";
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, RelationshipStatus.DELETED.name());
            List<Relationship> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new DatabaseException("列出关系失败", "RelationshipRepository.listActive", e);
        }
    }

    /** 更新阶段（同时写阶段历史由 Service 在同一事务完成）。 */
    public void updateStage(Connection c, Long id, RelationshipStage stage) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE relationship SET current_stage=?, updated_at=? WHERE id=?")) {
            ps.setString(1, stage.name());
            ps.setObject(2, LocalDateTime.now(
                    com.harbor.relationshipassistant.infrastructure.importer.wechat.MessageMapper.WECHAT_ZONE));
            ps.setLong(3, id);
            ps.executeUpdate();
        }
    }

    public void writeStageHistory(Connection c, Long relationshipId, RelationshipStage stage) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO relationship_stage_history(relationship_id, stage, started_at, created_by, created_at) " +
                        "VALUES(?,?,?,?,?)")) {
            ps.setLong(1, relationshipId);
            ps.setString(2, stage.name());
            ps.setObject(3, LocalDateTime.now(
                    com.harbor.relationshipassistant.infrastructure.importer.wechat.MessageMapper.WECHAT_ZONE));
            ps.setString(4, "USER");
            ps.setObject(5, LocalDateTime.now(
                    com.harbor.relationshipassistant.infrastructure.importer.wechat.MessageMapper.WECHAT_ZONE));
            ps.executeUpdate();
        }
    }

    public void updateStatus(Long id, RelationshipStatus status) {
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE relationship SET status=?, updated_at=? WHERE id=?")) {
            ps.setString(1, status.name());
            ps.setObject(2, LocalDateTime.now(
                    com.harbor.relationshipassistant.infrastructure.importer.wechat.MessageMapper.WECHAT_ZONE));
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("更新关系状态失败", "RelationshipRepository.updateStatus", e);
        }
    }

    private Relationship map(ResultSet rs) throws SQLException {
        Relationship r = new Relationship();
        r.setId(rs.getLong("id"));
        r.setName(rs.getString("name"));
        r.setMyName(rs.getString("my_name"));
        r.setCurrentStage(RelationshipStage.valueOf(rs.getString("current_stage")));
        r.setAvatarPath(rs.getString("avatar_path"));
        r.setStatus(RelationshipStatus.valueOf(rs.getString("status")));
        r.setGoalNote(rs.getString("goal_note"));
        r.setWechatWxid(rs.getString("wechat_wxid"));
        r.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        r.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
        return r;
    }

    public Optional<Relationship> findByWechatWxid(String wxid) {
        String sql = "SELECT * FROM relationship WHERE wechat_wxid=?";
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, wxid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("按 wxid 查询关系失败", "RelationshipRepository.findByWechatWxid", e);
        }
    }

    public List<Relationship> findByName(String name) {
        String sql = "SELECT * FROM relationship WHERE name=?";
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            List<Relationship> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new DatabaseException("按名称查询关系失败", "RelationshipRepository.findByName", e);
        }
    }

    public void updateWechatWxid(long relationshipId, String wxid) {
        LocalDateTime now = LocalDateTime.now(
                com.harbor.relationshipassistant.infrastructure.importer.wechat.MessageMapper.WECHAT_ZONE);
        String sql = "UPDATE relationship SET wechat_wxid=?, updated_at=? WHERE id=?";
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, wxid);
            ps.setObject(2, now);
            ps.setLong(3, relationshipId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new DatabaseException("绑定 wxid 失败：relationship id=" + relationshipId + " 不存在",
                        "RelationshipRepository.updateWechatWxid", null);
            }
        } catch (SQLException e) {
            throw new DatabaseException("绑定 wxid 失败", "RelationshipRepository.updateWechatWxid", e);
        }
    }
}
