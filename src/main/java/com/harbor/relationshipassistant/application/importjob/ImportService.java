package com.harbor.relationshipassistant.application.importjob;

import com.harbor.relationshipassistant.common.exception.ImportException;
import com.harbor.relationshipassistant.domain.chat.ChatMessage;
import com.harbor.relationshipassistant.domain.chat.MessageSourceType;
import com.harbor.relationshipassistant.infrastructure.importer.ChatImporter;
import com.harbor.relationshipassistant.infrastructure.importer.ImportPreview;
import com.harbor.relationshipassistant.infrastructure.importer.ImportRequest;
import com.harbor.relationshipassistant.infrastructure.importer.ImportedRawMessage;
import com.harbor.relationshipassistant.infrastructure.persistence.AuditLogRepository;
import com.harbor.relationshipassistant.infrastructure.persistence.ChatMessageRepository;
import com.harbor.relationshipassistant.infrastructure.persistence.DataSourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 导入业务（技术设计 §36）：Parse -> Preview -> User Confirm -> 事务写库。
 * 外部原始文件只读，不参与事务。
 */
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final DataSourceFactory ds;
    private final ChatMessageRepository messages;
    private final AuditLogRepository audit;
    private final List<ChatImporter> importers;

    public ImportService(DataSourceFactory ds, ChatMessageRepository messages,
                         AuditLogRepository audit, List<ChatImporter> importers) {
        this.ds = ds;
        this.messages = messages;
        this.audit = audit;
        this.importers = importers;
    }

    public ImportPreview preview(ImportRequest request) {
        ChatImporter importer = importers.stream()
                .filter(i -> i.type().equalsIgnoreCase(request.getImporterType()))
                .findFirst()
                .orElseThrow(() -> new ImportException("不支持的导入类型: " + request.getImporterType(), "ImportService.preview"));
        ImportPreview p = importer.parse(request);
        log.info("[IMPORT] 预览完成 parsed={} errors={}", p.getTotalParsed(), p.getParseErrors().size());
        return p;
    }

    /** 用户确认导入：事务写入，按 (relationship, source_type, source_message_id) 幂等去重。 */
    public int confirm(Long relationshipId, ImportPreview preview) {
        boolean html = "HTML".equalsIgnoreCase(preview.getImporterType());
        String tag = html ? "[HTML_CONFIRM]" : "[IMPORT]";
        LocalDateTime now = LocalDateTime.now(
                com.harbor.relationshipassistant.infrastructure.importer.wechat.MessageMapper.WECHAT_ZONE);
        int inserted = 0, duplicates = 0;
        log.info("{} rel={} file={} parsed={}", tag, relationshipId,
                preview.getSourceLocation(), preview.getTotalParsed());
        try (Connection c = ds.newConnection()) {
            c.setAutoCommit(false);
            try {
                for (ImportedRawMessage raw : preview.getMessages()) {
                    // HTML 无稳定外部行号：用导入期算好的 sourceMessageId（html:sha256(原始时间|原始内容)）；
                    // 为空（Preview 中用户手动新增的行）时回退内容哈希作为幂等键。
                    String sourceId = raw.getSourceMessageId() != null && !raw.getSourceMessageId().isBlank()
                            ? raw.getSourceMessageId() : raw.getSourceHash();
                    if (messages.existsBySource(c, relationshipId, MessageSourceType.IMPORTED, sourceId)) {
                        duplicates++;
                        if (html) log.info("[HTML_DUPLICATE] rel={} id={}", relationshipId, sourceId);
                        continue;
                    }
                    ChatMessage m = new ChatMessage();
                    m.setRelationshipId(relationshipId);
                    m.setSenderType(raw.getSenderType());
                    m.setMessageType(raw.getMessageType());
                    m.setContent(raw.getContent());
                    m.setMessageTime(raw.getMessageTime());
                    m.setSourceType(MessageSourceType.IMPORTED);
                    m.setSourceMessageId(sourceId);
                    m.setSourceHash(raw.getSourceHash());
                    // source_content = 解析出的原始内容；用户在 Preview 里改的是 content，不覆盖原始值
                    m.setSourceContent(raw.getSourceContent() != null ? raw.getSourceContent() : raw.getContent());
                    messages.insert(c, m);
                    inserted++;
                    if (html) log.info("[HTML_INSERT] rel={} id={} sender={} type={}",
                            relationshipId, sourceId, raw.getSenderType(), raw.getMessageType());
                }
                writeImportRecord(c, relationshipId, preview.getImporterType(), preview.getSourceLocation(),
                        preview.getTotalParsed(), inserted, duplicates, now);
                c.commit();
                log.info("{} done inserted={} duplicates={}", tag, inserted, duplicates);
            } catch (SQLException e) {
                c.rollback();
                log.error("[HTML_ERROR] rel={} 导入写库失败已回滚: {}", relationshipId, e.getMessage());
                throw new ImportException("导入写库失败，已回滚", "ImportService.confirm", e);
            }
        } catch (SQLException e) {
            throw new ImportException("导入失败", "ImportService.confirm", e);
        }
        audit.log(relationshipId, "IMPORT_CONFIRM",
                "inserted=" + inserted + " duplicates=" + duplicates);
        return inserted;
    }

    private void writeImportRecord(Connection c, Long relId, String type, String location,
                                   int total, int inserted, int duplicates, LocalDateTime now) throws SQLException {
        try (var ps = c.prepareStatement(
                "INSERT INTO chat_import_record(relationship_id, importer_type, source_location, total_parsed, " +
                        "inserted_count, duplicate_count, status, created_at, confirmed_at) VALUES(?,?,?,?,?,?,?,?,?)")) {
            ps.setLong(1, relId);
            ps.setString(2, type);
            ps.setString(3, location);
            ps.setInt(4, total);
            ps.setInt(5, inserted);
            ps.setInt(6, duplicates);
            ps.setString(7, "CONFIRMED");
            ps.setObject(8, now);
            ps.setObject(9, now);
            ps.executeUpdate();
        }
    }
}
