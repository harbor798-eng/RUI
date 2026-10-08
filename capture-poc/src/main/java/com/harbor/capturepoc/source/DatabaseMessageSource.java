package com.harbor.capturepoc.source;

import com.harbor.capturepoc.CaptureDiag;
import com.harbor.capturepoc.persist.ChatMessageWriter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Database MessageSource: reads WeChat local DB via Python wechatauto bridge.
 *
 * Bridge script: capture-poc/db_bridge.py
 *  - history mode: prints one JSON array line
 *  - listen mode: prints one JSON line per new message
 *
 * Writes to chat_message via ChatMessageWriter with source_type=DATABASE.
 * sourceMessageId = "db-<wxid>-<localId>" (stable, unique per WeChat message).
 */
public class DatabaseMessageSource {

    private final String pythonExe;
    private final String bridgeScript;
    private final String wechatApiDir;
    private final ChatMessageWriter writer;
    private final String wxid;
    private final String account;
    private final long relationshipId;
    private Process listenProcess;
    private Thread listenThread;

    public interface Listener {
        void onMessageInserted(long messageId, long relationshipId);
    }

    public DatabaseMessageSource(String pythonExe, String bridgeScript, String wechatApiDir,
                                 ChatMessageWriter writer, String wxid, long relationshipId) {
        this(pythonExe, bridgeScript, wechatApiDir, writer, wxid, relationshipId, null);
    }

    public DatabaseMessageSource(String pythonExe, String bridgeScript, String wechatApiDir,
                                 ChatMessageWriter writer, String wxid, long relationshipId,
                                 String account) {
        this.pythonExe = pythonExe;
        this.bridgeScript = bridgeScript;
        this.wechatApiDir = wechatApiDir;
        this.writer = writer;
        this.wxid = wxid;
        this.relationshipId = relationshipId;
        this.account = account;
    }

