package com.harbor.relationshipassistant.infrastructure.persistence;

import com.harbor.relationshipassistant.common.exception.DatabaseException;

import java.sql.*;
import java.time.LocalDateTime;

/** 审计日志（技术设计 §43）。严禁记录 API Key / 身份证 / 手机号 / 详细地址。 */
public class AuditLogRepository {

    private final DataSourceFactory ds;

    public AuditLogRepository(DataSourceFactory ds) { this.ds = ds; }

    public void log(Long relationshipId, String operation, String detail) {
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO audit_log(relationship_id, operation, detail, operator, created_at) VALUES(?,?,?,?,?)")) {
            if (relationshipId == null) ps.setNull(1, Types.BIGINT);
            else ps.setLong(1, relationshipId);
            ps.setString(2, operation);
            ps.setString(3, detail);
            ps.setString(4, "USER");
            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("写审计日志失败", "AuditLogRepository.log", e);
        }
    }
}
