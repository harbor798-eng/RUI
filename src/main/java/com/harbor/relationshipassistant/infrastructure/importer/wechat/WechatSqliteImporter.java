package com.harbor.relationshipassistant.infrastructure.importer.wechat;

import com.harbor.relationshipassistant.common.exception.ImportException;
import com.harbor.relationshipassistant.domain.chat.SenderType;
import com.harbor.relationshipassistant.infrastructure.importer.ChatImporter;
import com.harbor.relationshipassistant.infrastructure.importer.ImportPreview;
import com.harbor.relationshipassistant.infrastructure.importer.ImportRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 微信 SQLite 备份导入器（技术设计 §6.1 的 WECHAT_SQLITE 实现）。
 *
 * <p><b>只读承诺</b>：所有 .db 一律以 {@code open_mode=1 (SQLITE_OPEN_READONLY)} 打开，
 * 连接后再执行 {@code PRAGMA query_only=ON}；message_content 的 zstd 解压只在内存进行，
 * 绝不回写、不覆盖、不修改任何原始文件。</p>
 *
 * <p>映射链路（全部经真实备份验证）：
 * chats.db.chats.display_name -> chat_id(wxid) -> md5(wxid)小写 -> message_0.db.Msg_&lt;hash&gt;
 * -> real_sender_id = Name2Id.rowid -> wxid。</p>
 *
 * <p>第一阶段只做纯文本；媒体经 WechatMediaHandler 扩展点预留，本阶段不实现。</p>
 */
public class WechatSqliteImporter implements ChatImporter {

    private static final Logger log = LoggerFactory.getLogger(WechatSqliteImporter.class);

    private final MessageMapper mapper = new MessageMapper();

    /** 一次 parse 的统计结果，供预览/审计。 */
    public static class Stats {
        public long meCount;
        public long otherCount;
        public long plaintextCount;
        public long zstdCount;
        public long decompressFailCount;
        public LocalDateTime earliest;
        public LocalDateTime latest;
        public String targetWxid;
        public String tableName;
    }

    private Stats lastStats;

    public Stats getLastStats() { return lastStats; }

    @Override
    public String type() { return "WECHAT_SQLITE"; }

    @Override
    public ImportPreview parse(ImportRequest request) {
        Path root = Path.of(request.getSourceLocation());
        Path chatsDb = root.resolve("data").resolve("chats.db");
        Path messageDb = root.resolve("message").resolve("message_0.db");

        ImportPreview preview = new ImportPreview();
        preview.setImporterType(type());
        preview.setSourceLocation(root.toString());

        String selfWxid = request.getSelfWxid();
        String targetName = request.getTargetChatName();
        if (selfWxid == null || selfWxid.isBlank()) {
            throw new ImportException("微信导入必须提供 selfWxid", "WechatSqliteImporter.parse");
        }
        if (targetName == null || targetName.isBlank()) {
            throw new ImportException("微信导入必须提供 targetChatName（chats.display_name）",
                    "WechatSqliteImporter.parse");
        }

        Stats stats = new Stats();
        stats.targetWxid = findTargetWxid(chatsDb, targetName);
        String table = "Msg_" + md5Hex(stats.targetWxid);
        stats.tableName = table;
        log.info("[WECHAT] target={} wxid={} table={}", targetName, stats.targetWxid, table);

        // Name2Id: rowid -> wxid
        Map<Long, String> name2Id = loadName2Id(messageDb);

        String sql = "SELECT local_id, server_id, local_type, sort_seq, real_sender_id, create_time, " +
                "origin_source, WCDB_CT_message_content, message_content FROM " + table +
                " ORDER BY sort_seq";
        try (Connection c = openReadOnly(messageDb);
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                WechatRawMessage raw = new WechatRawMessage();
                raw.setTableName(table);
                raw.setLocalId(rs.getLong("local_id"));
                raw.setServerId(rs.getLong("server_id"));
                raw.setLocalType(rs.getLong("local_type"));
                raw.setSortSeq(rs.getLong("sort_seq"));
                raw.setRealSenderId(rs.getLong("real_sender_id"));
                raw.setCreateTime(rs.getLong("create_time"));
                raw.setOriginSource(rs.getLong("origin_source"));
                raw.setContentCompressFlag(rs.getInt("WCDB_CT_message_content"));
                // 0=明文 TEXT，4=zstd BLOB（真实样本核验）
                if (raw.getContentCompressFlag() == 4) {
                    raw.setRawContent(rs.getBytes("message_content"));
                } else {
                    raw.setRawContent(rs.getString("message_content"));
                }
                raw.setSenderWxid(name2Id.getOrDefault(raw.getRealSenderId(), ""));

                MessageMapper.Mapped mapped = mapper.map(raw, selfWxid, stats.targetWxid);
                preview.getMessages().add(mapped.message);

                // stats
                if (mapped.message.getSenderType() == SenderType.ME) stats.meCount++;
                else stats.otherCount++;
                if ("zstd".equals(mapped.rawKind)) stats.zstdCount++;
                else if ("plaintext".equals(mapped.rawKind)) stats.plaintextCount++;
                if (mapped.decompressFailed) {
                    stats.decompressFailCount++;
                    preview.getParseErrors().add("local_id=" + raw.getLocalId() + " zstd解压失败");
                }
                LocalDateTime t = mapped.message.getMessageTime();
                if (stats.earliest == null || t.isBefore(stats.earliest)) stats.earliest = t;
                if (stats.latest == null || t.isAfter(stats.latest)) stats.latest = t;
            }
        } catch (Exception e) {
            throw new ImportException("读取微信消息库失败: " + e.getMessage(),
                    "WechatSqliteImporter.parse", e);
        }

        lastStats = stats;
        log.info("[WECHAT] parsed total={} me={} other={} zstd={} plaintext={} fail={}",
                preview.getTotalParsed(), stats.meCount, stats.otherCount,
                stats.zstdCount, stats.plaintextCount, stats.decompressFailCount);
        return preview;
    }

    private String findTargetWxid(Path chatsDb, String displayName) {
        String sql = "SELECT chat_id FROM chats WHERE display_name = ?";
        try (Connection c = openReadOnly(chatsDb);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, displayName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ImportException("chats.db 中找不到 display_name=" + displayName,
                            "WechatSqliteImporter.findTargetWxid");
                }
                return rs.getString(1);
            }
        } catch (ImportException e) {
            throw e;
        } catch (Exception e) {
            throw new ImportException("读取 chats.db 失败", "WechatSqliteImporter.findTargetWxid", e);
        }
    }

    private Map<Long, String> loadName2Id(Path messageDb) {
        Map<Long, String> map = new HashMap<>();
        try (Connection c = openReadOnly(messageDb);
             PreparedStatement ps = c.prepareStatement("SELECT rowid, user_name FROM Name2Id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getLong(1), rs.getString(2));
            }
        } catch (Exception e) {
            throw new ImportException("读取 Name2Id 失败", "WechatSqliteImporter.loadName2Id", e);
        }
        return map;
    }

    /** 只读打开 SQLite：open_mode=1 + query_only=ON。 */
    private Connection openReadOnly(Path dbFile) throws Exception {
        String url = "jdbc:sqlite:" + dbFile.toAbsolutePath().toString().replace('\\', '/')
                + "?open_mode=1";
        Connection c = DriverManager.getConnection(url);
        try (PreparedStatement ps = c.prepareStatement("PRAGMA query_only=ON")) {
            ps.execute();
        }
        return c;
    }

    private static String md5Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format(Locale.ROOT, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new ImportException("md5 计算失败", "WechatSqliteImporter.md5Hex", e);
        }
    }
}
