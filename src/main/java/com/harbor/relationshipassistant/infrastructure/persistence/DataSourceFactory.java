package com.harbor.relationshipassistant.infrastructure.persistence;

import com.harbor.relationshipassistant.common.config.AppConfig;
import com.harbor.relationshipassistant.common.exception.DatabaseException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/** 数据源 + Flyway 迁移启动（技术设计 §47）。 */
public class DataSourceFactory {

    private static final Logger log = LoggerFactory.getLogger(DataSourceFactory.class);

    private final HikariDataSource dataSource;
    private final String safeUrl;

    public DataSourceFactory(AppConfig config) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.dbUrl());
        hc.setUsername(config.dbUsername());
        hc.setPassword(config.dbPassword());
        hc.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hc.setMaximumPoolSize(5);
        hc.setPoolName("ra-pool");
        this.dataSource = new HikariDataSource(hc);
        // 日志只打印 JDBC URL，绝不打印账号密码
        this.safeUrl = config.dbUrl();
    }

    public DataSource dataSource() { return dataSource; }

    /** 启动时执行 Flyway 迁移（建表 / 升级）。 */
    public void migrate() {
        log.info("[DB] Flyway migrate start: {}", safeUrl);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        log.info("[DB] Flyway migrate done");
    }

    public Connection newConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new DatabaseException("获取数据库连接失败", "newConnection", e);
        }
    }
}
