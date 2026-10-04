package com.harbor.relationshipassistant.infrastructure.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harbor.relationshipassistant.common.exception.BackupException;
import com.harbor.relationshipassistant.infrastructure.persistence.DataSourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 本地备份（技术设计 §46）。V1：把核心表导出为 JSON 打包成 zip。
 * 原始外部聊天备份（微信 SQLite/HTML）不复制。
 */
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final String[] TABLES = {
            "relationship", "relationship_stage_history", "chat_message", "chat_message_revision",
            "chat_import_record", "profile", "profile_item", "ai_profile_observation",
            "profile_nickname_history", "ai_generated_message", "ai_message_edit_analysis",
            "ai_suggestion", "memory_item", "timeline_event", "analysis_task", "analysis_result",
            "audit_log"
    };

    private final DataSourceFactory ds;
    private final ObjectMapper mapper = new ObjectMapper();

    public BackupService(DataSourceFactory ds) { this.ds = ds; }

    public Path backup(Path outputDir) {
        try {
            Files.createDirectories(outputDir);
            String name = "ra-backup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".zip";
            Path out = outputDir.resolve(name);
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out.toFile()))) {
                for (String table : TABLES) {
                    zos.putNextEntry(new ZipEntry(table + ".json"));
                    List<Map<String, Object>> rows = dumpTable(table);
                    zos.write(mapper.writeValueAsBytes(rows));
                    zos.closeEntry();
                }
            }
            log.info("[BACKUP] 备份完成: {}", out);
            return out;
        } catch (Exception e) {
            throw new BackupException("备份失败", "backup", e);
        }
    }

    private List<Map<String, Object>> dumpTable(String table) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection c = ds.newConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM " + table)) {
            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= n; i++) {
                    row.put(md.getColumnLabel(i), rs.getString(i));
                }
                rows.add(row);
            }
        }
        return rows;
    }
}
