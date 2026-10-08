package com.harbor.relationshipassistant.infrastructure.persistence;

import com.harbor.relationshipassistant.common.exception.DatabaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * app_config KV 表 DAO（V1 仅服务于 wechat.self_wxid）。
 * 表结构：config_key PK, config_value, updated_at。
 */
public class AppConfigRepository {

    private static final Logger log = LoggerFactory.getLogger(AppConfigRepository.class);

    private final DataSourceFactory ds;

    public AppConfigRepository(DataSourceFactory ds) { this.ds = ds; }

    public Optional<String> get(String key) {
        String sql = "SELECT config_value FROM app_config WHERE config_key=?";
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("读取配置失败: " + key, "AppConfigRepository.get", e);
        }
    }

    /**
     * 写入配置；存在则更新，不存在则插入。updated_at 每次刷新。
     * 由于 config_key 是 PRIMARY KEY，直接 INSERT OR REPLACE 即可保证单行。
     */
    public void put(String key, String value) {
        LocalDateTime now = LocalDateTime.now(
                com.harbor.relationshipassistant.infrastructure.importer.wechat.MessageMapper.WECHAT_ZONE);
        String sql = "INSERT OR REPLACE INTO app_config(config_key, config_value, updated_at) VALUES(?,?,?)";
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.setObject(3, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("写入配置失败: " + key, "AppConfigRepository.put", e);
        }
    }
}