    /** Pull recent history from WeChat DB and insert new messages. Returns count inserted. */
    public int syncHistory(int limit) throws Exception {
        CaptureDiag.log("[DBSource] history start wxid=" + wxid + " limit=" + limit);
        ProcessBuilder pb = new ProcessBuilder(pythonExe, bridgeScript, "history", wxid, String.valueOf(limit));
        pb.directory(new java.io.File(wechatApiDir));
        pb.environment().put("PYTHONPATH", wechatApiDir);
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        if (account != null && !account.isBlank()) pb.environment().put("WECHAT_ACCOUNT", account);
        pb.redirectErrorStream(false);
        Process p = pb.start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) out.append(line);
        }
        // also drain stderr
        StringBuilder err = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) err.append(line).append('\n');
        }
        int rc = p.waitFor();
        if (rc != 0) {
            CaptureDiag.log("[DBSource][ERROR] history rc=" + rc + " stderr=" + err);
            throw new RuntimeException("db_bridge history failed rc=" + rc);
        }
        // Parse JSON array
        List<Msg> msgs = parseHistory(out.toString());
        int inserted = 0;
        for (Msg m : msgs) {
            String sender = resolveSender(m);
            String sourceMsgId = "db-" + wxid + "-" + m.localId;
            LocalDateTime msgTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(m.createTime), ZoneId.of("Asia/Shanghai"));
            long id = writer.insert(relationshipId, sender, m.content, sourceMsgId, msgTime, "DATABASE");
            if (id > 0) {
                inserted++;
                CaptureDiag.log("[DBSource] INSERT id=" + id + " localId=" + m.localId + " sender=" + sender + " senderUser=" + m.senderUsername + " text=" + trunc(m.content));
            }
        }
        CaptureDiag.log("[DBSource] history done, total=" + msgs.size() + " inserted=" + inserted);
        return inserted;
    }

    /** Start realtime listener. Blocking callback on each new message. */
    public void startRealtime(Listener listener) throws Exception {
        CaptureDiag.log("[DBSource] listen start wxid=" + wxid);
        ProcessBuilder pb = new ProcessBuilder(pythonExe, bridgeScript, "listen", wxid);
        pb.directory(new java.io.File(wechatApiDir));
        pb.environment().put("PYTHONPATH", wechatApiDir);
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        if (account != null && !account.isBlank()) pb.environment().put("WECHAT_ACCOUNT", account);
        pb.redirectErrorStream(true);
        listenProcess = pb.start();
        listenThread = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(listenProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isBlank()) continue;
                    CaptureDiag.log("[DBSource] bridge: " + line);
                    Msg m = parseMsg(line);
                    if (m == null || m.localId == null) continue;
                    String sender = resolveSender(m);
                    String sourceMsgId = "db-" + wxid + "-" + m.localId;
                    LocalDateTime msgTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(m.createTime), ZoneId.of("Asia/Shanghai"));
                    long id = writer.insert(relationshipId, sender, m.content, sourceMsgId, msgTime, "DATABASE");
                    if (id > 0) {
                        CaptureDiag.log("[DBSource] REALTIME INSERT id=" + id + " localId=" + m.localId + " sender=" + sender);
                        if (listener != null) listener.onMessageInserted(id, relationshipId);
                    }
                }
            } catch (Exception e) {
                CaptureDiag.log("[DBSource][ERROR] listen thread: " + e.getMessage());
            }
        }, "db-source-listen");
        listenThread.setDaemon(true);
        listenThread.start();
    }

    public void stopRealtime() {
        if (listenProcess != null) listenProcess.destroy();
    }

    // --- minimal JSON parsing (no external dep) ---
    private static List<Msg> parseHistory(String json) {
        List<Msg> out = new ArrayList<>();
        if (json == null || json.isBlank()) return out;
        // Use org.json? Not available. Use a tiny manual parse.
        // We'll use javax.script? No. Let's just use a simple regex-based extraction.
        // Actually, Java has no built-in JSON. Let's use a minimal approach: split objects.
        // For robustness, use a simple recursive parser via StringBuilder.
        // We'll extract fields per object using regex.
        String[] objs = json.split("\\},\\s*\\{");
        for (String obj : objs) {
            obj = obj.replaceAll("[\\[\\{\\}\\]]", "");
            Msg m = new Msg();
            m.localId = extractLong(obj, "localId");
            m.senderId = extractLong(obj, "senderId");
            m.senderUsername = extractString(obj, "senderUsername");
            m.isSelf = extractBool(obj, "isSelf");
            m.createTime = extractLong(obj, "createTime");
            m.type = extractString(obj, "type");
            m.content = extractString(obj, "content");
            if (m.localId != null) out.add(m);
        }
        return out;
    }

    private static Msg parseMsg(String line) {
        Msg m = new Msg();
        m.localId = extractLong(line, "localId");
        m.senderId = extractLong(line, "senderId");
        m.senderUsername = extractString(line, "senderUsername");
        m.isSelf = extractBool(line, "isSelf");
        m.createTime = extractLong(line, "createTime");
        m.type = extractString(line, "type");
        m.content = extractString(line, "content");
        if (m.localId == null) return null;
        return m;
    }

    private static Long extractLong(String s, String key) {
        java.util.regex.Matcher mt = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9]+)").matcher(s);
        return mt.find() ? Long.parseLong(mt.group(1)) : null;
    }

    private static String extractString(String s, String key) {
        java.util.regex.Matcher mt = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(s);
        return mt.find() ? mt.group(1).replace("\\n", "\n").replace("\\\"", "\"") : null;
    }

    private static Boolean extractBool(String s, String key) {
        java.util.regex.Matcher mt = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*(true|false)").matcher(s);
        return mt.find() ? Boolean.parseBoolean(mt.group(1)) : null;
    }

    private static String trunc(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\s+", " ");
        return s.length() > 60 ? s.substring(0, 60) + "..." : s;
    }

    private static class Msg {
        Long localId; Long senderId; Long createTime;
        String senderUsername; Boolean isSelf;
        String type; String content;
    }

    /**
     * Resolve ME/OTHER from bridge-provided isSelf flag.
     * Bridge (db_bridge.py) uses:
     *   - sender_index lookup against self wxid
     *   - sender_id == 1 (WeChat special self row)
     * Falls back to senderUsername comparison if isSelf is absent.
     */
    private String resolveSender(Msg m) {
        if (m.isSelf != null) {
            return m.isSelf ? "ME" : "OTHER";
        }
        // Fallback for older bridge output
        String su = m.senderUsername;
        if (su == null || su.isBlank() || "1".equals(su)) {
            return "ME";
        }
        if (su.equals(wxid)) {
            return "OTHER";
        }
        CaptureDiag.log("[DBSource][WARN] ambiguous senderUsername=" + su + " senderId=" + m.senderId
                + " contactWxid=" + wxid + " -> defaulting OTHER");
        return "OTHER";
    }
}
