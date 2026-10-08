package com.harbor.relationshipassistant.infrastructure.importer.wechat;

import com.harbor.relationshipassistant.common.exception.ImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 微信联系人发现：只读打开 WChatSJ/data/chats.db，列出个人聊天。
 * <p>只读承诺：open_mode=1 + PRAGMA query_only=ON。</p>
 */
public class WechatContactDiscovery {

    private static final Logger log = LoggerFactory.getLogger(WechatContactDiscovery.class);
    private static final int DEFAULT_LIMIT = 15;

    public List<WechatContact> listPersonal(Path root) {
        return listPersonal(root, DEFAULT_LIMIT);
    }

    public List<WechatContact> listPersonal(Path root, int limit) {
        Path chatsDb = root.resolve("data").resolve("chats.db");
        if (!Files.exists(chatsDb)) {
            throw new ImportException("chats.db 不存在: " + chatsDb, "WechatContactDiscovery.listPersonal");
        }
        String sql = "SELECT chat_id, display_name, message_count, first_msg_time, last_msg_time " +
                "FROM chats WHERE is_group=0 ORDER BY last_msg_time DESC LIMIT ?";
        List<WechatContact> out = new ArrayList<>();
        try (Connection c = openReadOnly(chatsDb);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new WechatContact(
                            rs.getString(1),
                            rs.getString(2),
                            rs.getLong(3),
                            rs.getLong(4),
                            rs.getLong(5)));
                }
            }
        } catch (Exception e) {
            throw new ImportException("读取个人联系人失败: " + e.getMessage(),
                    "WechatContactDiscovery.listPersonal", e);
        }
        log.info("[WECHAT-DISC] listPersonal root={} -> {} contacts", root, out.size());
        return out;
    }

    public Optional<WechatContact> findByWxid(Path root, String wxid) {
        if (wxid == null || wxid.isBlank()) return Optional.empty();
        Path chatsDb = root.resolve("data").resolve("chats.db");
        if (!Files.exists(chatsDb)) {
            throw new ImportException("chats.db 不存在: " + chatsDb, "WechatContactDiscovery.findByWxid");
        }
        String sql = "SELECT chat_id, display_name, message_count, first_msg_time, last_msg_time " +
                "FROM chats WHERE chat_id=? AND is_group=0";
        try (Connection c = openReadOnly(chatsDb);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, wxid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new WechatContact(
                        rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getLong(5)))
                        : Optional.empty();
            }
        } catch (Exception e) {
            throw new ImportException("按 wxid 查询联系人失败: " + e.getMessage(),
                    "WechatContactDiscovery.findByWxid", e);
        }
    }

    /** 按微信号 alias 查 contacts 表，再回 chats 取会话信息。alias 覆盖率低，查不到返回 empty。 */
    public Optional<WechatContact> findByAlias(Path root, String alias) {
        if (alias == null || alias.isBlank()) return Optional.empty();
        Path chatsDb = root.resolve("data").resolve("chats.db");
        if (!Files.exists(chatsDb)) {
            throw new ImportException("chats.db 不存在: " + chatsDb, "WechatContactDiscovery.findByAlias");
        }
        String wxid;
        try (Connection c = openReadOnly(chatsDb);
             PreparedStatement ps = c.prepareStatement("SELECT wxid FROM contacts WHERE alias=? COLLATE NOCASE LIMIT 1")) {
            ps.setString(1, alias.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                wxid = rs.getString(1);
            }
        } catch (Exception e) {
            throw new ImportException("按 alias 查询联系人失败: " + e.getMessage(),
                    "WechatContactDiscovery.findByAlias", e);
        }
        return findByWxid(root, wxid);
    }

    private static Connection openReadOnly(Path dbFile) throws Exception {
        String url = "jdbc:sqlite:" + dbFile.toAbsolutePath().toString().replace('\\', '/')
                + "?open_mode=1";
        Connection c = DriverManager.getConnection(url);
        try (PreparedStatement ps = c.prepareStatement("PRAGMA query_only=ON")) {
            ps.execute();
        }
        return c;
    }
}
