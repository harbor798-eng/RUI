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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * selfWxid 候选生成：统计 message_0.db 中各 wxid 在<b>个人聊天表</b>里的发消息总数，
 * 按降序返回 top N。
 *
 * <p>启发式：群聊表里大量参与者会淹没"自己发的消息"这一信号，因此只统计 chats.db 中
 * is_group=0 的个人聊天对应表（Msg_&lt;md5(wxid)&gt;）。</p>
 *
 * <p>注意：这是<b>候选</b>，不是自动识别。用户必须在 UI 中确认。</p>
 */
public class SelfWxidCandidates {

    private static final Logger log = LoggerFactory.getLogger(SelfWxidCandidates.class);

    public static class Candidate {
        public final String wxid;
        public final long messageCount;
        public final String displayName;
        public Candidate(String wxid, long messageCount, String displayName) {
            this.wxid = wxid;
            this.messageCount = messageCount;
            this.displayName = displayName;
        }
    }

    public List<Candidate> list(Path root) {
        return list(root, 3);
    }

    public List<Candidate> list(Path root, int topN) {
        Path chatsDb = root.resolve("data").resolve("chats.db");
        Path messageDb = root.resolve("message").resolve("message_0.db");
        if (!Files.exists(chatsDb)) {
            throw new ImportException("chats.db 不存在: " + chatsDb, "SelfWxidCandidates.list");
        }
        if (!Files.exists(messageDb)) {
            throw new ImportException("message_0.db 不存在: " + messageDb, "SelfWxidCandidates.list");
        }

        // 1) 个人聊天 wxid 列表 + contacts wxid→display_name 映射
        List<String> personalWxids = new ArrayList<>();
        Map<String,String> displayByWxid = new HashMap<>();
        try (Connection c = openReadOnly(chatsDb);
             PreparedStatement ps = c.prepareStatement("SELECT chat_id FROM chats WHERE is_group=0");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) personalWxids.add(rs.getString(1));
        } catch (Exception e) {
            throw new ImportException("读取个人聊天列表失败: " + e.getMessage(),
                    "SelfWxidCandidates.list", e);
        }
        try (Connection c = openReadOnly(chatsDb);
             PreparedStatement ps = c.prepareStatement("SELECT wxid, display_name FROM contacts");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String w = rs.getString(1);
                String n = rs.getString(2);
                if (w != null && !w.isBlank()) displayByWxid.put(w, n);
            }
        } catch (Exception e) {
            log.warn("[WECHAT-SELF] load contacts display_name failed: {}", e.toString());
        }

        // 2) 打开 message_0.db，加载 Name2Id
        Map<Long, String> name2Id = new HashMap<>();
        Map<String, Long> countByWxid = new TreeMap<>();
        try (Connection c = openReadOnly(messageDb)) {
            try (PreparedStatement ps = c.prepareStatement("SELECT rowid, user_name FROM Name2Id");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) name2Id.put(rs.getLong(1), rs.getString(2));
            }
            // 3) 仅在个人聊天对应表内统计
            for (String wx : personalWxids) {
                String table = "Msg_" + md5Hex(wx);
                // 表可能不存在（备份不完整），跳过
                if (!tableExists(c, table)) continue;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT real_sender_id, COUNT(*) FROM " + table + " GROUP BY real_sender_id");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long rid = rs.getLong(1);
                        long cnt = rs.getLong(2);
                        String w = name2Id.getOrDefault(rid, "rowid:" + rid);
                        countByWxid.merge(w, cnt, Long::sum);
                    }
                }
            }
        } catch (Exception e) {
            throw new ImportException("统计 selfWxid 候选失败: " + e.getMessage(),
                    "SelfWxidCandidates.list", e);
        }

        List<Candidate> out = new ArrayList<>();
        countByWxid.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .forEach(e -> out.add(new Candidate(e.getKey(), e.getValue(), displayByWxid.get(e.getKey()))));
        log.info("[WECHAT-SELF] candidates root={} personalChats={} -> {}", root, personalWxids.size(), out.size());
        return out;
    }

    private static boolean tableExists(Connection c, String table) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String md5Hex(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format(java.util.Locale.ROOT, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new ImportException("md5 失败", "SelfWxidCandidates.md5Hex", e);
        }
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
