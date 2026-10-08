package com.harbor.relationshipassistant.infrastructure.persistence;

import com.harbor.relationshipassistant.common.config.AppConfig;
import com.harbor.relationshipassistant.common.exception.DatabaseException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * DataSource + Flyway bootstrap.
 * <p>
 * SQLite is the default desktop database (Phase 27-B). MySQL remains as an opt-in fallback
 * via db.url=jdbc:mysql://...
 * </p>
 */
public class DataSourceFactory {

    private static final Logger log = LoggerFactory.getLogger(DataSourceFactory.class);

    private final HikariDataSource dataSource;
    private final String safeUrl;
    private final boolean sqlite;
    private final String flywayLocation;

    public DataSourceFactory(AppConfig config) {
        String url = config.dbUrl();
        this.sqlite = url != null && url.startsWith("jdbc:sqlite:");
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(url);
        hc.setPoolName("ra-pool");

        if (sqlite) {
            // Ensure parent dir exists for jdbc:sqlite:data/jeve.db
            try {
                Path dbPath = extractSqlitePath(url);
                if (dbPath != null && dbPath.getParent() != null) {
                    Files.createDirectories(dbPath.getParent());
                }
            } catch (IOException e) {
                throw new IllegalStateException("无法创建 SQLite 数据目录", e);
            }
            hc.setDriverClassName("org.sqlite.JDBC");
            hc.setUsername("");
            hc.setPassword("");
            hc.setMaximumPoolSize(2);
            hc.setConnectionInitSql("PRAGMA foreign_keys = ON; PRAGMA journal_mode = WAL;");
            this.flywayLocation = "classpath:db/migration-sqlite";
            log.info("[DB] databaseType=sqlite");
            log.info("[DB] databasePath={}", url);
            log.info("[DB] connectionPoolSize=2 foreignKeys=ON journalMode=WAL");
        } else {
            hc.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hc.setUsername(config.dbUsername());
            hc.setPassword(config.dbPassword());
            hc.setMaximumPoolSize(5);
            this.flywayLocation = "classpath:db/migration";
            log.info("[DB] databaseType=mysql");
        }
        this.dataSource = new HikariDataSource(hc);
        this.safeUrl = url;

        // SQLite: enforce WAL immediately on first connection too (init SQL covers pooled conns, but be safe).
        if (sqlite) {
            try (Connection c = dataSource.getConnection();
                 Statement st = c.createStatement()) {
                st.execute("PRAGMA journal_mode = WAL");
                st.execute("PRAGMA foreign_keys = ON");
            } catch (SQLException e) {
                throw new DatabaseException("SQLite pragma 初始化失败", "DataSourceFactory", e);
            }
        }
    }

    private static Path extractSqlitePath(String url) {
        // jdbc:sqlite:<path>  (no host:port)
        String prefix = "jdbc:sqlite:";
        if (!url.startsWith(prefix)) return null;
        String rest = url.substring(prefix.length());
        // strip query params
        int q = rest.indexOf('?');
        if (q >= 0) rest = rest.substring(0, q);
        return Path.of(rest).toAbsolutePath().normalize();
    }

    public DataSource dataSource() { return dataSource; }

    public boolean isSqlite() { return sqlite; }

    public void migrate() {
        log.info("[DB] Flyway migrate start, location={}, url={}", flywayLocation, safeUrl);
        Flyway.configure()
                .dataSource(dataSource)
                .locations(flywayLocation)
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
