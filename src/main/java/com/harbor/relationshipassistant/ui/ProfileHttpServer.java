package com.harbor.relationshipassistant.ui;

import com.harbor.relationshipassistant.application.profile.ProfileService;
import com.harbor.relationshipassistant.application.ai.QuickReplyService;
import com.harbor.relationshipassistant.application.ai.dto.CandidateSet;
import com.harbor.relationshipassistant.domain.profile.OwnerType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 本地 HTTP 服务器：服务 AI 档案 HTML 页面 + JSON API。
 * 仅监听 127.0.0.1，不对外暴露。
 */
public class ProfileHttpServer {

    private static final Logger log = LoggerFactory.getLogger(ProfileHttpServer.class);
    private HttpServer server;
    private final ProfileService profileService;
    private volatile long relationshipId;
    private final int port;
    private final Path webDir;
    private volatile QuickReplyService quickReplyService;
    private volatile com.harbor.relationshipassistant.application.analysis.AnalysisService analysisService;
    private volatile com.harbor.relationshipassistant.application.analysis.report.DeepObservationReportService deepReportService;
    private volatile com.harbor.relationshipassistant.infrastructure.persistence.RelationshipRepository relationshipRepository;
    private volatile com.harbor.relationshipassistant.infrastructure.persistence.ChatMessageRepository chatMessageRepository;
    private final java.util.Map<String, String> reportCache = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile String contactName = "当前会话";

    public void setQuickReplyService(QuickReplyService s) { this.quickReplyService = s; }
    public void setAnalysisService(com.harbor.relationshipassistant.application.analysis.AnalysisService s) { this.analysisService = s; }
    public void setDeepReportService(com.harbor.relationshipassistant.application.analysis.report.DeepObservationReportService s) { this.deepReportService = s; }
    public void setRelationshipRepository(com.harbor.relationshipassistant.infrastructure.persistence.RelationshipRepository r) { this.relationshipRepository = r; }
    public void setChatMessageRepository(com.harbor.relationshipassistant.infrastructure.persistence.ChatMessageRepository r) { this.chatMessageRepository = r; }
    public void setContactName(String name) { if (name != null && !name.isBlank()) this.contactName = name; }

    public ProfileHttpServer(ProfileService profileService, long relationshipId, int port, Path webDir) {
        this.profileService = profileService;
        this.relationshipId = relationshipId;
        this.port = port;
        this.webDir = webDir;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/", this::handleApi);
        server.createContext("/", this::handleStatic);
        server.setExecutor(null);
        server.start();
        log.info("[PROFILE_HTTP] Started on http://127.0.0.1:{}/profile.html", port);
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    public String url() { return "http://127.0.0.1:" + port + "/profile.html"; }

    public void setRelationshipId(long relationshipId) {
        this.relationshipId = relationshipId;
        log.info("[PROFILE_HTTP] Switched relationshipId={}", relationshipId);
    }

    private void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/profile.html";
        Path file = webDir.resolve(path.substring(1)).normalize();
        if (!file.startsWith(webDir) || !Files.exists(file)) {
            sendJson(ex, 404, "{\"error\":\"not found\"}");
            return;
        }
        String mime = Files.probeContentType(file);
        if (mime == null) mime = "text/plain";
        if (file.toString().endsWith(".css")) mime = "text/css; charset=utf-8";
        if (file.toString().endsWith(".js")) mime = "application/javascript; charset=utf-8";
        if (file.toString().endsWith(".html")) mime = "text/html; charset=utf-8";
        byte[] data = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", mime);
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    private void handleApi(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        if ("OPTIONS".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }
        try {
            String path = ex.getRequestURI().getPath();
            Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
            if (path.equals("/api/profile") && "GET".equals(ex.getRequestMethod())) {
                OwnerType owner = OwnerType.valueOf(q.getOrDefault("owner", "ME"));
                Map<String, String> items = profileService.loadItems(relationshipId, owner);
                Map<String, Boolean> vis = profileService.loadAiVisibility(relationshipId, owner);
                StringBuilder sb = new StringBuilder("{\"items\":{");
                boolean first = true;
                for (var e : items.entrySet()) {
                    if (!first) sb.append(",");
                    sb.append("\"").append(esc(e.getKey())).append("\":\"").append(esc(e.getValue())).append("\"");
                    first = false;
                }
                sb.append("},\"permissions\":{");
                first = true;
                for (var e : vis.entrySet()) {
                    if (!first) sb.append(",");
                    sb.append("\"").append(esc(e.getKey())).append("\":").append(e.getValue());
                    first = false;
                }
                sb.append("}}");
                sendJson(ex, 200, sb.toString());
            } else if (path.equals("/api/profile/item") && "POST".equals(ex.getRequestMethod())) {
                String body = readBody(ex);
                Map<String, String> p = parseQuery(body);
                OwnerType owner = OwnerType.valueOf(p.getOrDefault("owner", "ME"));
                String key = p.get("key");
                String value = p.getOrDefault("value", "");
                String category = p.getOrDefault("category", "BASIC");
                profileService.saveItem(relationshipId, owner, category, key, value);
                sendJson(ex, 200, "{\"ok\":true}");
            } else if (path.equals("/api/profile/permission") && "POST".equals(ex.getRequestMethod())) {
                String body = readBody(ex);
                Map<String, String> p = parseQuery(body);
                OwnerType owner = OwnerType.valueOf(p.getOrDefault("owner", "ME"));
                String key = p.get("key");
                boolean visible = Boolean.parseBoolean(p.getOrDefault("visible", "true"));
                profileService.setAiVisible(relationshipId, owner, key, visible);
                sendJson(ex, 200, "{\"ok\":true}");
            } else if (path.equals("/api/stats") && "GET".equals(ex.getRequestMethod())) {
                // Placeholder - no real stats endpoint yet
                sendJson(ex, 200, "{\"total\":0,\"meCount\":0,\"otherCount\":0,\"note\":\"暂未接入\"}");
            } else if (path.equals("/api/session") && "GET".equals(ex.getRequestMethod())) {
                sendJson(ex, 200, "{\"relationshipId\":" + relationshipId
                        + ",\"contactName\":\"" + esc(contactName) + "\"}");
            } else if (path.equals("/api/chat-history") && "GET".equals(ex.getRequestMethod())) {
                if (chatMessageRepository == null) { sendJson(ex, 503, "{\"success\":false}"); return; }
                int size = 50;
                String chQuery = ex.getRequestURI().getQuery();
                String beforeTime = null; Long beforeId = null;
                if (chQuery != null) {
                    java.util.Map<String,String> qm = parseQuery(chQuery);
                    try { size = Integer.parseInt(qm.getOrDefault("size","50")); } catch (Exception ignored) {}
                    if (qm.get("beforeTime") != null) beforeTime = qm.get("beforeTime");
                    if (qm.get("beforeId") != null) beforeId = Long.parseLong(qm.get("beforeId"));
                }
                var list = (beforeTime == null)
                    ? chatMessageRepository.findLatest(relationshipId, size)
                    : chatMessageRepository.findOlder(relationshipId, java.time.LocalDateTime.parse(beforeTime), beforeId.longValue(), size);
                log.info("[JEVE][ChatHistory] {} relationshipId={} count={}", beforeTime==null?"latest":"older", relationshipId, list.size());
                StringBuilder sb = new StringBuilder("{\"success\":true,\"hasMore\":").append(list.size()==size).append(",\"messages\":[");
                for (int i = 0; i < list.size(); i++) {
                    var m = list.get(i);
                    if (i > 0) sb.append(',');
                    sb.append("{\"id\":").append(m.getId())
                      .append(",\"speaker\":\"").append(m.getSenderType() == null ? "OTHER" : m.getSenderType().name()).append('"')
                      .append(",\"time\":\"").append(m.getMessageTime() == null ? "" : m.getMessageTime().toString()).append('"')
                      .append(",\"content\":\"").append(esc(m.getContent() == null ? "" : m.getContent())).append("\"}");
                }
                sb.append("]}");
                sendJson(ex, 200, sb.toString());
            } else if (path.equals("/api/chat-history") && "POST".equals(ex.getRequestMethod())) {
                if (chatMessageRepository == null) { sendJson(ex, 503, "{\"success\":false}"); return; }
                Map<String,String> body = parseJsonBody(readBody(ex));
                try {
                    com.harbor.relationshipassistant.domain.chat.ChatMessage m = new com.harbor.relationshipassistant.domain.chat.ChatMessage();
                    m.setRelationshipId(relationshipId);
                    m.setSenderType(com.harbor.relationshipassistant.domain.chat.SenderType.valueOf(body.getOrDefault("speaker","OTHER")));
                    m.setMessageType(com.harbor.relationshipassistant.domain.chat.MessageType.TEXT);
                    m.setContent(body.get("content"));
                    String t = body.get("time");
                    if (t != null && !t.isBlank()) m.setMessageTime(java.time.LocalDateTime.parse(t.replace('T','T')));
                    m.setSourceType(com.harbor.relationshipassistant.domain.chat.MessageSourceType.MANUAL);
                    m.setSourceMessageId("");
                    m.setSourceHash("");
                    var saved = chatMessageRepository.insertAutoCommit(m);
                    log.info("[JEVE][ChatHistory] created id={}", saved.getId());
                    sendJson(ex, 200, "{\"success\":true,\"id\":" + saved.getId() + "}");
                } catch (Exception e) {
                    log.warn("[JEVE][ChatHistory] create failed: {}", e.getMessage());
                    sendJson(ex, 200, "{\"success\":false}");
                }
            } else if (path.matches("/api/chat-history/\\d+") && ("PUT".equals(ex.getRequestMethod()) || "DELETE".equals(ex.getRequestMethod()))) {
                if (chatMessageRepository == null) { sendJson(ex, 503, "{\"success\":false}"); return; }
                long id = Long.parseLong(path.substring("/api/chat-history/".length()));
                var existing = chatMessageRepository.findById(id);
                if (existing == null || existing.getRelationshipId() != relationshipId) { sendJson(ex, 404, "{\"success\":false}"); return; }
                try {
                    if ("PUT".equals(ex.getRequestMethod())) {
                        Map<String,String> body = parseJsonBody(readBody(ex));
                        com.harbor.relationshipassistant.domain.chat.SenderType st = com.harbor.relationshipassistant.domain.chat.SenderType.valueOf(body.getOrDefault("speaker", existing.getSenderType().name()));
                        java.time.LocalDateTime mt = body.get("time") != null && !body.get("time").isBlank() ? java.time.LocalDateTime.parse(body.get("time")) : existing.getMessageTime();
                        chatMessageRepository.editMessage(id, st, com.harbor.relationshipassistant.domain.chat.MessageType.TEXT, body.getOrDefault("content", existing.getContent()), mt);
                        log.info("[JEVE][ChatHistory] updated id={}", id);
                    } else {
                        chatMessageRepository.softDelete(id);
                        log.info("[JEVE][ChatHistory] deleted id={}", id);
                    }
                    sendJson(ex, 200, "{\"success\":true}");
                } catch (Exception e) {
                    log.warn("[JEVE][ChatHistory] update/delete failed: {}", e.getMessage());
                    sendJson(ex, 200, "{\"success\":false}");
                }
            } else if (path.equals("/api/quick-reply") && "POST".equals(ex.getRequestMethod())) {
                if (quickReplyService == null) {
                    sendJson(ex, 503, "{\"success\":false,\"error\":\"quick reply service unavailable\"}");
                    return;
                }
                log.info("[QuickReplyApi] generate relationshipId={}", relationshipId);
                if (chatMessageRepository != null && chatMessageRepository.countByRelationship(relationshipId) == 0) {
                    sendJson(ex, 200, "{\"success\":false,\"code\":\"NO_CHAT_DATA\"}");
                    return;
                }
                QuickReplyService.GenerationResult r = quickReplyService.generateCandidates(relationshipId, "");
                CandidateSet set = r.candidates();
                for (var s : set.strategies) {
                    log.info("[JEVE][QuickReply] candidate strategy={} replies={}", s.name, s.replies.size());
                }
                StringBuilder sb = new StringBuilder("{\"success\":true,\"candidates\":[");
                boolean first = true;
                for (var s : set.strategies) {
                    for (var rep : s.replies) {
                        if (!first) sb.append(",");
                        sb.append("{\"strategy\":\"").append(esc(s.name))
                          .append("\",\"replyText\":\"").append(esc(rep.text)).append("\"}");
                        first = false;
                    }
                }
                sb.append("]}");
                sendJson(ex, 200, sb.toString());
            } else if (path.equals("/api/quick-reply/refresh") && "POST".equals(ex.getRequestMethod())) {
                if (quickReplyService == null) { sendJson(ex, 503, "{\"success\":false}"); return; }
                Map<String,String> body = parseJsonBody(readBody(ex));
                String want = body.getOrDefault("strategy","NATURAL");
                com.harbor.relationshipassistant.domain.ai.ReplyStrategy strat;
                try { strat = com.harbor.relationshipassistant.domain.ai.ReplyStrategy.valueOf(want); }
                catch (IllegalArgumentException bad) { sendJson(ex, 400, "{\"success\":false,\"error\":\"bad strategy\"}"); return; }
                if (chatMessageRepository != null && chatMessageRepository.countByRelationship(relationshipId) == 0) {
                    sendJson(ex, 200, "{\"success\":false,\"code\":\"NO_CHAT_DATA\"}"); return;
                }
                log.info("[JEVE][QuickReply] refresh strategy={}", want);
                try {
                    var r = quickReplyService.generateSingleCandidate(relationshipId, strat);
                    sendJson(ex, 200, "{\"success\":true,\"candidate\":{\"strategy\":\"" + esc(want) + "\",\"replyText\":\"" + esc(r.text()) + "\"}}");
                } catch (Exception e) {
                    sendJson(ex, 200, "{\"success\":false}");
                }
            } else if (path.equals("/api/detail-analysis") && "POST".equals(ex.getRequestMethod())) {
                if (analysisService == null) {
                    sendJson(ex, 503, "{\"success\":false,\"error\":\"analysis service unavailable\"}");
                    return;
                }
                log.info("[DetailAnalysis] request received. relationshipId={}", relationshipId);
                try {
                    var meRaw = profileService.loadItems(relationshipId, OwnerType.ME);
                    var otherRaw = profileService.loadItems(relationshipId, OwnerType.OTHER);
                    var meVis = profileService.loadAiVisibility(relationshipId, OwnerType.ME);
                    var otherVis = profileService.loadAiVisibility(relationshipId, OwnerType.OTHER);
                    java.util.Map<String,String> meW = new java.util.HashMap<>(); meVis.forEach((k,v)->meW.put(k, Boolean.TRUE.equals(v)?"NORMAL":"NONE"));
                    java.util.Map<String,String> otW = new java.util.HashMap<>(); otherVis.forEach((k,v)->otW.put(k, Boolean.TRUE.equals(v)?"NORMAL":"NONE"));
                    com.harbor.relationshipassistant.domain.relationship.Relationship r = relationshipRepository == null ? null : relationshipRepository.findById(relationshipId);
                    var stage = r == null ? null : r.getCurrentStage();
                    var relStart = r == null ? null : r.getCreatedAt();
                    var res = analysisService.executeDetailAnalysis(relationshipId,
                            com.harbor.relationshipassistant.domain.analysis.Skill.NONE,
                            com.harbor.relationshipassistant.domain.analysis.OutputMode.NORMAL,
                            meRaw, meW, otherRaw, otW, stage, relStart);
                    if (!res.success() || !(res.output() instanceof com.harbor.relationshipassistant.domain.analysis.AnalysisResult ar)) {
                        sendJson(ex, 200, "{\"success\":true,\"status\":\"NO_DATA\",\"message\":\"最近 7 天暂无足够聊天记录，暂时无法进行详细分析。\"}");
                        return;
                    }
                    var om = new com.fasterxml.jackson.databind.ObjectMapper();
                    var root = om.createObjectNode();
                    root.put("success", true);
                    root.put("status", "SUCCESS");
                    root.put("taskType", "DETAIL_ANALYSIS");
                    root.put("range", "RECENT_7_DAYS");
                    var arr = root.putObject("result");
                    arr.put("observations", om.valueToTree(ar.getObservations()));
                    arr.put("possibilities", om.valueToTree(ar.getPossibilities()));
                    arr.put("emotions", om.valueToTree(ar.getEmotions()));
                    arr.put("userIssues", om.valueToTree(ar.getUserIssues()));
                    arr.put("recommendations", om.valueToTree(ar.getRecommendations()));
                    arr.put("facts", om.valueToTree(ar.getFacts()));
                    sendJson(ex, 200, root.toString());
                } catch (Exception ex2) {
                    log.error("[DetailAnalysis] failed: {}", ex2.getMessage(), ex2);
                    sendJson(ex, 200, "{\"success\":false,\"error\":\"暂时无法完成分析\"}");
                }
            } else if (path.equals("/api/deep-observation/data-range") && "GET".equals(ex.getRequestMethod())) {
                if (chatMessageRepository == null) { sendJson(ex, 200, "{\"success\":true,\"hasData\":false}"); return; }
                var tr = chatMessageRepository.getMessageTimeRange(relationshipId);
                if (tr[0] == null) { sendJson(ex, 200, "{\"success\":true,\"hasData\":false}"); return; }
                sendJson(ex, 200, "{\"success\":true,\"hasData\":true,\"first\":\"" + esc(tr[0].toString()) + "\",\"last\":\"" + esc(tr[1].toString()) + "\"}");
            } else if (path.equals("/api/deep-observation") && "POST".equals(ex.getRequestMethod())) {
                if (analysisService == null || deepReportService == null) {
                    sendJson(ex, 503, "{\"success\":false,\"status\":\"UNAVAILABLE\"}"); return;
                }
                Map<String, String> p = parseJsonBody(readBody(ex));
                String range = p.getOrDefault("range", "ALL");
                com.harbor.relationshipassistant.domain.analysis.AnalysisRange ar;
                try {
                    if ("CUSTOM".equals(range)) {
                        String s = p.get("start"), e = p.get("end");
                        if (s == null || e == null) throw new IllegalArgumentException();
                        ar = com.harbor.relationshipassistant.domain.analysis.AnalysisRange.custom(
                                java.time.LocalDate.parse(s).atStartOfDay(), java.time.LocalDate.parse(e).atStartOfDay());
                    } else {
                        ar = com.harbor.relationshipassistant.domain.analysis.AnalysisRange.of(
                                com.harbor.relationshipassistant.domain.analysis.AnalysisRange.Kind.valueOf(range));
                    }
                } catch (Exception e1) {
                    sendJson(ex, 200, "{\"success\":false,\"status\":\"INVALID_RANGE\"}"); return;
                }
                log.info("[DeepObservationWeb] start relationshipId={} range={}", relationshipId, range);
                try {
                    var meRaw = profileService.loadItems(relationshipId, OwnerType.ME);
                    var otherRaw = profileService.loadItems(relationshipId, OwnerType.OTHER);
                    var meVis = profileService.loadAiVisibility(relationshipId, OwnerType.ME);
                    var otherVis = profileService.loadAiVisibility(relationshipId, OwnerType.OTHER);
                    java.util.Map<String,String> meW = new java.util.HashMap<>(); meVis.forEach((k,v)->meW.put(k, Boolean.TRUE.equals(v)?"NORMAL":"NONE"));
                    java.util.Map<String,String> otW = new java.util.HashMap<>(); otherVis.forEach((k,v)->otW.put(k, Boolean.TRUE.equals(v)?"NORMAL":"NONE"));
                    var r = relationshipRepository == null ? null : relationshipRepository.findById(relationshipId);
                    var stage = r == null ? null : r.getCurrentStage();
                    var relStart = r == null ? null : r.getCreatedAt();
                    var pipe = analysisService.executeDeepObservationPipeline(relationshipId,
                            com.harbor.relationshipassistant.domain.analysis.AnalysisTaskType.DEEP_OBSERVATION, ar,
                            com.harbor.relationshipassistant.domain.analysis.Skill.GOUTOUJUNSHI,
                            com.harbor.relationshipassistant.domain.analysis.OutputMode.NORMAL,
                            meRaw, meW, otherRaw, otW, stage, relStart, null, null);
                    var rep = deepReportService.generate(pipe);
                    if (!rep.success()) {
                        sendJson(ex, 200, "{\"success\":false,\"status\":\"" + esc(String.valueOf(rep.failureType())) + "\"}"); return;
                    }
                    String reportId = java.util.UUID.randomUUID().toString();
                    reportCache.put(reportId, rep.html());
                    sendJson(ex, 200, "{\"success\":true,\"status\":\"SUCCESS\",\"reportId\":\"" + reportId + "\"}");
                } catch (Exception e2) {
                    log.error("[DeepObservationWeb] failed: {}", e2.getMessage(), e2);
                    sendJson(ex, 200, "{\"success\":false,\"status\":\"ANALYSIS_FAILED\"}");
                }
            } else if (path.equals("/api/deep-observation/report") && "GET".equals(ex.getRequestMethod())) {
                String rid = ex.getRequestURI().getQuery() == null ? null : parseQuery(ex.getRequestURI().getQuery()).get("reportId");
                String html = rid == null ? null : reportCache.get(rid);
                if (html == null) { sendJson(ex, 404, "{\"error\":\"not found\"}"); return; }
                byte[] data = html.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                ex.sendResponseHeaders(200, data.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(data); }
            } else if (path.equals("/api/deep-observation/save") && "POST".equals(ex.getRequestMethod())) {
                Map<String, String> p = parseJsonBody(readBody(ex));
                String rid = p.get("reportId");
                String html = rid == null ? null : reportCache.get(rid);
                if (html == null) { sendJson(ex, 200, "{\"success\":false}"); return; }
                final String htmlF = html;
                try {
                    java.util.concurrent.CountDownLatch l = new java.util.concurrent.CountDownLatch(1);
                    final boolean[] ok = {false};
                    javafx.application.Platform.runLater(() -> {
                        try {
                            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
                            fc.setInitialFileName("JEVE-深度观察报告-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".html");
                            var stage = javafx.stage.Window.getWindows().stream().filter(w -> w instanceof javafx.stage.Stage).map(w -> (javafx.stage.Stage)w).findFirst().orElse(null);
                            var f = fc.showSaveDialog(stage);
                            if (f != null) { java.nio.file.Files.writeString(f.toPath(), htmlF, StandardCharsets.UTF_8); ok[0] = true; }
                        } catch (Exception ignored) {}
                        l.countDown();
                    });
                    l.await();
                    sendJson(ex, 200, "{\"success\":" + ok[0] + "}");
                } catch (Exception e2) {
                    sendJson(ex, 200, "{\"success\":false}");
                }
            } else if (path.equals("/api/clipboard") && "POST".equals(ex.getRequestMethod())) {                String body = readBody(ex);
                Map<String, String> p = parseJsonBody(body);
                String text = p.getOrDefault("text", "");
                String finalText = text;
                javafx.application.Platform.runLater(() -> {
                    javafx.scene.input.Clipboard cb = javafx.scene.input.Clipboard.getSystemClipboard();
                    javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
                    cc.putString(finalText);
                    cb.setContent(cc);
                });
                sendJson(ex, 200, "{\"success\":true}");
            } else {
                sendJson(ex, 404, "{\"error\":\"unknown endpoint\"}");
            }
        } catch (Exception e) {
            log.error("[PROFILE_HTTP] API error: {}", e.getMessage(), e);
            sendJson(ex, 500, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private Map<String, String> parseJsonBody(String body) {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        if (body == null) return m;
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            node.fields().forEachRemaining(e -> m.put(e.getKey(), e.getValue().asText("")));
        } catch (Exception ignore) {}
        return m;
    }

    private void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    private String readBody(HttpExchange ex) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private static String wantLabel(String enumKey) {
        return switch (enumKey) {
            case "NATURAL" -> "自然";
            case "PROACTIVE" -> "主动";
            case "LIGHT_FLIRT" -> "轻微暧昧";
            default -> enumKey;
        };
    }

    private Map<String, String> parseQuery(String q) {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        if (q == null || q.isEmpty()) return m;
        for (String pair : q.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0) m.put(urlDec(pair.substring(0, i)), urlDec(pair.substring(i + 1)));
        }
        return m;
    }

    private static String urlDec(String s) {
        return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
