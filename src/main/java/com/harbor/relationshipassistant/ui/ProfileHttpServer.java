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

    // Phase 12: new Analysis Runtime
    private volatile com.harbor.relationshipassistant.application.analysis.AnalysisApplicationService analysisAppService;
    private volatile com.harbor.relationshipassistant.application.analysis.AnalysisTaskFactory analysisTaskFactory;
    private volatile com.harbor.relationshipassistant.application.analysis.AnalysisContextAdapter contextAdapter;
    private volatile com.harbor.relationshipassistant.application.skill.SkillManager skillManager;
    private volatile com.harbor.relationshipassistant.application.skill.adapter.SkillAdapter skillAdapter;
    private volatile com.harbor.relationshipassistant.application.systemhost.SystemHostPlanner systemHostPlanner;
    private volatile com.harbor.relationshipassistant.application.wechat.WechatImportService wechatImportService;

    // Phase 1: DatabaseMessageSource config
    private volatile String dbUrl, dbUser, dbPass;
    private volatile String projectRoot; // demo02 root
    private volatile String dbsourceType = "database"; // default
    private volatile String dbsourceAccount = ""; // current self WeChat wxid; set at runtime via /api/dbsource/config
    private volatile int dbsourceBatchSize = 50;
    // Long-running realtime listener
    private volatile com.harbor.capturepoc.source.DatabaseMessageSource realtimeSource;
    private volatile String realtimeWxid;
    private volatile long realtimeRelId;

    private void ensureRealtimeListener() {
        if (!"database".equalsIgnoreCase(dbsourceType)) { stopRealtimeListener(); return; }
        if (relationshipRepository == null) return;
        com.harbor.relationshipassistant.domain.relationship.Relationship r;
        try { r = relationshipRepository.findById(relationshipId); }
        catch (Exception e) { log.warn("[DBSource] findById failed: {}", e.toString()); return; }
        if (r == null || r.getWechatWxid() == null || r.getWechatWxid().isBlank()) return;
        String wxid = r.getWechatWxid();
        long relId = r.getId();
        // Already running for same contact?
        if (realtimeSource != null && wxid.equals(realtimeWxid) && relId == realtimeRelId) return;
        stopRealtimeListener();
        try {
            String root = projectRoot != null ? projectRoot : System.getProperty("user.dir");
            String pythonExe = com.harbor.relationshipassistant.common.config.PythonExecutableResolver.resolve();
            String bridge = root + "\\capture-poc\\db_bridge.py";
            String wechatApiDir = root + "\\wechatapi-main";
            com.harbor.capturepoc.persist.ChatMessageWriter w =
                    new com.harbor.capturepoc.persist.ChatMessageWriter(dbUrl, dbUser, dbPass);
            com.harbor.capturepoc.source.DatabaseMessageSource src =
                    new com.harbor.capturepoc.source.DatabaseMessageSource(
                            pythonExe, bridge, wechatApiDir, w, wxid, relId, dbsourceAccount);
            src.startRealtime((mid, rid) -> log.info("[DBSource][REALTIME] inserted mid={} relId={}", mid, rid));
            realtimeSource = src; realtimeWxid = wxid; realtimeRelId = relId;
            log.info("[DBSource] realtime listener STARTED wxid={} relId={}", wxid, relId);
        } catch (Exception e) {
            log.error("[DBSource] realtime listener start failed: {}", e.toString(), e);
        }
    }

    private void stopRealtimeListener() {
        if (realtimeSource != null) {
            try { realtimeSource.stopRealtime(); } catch (Exception ignored) {}
            log.info("[DBSource] realtime listener STOPPED wxid={}", realtimeWxid);
            realtimeSource = null; realtimeWxid = null; realtimeRelId = 0;
        }
    }

    public void setDbConfig(String url, String user, String pass) {
        this.dbUrl = url; this.dbUser = user; this.dbPass = pass;
    }
    public void setProjectRoot(String root) { this.projectRoot = root; }

    public void setWechatImportService(com.harbor.relationshipassistant.application.wechat.WechatImportService s) {
        this.wechatImportService = s;
        log.info("[PROFILE_HTTP] WechatImportService wired");
    }

    public void setSkillManager(com.harbor.relationshipassistant.application.skill.SkillManager s) { this.skillManager = s; }
    public void setSkillAdapter(com.harbor.relationshipassistant.application.skill.adapter.SkillAdapter a) { this.skillAdapter = a; }
    public void setSystemHostPlanner(com.harbor.relationshipassistant.application.systemhost.SystemHostPlanner p) { this.systemHostPlanner = p; }

    public void setAnalysisApplicationService(
            com.harbor.relationshipassistant.application.analysis.AnalysisApplicationService app,
            com.harbor.relationshipassistant.application.analysis.AnalysisTaskFactory factory,
            com.harbor.relationshipassistant.application.analysis.AnalysisContextAdapter adapter) {
        this.analysisAppService = app;
        this.analysisTaskFactory = factory;
        this.contextAdapter = adapter;
        log.info("[PROFILE_HTTP] AnalysisApplicationService wired: true");
    }

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
        stopRealtimeListener();
        if (server != null) server.stop(0);
    }

    public String url() { return "http://127.0.0.1:" + port + "/profile.html"; }

    public void setRelationshipId(long relationshipId) {
        this.relationshipId = relationshipId;
        log.info("[PROFILE_HTTP] Switched relationshipId={}", relationshipId);
        stopRealtimeListener();
        if ("database".equalsIgnoreCase(dbsourceType)) ensureRealtimeListener();
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
        ex.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
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
                // DEPRECATED (Stage 1B): legacy 3-strategy endpoint, does NOT go through SystemHostPlanner.
                // WebView UI uses /api/quick-reply-v2. This endpoint remains only for the old
                // JavaFX QuickReplyPanel still referenced by ChatViewApplication. Migration/removal
                // candidate once the legacy JavaFX panel is retired.
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
            } else if (path.equals("/api/quick-reply-v2") && "POST".equals(ex.getRequestMethod())) {
                if (analysisAppService == null || analysisTaskFactory == null || contextAdapter == null) {
                    sendJson(ex, 503, "{\"success\":false,\"error\":\"new analysis runtime not wired\"}");
                    return;
                }
                if (chatMessageRepository != null && chatMessageRepository.countByRelationship(relationshipId) == 0) {
                    sendJson(ex, 200, "{\"success\":false,\"code\":\"NO_CHAT_DATA\"}");
                    return;
                }
                log.info("[QUICK-REPLY-V2] request relationshipId={}", relationshipId);
                try {
                    var ctx = contextAdapter.build(relationshipId);
                    String rawBody = readBody(ex);
                    Map<String,String> body = parseJsonBody(rawBody);
                    String overrideSkill = body.getOrDefault("skillName", "").trim();
                    com.harbor.relationshipassistant.application.skill.context.AnalysisTask task;

                    // System Host 规划：Function + SkillResolution → ActiveSkill + DeliveryMode
                    com.harbor.relationshipassistant.application.systemhost.PlannedExecution plan = null;
                    boolean singleMode;
                    if (skillAdapter != null) {
                        var resolution = skillAdapter.resolve(
                                com.harbor.relationshipassistant.application.skill.context.AnalysisFunction.QUICK_REPLY,
                                overrideSkill);
                        task = analysisTaskFactory.quickReply(ctx, resolution);
                        if (systemHostPlanner != null) {
                            plan = systemHostPlanner.plan(
                                    com.harbor.relationshipassistant.application.skill.context.AnalysisFunction.QUICK_REPLY,
                                    resolution);
                        }
                    } else {
                        if (!overrideSkill.isEmpty()) {
                            task = new com.harbor.relationshipassistant.application.skill.context.AnalysisTask(
                                    com.harbor.relationshipassistant.application.skill.context.AnalysisFunction.QUICK_REPLY,
                                    overrideSkill, ctx, false);
                            // skillAdapter 未装配时仍记录 System Host 规划（User Skill → SINGLE_RESULT）
                            if (systemHostPlanner != null) {
                                log.info("[SystemHost] function={} activeSkill={} skillType={} delivery={}",
                                        com.harbor.relationshipassistant.application.skill.context.AnalysisFunction.QUICK_REPLY,
                                        overrideSkill, com.harbor.relationshipassistant.application.systemhost.SkillKind.USER,
                                        com.harbor.relationshipassistant.application.systemhost.DeliveryMode.SINGLE_RESULT);
                                plan = new com.harbor.relationshipassistant.application.systemhost.PlannedExecution(
                                        com.harbor.relationshipassistant.application.skill.context.AnalysisFunction.QUICK_REPLY,
                                        com.harbor.relationshipassistant.application.systemhost.ActiveSkill.userSkill(overrideSkill),
                                        com.harbor.relationshipassistant.application.systemhost.DeliveryMode.SINGLE_RESULT);
                            }
                        } else {
                            task = analysisTaskFactory.quickReply(ctx);
                            if (systemHostPlanner != null) {
                                log.info("[SystemHost] function={} activeSkill={} skillType={} delivery={}",
                                        com.harbor.relationshipassistant.application.skill.context.AnalysisFunction.QUICK_REPLY,
                                        com.harbor.relationshipassistant.application.systemhost.ActiveSkill.DEFAULT_THREE_STRATEGY_NAME,
                                        com.harbor.relationshipassistant.application.systemhost.SkillKind.SYSTEM,
                                        com.harbor.relationshipassistant.application.systemhost.DeliveryMode.THREE_BY_THREE);
                                plan = new com.harbor.relationshipassistant.application.systemhost.PlannedExecution(
                                        com.harbor.relationshipassistant.application.skill.context.AnalysisFunction.QUICK_REPLY,
                                        com.harbor.relationshipassistant.application.systemhost.ActiveSkill.systemDefaultThreeStrategy(),
                                        com.harbor.relationshipassistant.application.systemhost.DeliveryMode.THREE_BY_THREE);
                            }
                        }
                    }
                    // 把 System Host 规划挂到 task 上，让 PromptAssembler / OutputRuntime 真正读到 ActiveSkill
                    if (plan != null) {
                        task = task.withPlannedExecution(plan);
                    }
                    // Stage 8-4B: knowledge scope from frontend selectedDocumentIds
                    task = task.withKnowledgeScope(parseSelectedDocIds(rawBody));
                    // Delivery 由 SystemHostPlanner 决定；User Skill → SINGLE_RESULT；默认三策略 → THREE_BY_THREE
                    singleMode = (plan == null) ? !overrideSkill.isEmpty()
                            : (plan.deliveryMode() == com.harbor.relationshipassistant.application.systemhost.DeliveryMode.SINGLE_RESULT);
                    log.info("[QUICK-REPLY-V2] task built function={} skill={} useKnowledge={} singleMode={}",
                            task.getFunction(), task.getRequestedSkillName(), task.isUseKnowledge(), singleMode);
                    // Stage 5-2：走权威执行路径，按 ResultSpec 解析成 ResultItem[]。
                    var planned = analysisAppService.executePlanned(task);
                    StringBuilder sb = new StringBuilder();
                    sb.append("{\"success\":true,\"v2\":true,\"single\":").append(singleMode)
                      .append(",\"model\":\"").append(esc(planned.model()));
                    sb.append("\",\"createdAt\":\"").append(esc(planned.createdAt() == null ? "" : planned.createdAt().toString()));
                    sb.append("\",\"parseDegraded\":").append(planned.parseDegraded());
                    // notice（默认三策略点详细分析时由 planner 产出，Quick Reply 路径一般为空）
                    if (plan != null && plan.hasNotice()) {
                        sb.append(",\"notice\":\"").append(esc(plan.notice())).append("\"");
                    }
                    // 权威 items[]
                    sb.append(",\"items\":[");
                    boolean firstItem = true;
                    for (var it : planned.items()) {
                        if (!firstItem) sb.append(',');
                        firstItem = false;
                        sb.append("{\"type\":\"").append(it.type())
                          .append("\",\"audience\":\"").append(it.audience())
                          .append("\",\"strategyKey\":").append(it.strategyKey() == null ? "null" : ("\"" + esc(it.strategyKey()) + "\""))
                          .append(",\"order\":").append(it.order())
                          .append(",\"text\":\"").append(esc(it.text())).append("\"}");
                    }
                    sb.append("]");
                    // 兼容旧 candidates[]：把 SENDABLE_REPLY 类 item 按 strategyKey 分组取首条，供老 WebView 路径渲染。
                    sb.append(",\"candidates\":[");
                    boolean first = true;
                    java.util.Map<String, String> byStrategy = new java.util.LinkedHashMap<>();
                    for (var it : planned.items()) {
                        if (it.type() != com.harbor.relationshipassistant.application.systemhost.ResultType.SENDABLE_REPLY) continue;
                        String key = it.strategyKey() == null || it.strategyKey().isBlank() ? "SINGLE" : it.strategyKey();
                        byStrategy.putIfAbsent(key, it.text());
                    }
                    if (byStrategy.isEmpty()) {
                        // 旧行为兜底：没有可发回复也不要让前端炸，给一个空 SINGLE 卡片。
                        byStrategy.put("SINGLE", "");
                    }
                    for (var e : byStrategy.entrySet()) {
                        String key = e.getKey();
                        String label = switch (key) {
                            case "NATURAL" -> "自然";
                            case "PROACTIVE" -> "主动";
                            case "LIGHT_FLIRT" -> "轻微暧昧";
                            case "SINGLE" -> overrideSkill.isBlank() ? "回复" : overrideSkill;
                            default -> key;
                        };
                        if (!first) sb.append(',');
                        first = false;
                        sb.append("{\"strategy\":\"").append(esc(key))
                          .append("\",\"strategyLabel\":\"").append(esc(label))
                          .append("\",\"replyText\":\"").append(esc(e.getValue())).append("\"}");
                    }
                    sb.append("]}");
                    sendJson(ex, 200, sb.toString());
                } catch (com.harbor.relationshipassistant.application.skill.adapter.SkillNotAvailableException sne) {
                    log.warn("[QUICK-REPLY-V2] skill not available: {}", sne.getSkillName());
                    sendJson(ex, 200, "{\"success\":false,\"code\":\"SKILL_NOT_AVAILABLE\",\"error\":\"" + esc(sne.getMessage()) + "\"}");
                } catch (Exception e) {
                    log.error("[QUICK-REPLY-V2] failed: {}", e.toString(), e);
                    sendJson(ex, 200, "{\"success\":false,\"error\":\"" + esc(e.getMessage()) + "\"}");
                }
            } else if (path.equals("/api/detail-analysis-v2") && "POST".equals(ex.getRequestMethod())) {
                if (analysisAppService == null || analysisTaskFactory == null || contextAdapter == null) {
                    sendJson(ex, 503, "{\"success\":false,\"error\":\"new analysis runtime not wired\"}");
                    return;
                }
                if (chatMessageRepository != null && chatMessageRepository.countByRelationship(relationshipId) == 0) {
                    sendJson(ex, 200, "{\"success\":true,\"status\":\"NO_DATA\",\"message\":\"暂无足够聊天记录。\"}");
                    return;
                }
                log.info("[DETAIL-V2] request relationshipId={}", relationshipId);
                try {
                    var ctx = contextAdapter.build(relationshipId);
                    String rawBody = readBody(ex);
                    Map<String,String> body = parseJsonBody(rawBody);
                    String skillName = body.getOrDefault("skillName", "").trim();
                    com.harbor.relationshipassistant.application.skill.context.AnalysisTask task;
                    com.harbor.relationshipassistant.application.systemhost.PlannedExecution detailPlan = null;
                    if (skillAdapter != null) {
                        var resolution = skillAdapter.resolve(
                                com.harbor.relationshipassistant.application.skill.context.AnalysisFunction.DETAILED_ANALYSIS,
                                skillName);
                        task = analysisTaskFactory.detailedAnalysis(ctx, resolution);
                        if (systemHostPlanner != null) {
                            detailPlan = systemHostPlanner.plan(
                                    com.harbor.relationshipassistant.application.skill.context.AnalysisFunction.DETAILED_ANALYSIS,
                                    resolution);
                        }
                    } else {
                        task = analysisTaskFactory.detailedAnalysis(ctx);
                        if (systemHostPlanner != null) {
                            detailPlan = systemHostPlanner.plan(
                                    com.harbor.relationshipassistant.application.skill.context.AnalysisFunction.DETAILED_ANALYSIS,
                                    com.harbor.relationshipassistant.application.skill.adapter.SkillResolution.none());
                        }
                    }
                    if (detailPlan != null) task = task.withPlannedExecution(detailPlan);
                    task = task.withKnowledgeScope(parseSelectedDocIds(rawBody));
                    log.info("[DETAIL-V2] task built function={} skill={} useKnowledge={} systemHostActive={}",
                            task.getFunction(), task.getRequestedSkillName(), task.isUseKnowledge(),
                            detailPlan == null ? "(none)" : detailPlan.activeSkill().name() + "/" + detailPlan.activeSkill().kind());
                    // Stage 5-2：权威执行路径。
                    var planned = analysisAppService.executePlanned(task);
                    // reportKind：取 items[0].type，或空时回退到 spec 第一个 slot。
                    String reportKind;
                    if (!planned.items().isEmpty()) {
                        reportKind = planned.items().get(0).type().name();
                    } else {
                        reportKind = planned.spec().slots().isEmpty()
                                ? "UNKNOWN" : planned.spec().slots().get(0).type().name();
                    }
                    // ANALYSIS_REPORT 路径：保留旧 DetailedAnalysisAdapter 的长 schema 字段，供老 UI 渲染。
                    // SHORT_ANALYSIS 路径：不填 emotions/facts 等长 schema，直接给 reportMarkdown。
                    com.harbor.relationshipassistant.application.analysis.DetailedAnalysisAdapter.Result adapted = null;
                    if ("ANALYSIS_REPORT".equals(reportKind)) {
                        // Stage 7-2：优先使用 item.payload（LLM 输出的结构化 JSON），
                        // 让 DetailedAnalysisAdapter 能提取 summary/emotions/facts 等字段。
                        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                        String payloadJson = null;
                        for (var it : planned.items()) {
                            if (it.payload() != null) {
                                payloadJson = it.payload().toString();
                                break;
                            }
                        }
                        if (payloadJson == null) {
                            // fallback：折叠 text
                            StringBuilder folded = new StringBuilder();
                            for (var it : planned.items()) {
                                if (!folded.isEmpty()) folded.append("\n\n");
                                folded.append(it.text());
                            }
                            if (folded.isEmpty()) folded.append(planned.rawContent());
                            payloadJson = folded.toString();
                        }
                        com.harbor.relationshipassistant.application.llm.AnalysisResult legacy = new com.harbor.relationshipassistant.application.llm.AnalysisResult(
                                planned.function(), planned.skillName(), payloadJson,
                                planned.model(), planned.createdAt());
                        adapted = new com.harbor.relationshipassistant.application.analysis.DetailedAnalysisAdapter().adapt(legacy);
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append("{\"success\":true,\"v2\":true,\"function\":\"DETAILED_ANALYSIS\"");
                    sb.append(",\"reportKind\":\"").append(reportKind).append("\"");
                    sb.append(",\"parseDegraded\":").append(planned.parseDegraded());
                    if (detailPlan != null && detailPlan.hasNotice()) {
                        sb.append(",\"notice\":\"").append(esc(detailPlan.notice())).append("\"");
                        sb.append(",\"skillRedirectedFrom\":\"default-three-strategy\"");
                    }
                    sb.append(",\"skillName\":\"").append(esc(planned.skillName() == null ? "" : planned.skillName()));
                    sb.append("\",\"model\":\"").append(esc(planned.model()));
                    sb.append("\",\"createdAt\":\"").append(esc(planned.createdAt() == null ? "" : planned.createdAt().toString())).append("\"");
                    // 权威 items[]
                    sb.append(",\"items\":[");
                    boolean firstItem = true;
                    for (var it : planned.items()) {
                        if (!firstItem) sb.append(',');
                        firstItem = false;
                        sb.append("{\"type\":\"").append(it.type())
                          .append("\",\"audience\":\"").append(it.audience())
                          .append("\",\"order\":").append(it.order())
                          .append(",\"text\":\"").append(esc(it.text())).append("\"}");
                    }
                    sb.append("]");
                    if (adapted != null) {
                        // ANALYSIS_REPORT：旧长 schema 字段。
                        sb.append(",\"summary\":\"").append(esc(adapted.summary())).append("\"");
                        sb.append(",\"emotions\":{\"me\":[");
                        boolean first = true;
                        for (var e : adapted.meEmotions()) {
                            if (!first) sb.append(',');
                            first = false;
                            sb.append("{\"label\":\"").append(esc(e.label()))
                              .append("\",\"hint\":\"").append(esc(e.hint())).append("\"}");
                        }
                        sb.append("],\"other\":[");
                        first = true;
                        for (var e : adapted.otherEmotions()) {
                            if (!first) sb.append(',');
                            first = false;
                            sb.append("{\"label\":\"").append(esc(e.label()))
                              .append("\",\"hint\":\"").append(esc(e.hint())).append("\"}");
                        }
                        sb.append("]}");
                        sb.append(",\"facts\":[");
                        first = true;
                        for (String f : adapted.facts()) {
                            if (!first) sb.append(',');
                            first = false;
                            sb.append("\"").append(esc(f)).append("\"");
                        }
                        sb.append("],\"inferences\":[");
                        first = true;
                        for (String s : adapted.inferences()) {
                            if (!first) sb.append(',');
                            first = false;
                            sb.append("\"").append(esc(s)).append("\"");
                        }
                        sb.append("],\"possibilities\":[");
                        first = true;
                        for (var p : adapted.possibilities()) {
                            if (!first) sb.append(',');
                            first = false;
                            sb.append("{\"content\":\"").append(esc(p.content()))
                              .append("\",\"confidence\":\"").append(esc(p.confidence())).append("\"}");
                        }
                        sb.append("]");
                        sb.append(",\"recommendation\":{\"action\":\"").append(esc(adapted.recommendation().action()))
                          .append("\",\"reason\":\"").append(esc(adapted.recommendation().reason())).append("\"}");
                        sb.append(",\"selfCare\":{\"show\":").append(adapted.selfCare().show())
                          .append(",\"reason\":\"").append(esc(adapted.selfCare().reason())).append("\"}");
                        sb.append(",\"reportMarkdown\":\"").append(esc(adapted.reportMarkdown())).append("\"");
                    } else {
                        // SHORT_ANALYSIS：直接给一段 markdown 正文，不填长 schema。
                        String shortText = planned.items().isEmpty() ? "" : planned.items().get(0).text();
                        sb.append(",\"reportMarkdown\":\"").append(esc(shortText)).append("\"");
                    }
                    sb.append("}");
                    sendJson(ex, 200, sb.toString());
                } catch (com.harbor.relationshipassistant.application.skill.adapter.SkillNotAvailableException sne) {
                    log.warn("[DETAIL-V2] skill not available: {}", sne.getSkillName());
                    sendJson(ex, 200, "{\"success\":false,\"code\":\"SKILL_NOT_AVAILABLE\",\"error\":\"" + esc(sne.getMessage()) + "\"}");
                } catch (Exception e) {
                    log.error("[DETAIL-V2] failed: {}", e.toString(), e);
                    sendJson(ex, 200, "{\"success\":false,\"error\":\"" + esc(e.getMessage()) + "\"}");
                }
            } else if (path.equals("/api/skills") && "GET".equals(ex.getRequestMethod())) {
                try {
                    StringBuilder sb = new StringBuilder();
                    sb.append("{\"success\":true,\"skills\":[");
                    boolean first = true;
                    // Built-in system skill
                    sb.append("{\"name\":\"").append(com.harbor.relationshipassistant.application.systemhost.BuiltInSkills.DEFAULT_THREE_STRATEGY_NAME)
                      .append("\",\"description\":\"RUI 内置三策略：自然 / 主动 / 轻微暧昧，各 3 条可直接发送的回复。\"")
                      .append(",\"kind\":\"SYSTEM\"}");
                    first = false;
                    if (skillManager != null) {
                        for (var s : skillManager.getReadySkills()) {
                            if (!first) sb.append(',');
                            first = false;
                            sb.append("{\"name\":\"").append(esc(s.getName()));
                            sb.append("\",\"description\":\"").append(esc(s.getDescription() == null ? "" : s.getDescription()));
                            sb.append("\",\"kind\":\"USER\"}");
                        }
                    }
                    sb.append("]}");
                    sendJson(ex, 200, sb.toString());
                } catch (Exception e) {
                    log.error("/api/skills failed: {}", e.toString());
                    sendJson(ex, 200, "{\"success\":false,\"error\":\"" + esc(e.getMessage()) + "\"}");
                }
            } else if (path.equals("/api/knowledge/collections") && "GET".equals(ex.getRequestMethod())) {
                try {
                    java.nio.file.Path kd = java.nio.file.Paths.get("knowledge").toAbsolutePath();
                    if (!java.nio.file.Files.exists(kd)) kd = java.nio.file.Paths.get("skills", "goutoujunshi", "knowledge").toAbsolutePath();
                    var discovered = com.harbor.relationshipassistant.application.knowledge.KnowledgeDiscovery
                            .discover("relationship-knowledge", "恋爱关系知识库", kd);
                    StringBuilder docJson = new StringBuilder("[");
                    for (int i = 0; i < discovered.size(); i++) {
                        if (i > 0) docJson.append(',');
                        var d = discovered.get(i);
                        docJson.append("{\"id\":\"").append(esc(d.id()))
                               .append("\",\"name\":\"").append(esc(d.title())).append("\"}");
                    }
                    docJson.append("]");
                    sendJson(ex, 200, "{\"success\":true,\"collections\":[{\"id\":\"relationship-knowledge\",\"name\":\"恋爱关系知识库\",\"docCount\":" + discovered.size() + ",\"documents\":" + docJson + "}]}");
                } catch (Exception e) {
                    sendJson(ex, 200, "{\"success\":false,\"error\":\"" + esc(e.getMessage()) + "\"}");
                }
            } else if (path.equals("/api/relationships") && "GET".equals(ex.getRequestMethod())) {
                // Phase 22: contact selector data source.
                try {
                    if (relationshipRepository == null) {
                        sendJson(ex, 200, "{\"success\":true,\"relationships\":[]}");
                        return;
                    }
                    var list = relationshipRepository.listActive();
                    StringBuilder sb = new StringBuilder();
                    sb.append("{\"success\":true,\"relationships\":[");
                    boolean first = true;
                    for (var r : list) {
                        if (!first) sb.append(',');
                        first = false;
                        sb.append("{\"id\":").append(r.getId());
                        sb.append(",\"name\":\"").append(esc(r.getName()));
                        sb.append("\",\"stage\":\"").append(r.getCurrentStage() == null ? "" : r.getCurrentStage().name());
                        sb.append("\",\"updatedAt\":\"").append(r.getUpdatedAt() == null ? "" : r.getUpdatedAt().toString());
                        sb.append("\"}");
                    }
                    sb.append("]}");
                    sendJson(ex, 200, sb.toString());
                } catch (Exception e) {
                    log.error("/api/relationships failed: {}", e.toString());
                    sendJson(ex, 200, "{\"success\":false,\"error\":\"" + esc(e.getMessage()) + "\"}");
                }
            } else if (path.equals("/api/use-relationship") && "POST".equals(ex.getRequestMethod())) {
                // Phase 22: contact selector switching current relationship.
                try {
                    String body = readBody(ex);
                    long rid = Long.parseLong(body.replaceAll("[^0-9-]", ""));
                    setRelationshipId(rid);
                    sendJson(ex, 200, "{\"success\":true,\"relationshipId\":" + rid + "}");
                } catch (Exception e) {
                    sendJson(ex, 200, "{\"success\":false,\"error\":\"" + esc(e.getMessage()) + "\"}");
                }
            } else if (path.equals("/api/dbsource/detect") && "GET".equals(ex.getRequestMethod())) {
                String root = projectRoot != null ? projectRoot : System.getProperty("user.dir");
                String bridge = root + "\\capture-poc\\db_bridge.py";
                String wechatApiDir = root + "\\wechatapi-main";
                String pythonExe;
                try {
                    pythonExe = com.harbor.relationshipassistant.common.config.PythonExecutableResolver.resolve();
                } catch (Exception resolveErr) {
                    log.error("[DbDetect] PYTHON_NOT_FOUND: {}", resolveErr.getMessage());
                    sendJson(ex, 200, "{\"ok\":false,\"errorCode\":\"PYTHON_NOT_FOUND\",\"error\":\"" + esc(resolveErr.getMessage()) + "\"}");
                    return;
                }
                log.info("[DbDetect] start python={} cwd={} script={}", pythonExe, wechatApiDir, bridge);
                try {
                    ProcessBuilder pb = new ProcessBuilder(pythonExe, bridge, "detect");
                    pb.directory(new java.io.File(wechatApiDir));
                    pb.environment().put("PYTHONPATH", wechatApiDir);
                    pb.environment().put("PYTHONIOENCODING", "utf-8");
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), "UTF-8"));
                    String line;
                    String jsonLine = "";
                    while ((line = br.readLine()) != null) {
                        String trimmed = line.trim();
                        log.info("[DbDetect] py> {}", trimmed);
                        if (trimmed.startsWith("{")) jsonLine = trimmed;
                    }
                    int exit = p.waitFor();
                    log.info("[DbDetect] exitCode={}", exit);
                    if (jsonLine.isEmpty()) jsonLine = "{\"ok\":false,\"errorCode\":\"NO_JSON_OUTPUT\",\"error\":\"no json output, exit=" + exit + "\"}";
                    log.info("[DbDetect] result={}", jsonLine);
                    sendJson(ex, 200, jsonLine);
                } catch (Exception e) {
                    log.error("[DbDetect] FAILED", e);
                    sendJson(ex, 200, "{\"ok\":false,\"errorCode\":\"PROCESS_FAILED\",\"error\":\"" + esc(e.getMessage()) + "\"}");
                }
            } else if (path.equals("/api/dbsource/selfinfo") && "GET".equals(ex.getRequestMethod())) {
                try {
                    String acct = ex.getRequestURI().getQuery() == null ? null : parseQuery(ex.getRequestURI().getQuery()).get("account");
                    String root = projectRoot != null ? projectRoot : System.getProperty("user.dir");
                    String pythonExe = com.harbor.relationshipassistant.common.config.PythonExecutableResolver.resolve();
                    String bridge = root + "\\capture-poc\\db_bridge.py";
                    String wechatApiDir = root + "\\wechatapi-main";
                    log.info("[DbSelfInfo] account={}", acct);
                    ProcessBuilder pb = new ProcessBuilder(pythonExe, bridge, "selfinfo", acct == null ? "" : acct);
                    pb.directory(new java.io.File(wechatApiDir));
                    pb.environment().put("PYTHONPATH", wechatApiDir);
                    pb.environment().put("PYTHONIOENCODING", "utf-8");
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), "UTF-8"));
                    String line, jsonLine = "";
                    while ((line = br.readLine()) != null) {
                        String t = line.trim();
                        log.info("[DbSelfInfo] py> {}", t);
                        if (t.startsWith("{")) jsonLine = t;
                    }
                    p.waitFor();
                    if (jsonLine.isEmpty()) jsonLine = "{\"ok\":false,\"error\":\"no json\"}";
                    sendJson(ex, 200, jsonLine);
                } catch (Exception e) {
                    log.error("[DbSelfInfo] FAILED", e);
                    sendJson(ex, 200, "{\"ok\":false,\"error\":\"" + esc(e.getMessage()) + "\"}");
                }
            } else if (path.equals("/api/dbsource/contacts") && "GET".equals(ex.getRequestMethod())) {
                try {
                    String acct = ex.getRequestURI().getQuery() == null ? null : parseQuery(ex.getRequestURI().getQuery()).get("account");
                    String root = projectRoot != null ? projectRoot : System.getProperty("user.dir");
                    String pythonExe = com.harbor.relationshipassistant.common.config.PythonExecutableResolver.resolve();
                    String bridge = root + "\\capture-poc\\db_bridge.py";
                    String wechatApiDir = root + "\\wechatapi-main";
                    log.info("[DbContacts] account={}", acct);
                    ProcessBuilder pb = new ProcessBuilder(pythonExe, bridge, "contacts", acct == null ? "" : acct);
                    pb.directory(new java.io.File(wechatApiDir));
                    pb.environment().put("PYTHONPATH", wechatApiDir);
                    pb.environment().put("PYTHONIOENCODING", "utf-8");
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), "UTF-8"));
                    String line, jsonLine = "";
                    while ((line = br.readLine()) != null) {
                        String t = line.trim();
                        log.info("[DbContacts] py> {}", t);
                        if (t.startsWith("{")) jsonLine = t;
                    }
                    p.waitFor();
                    if (jsonLine.isEmpty()) jsonLine = "{\"ok\":false,\"error\":\"no json\"}";
                    sendJson(ex, 200, jsonLine);
                } catch (Exception e) {
                    log.error("[DbContacts] FAILED", e);
                    sendJson(ex, 200, "{\"ok\":false,\"error\":\"" + esc(e.getMessage()) + "\"}");
                }
            } else if (path.equals("/api/dbsource/config") && "GET".equals(ex.getRequestMethod())) {
                sendJson(ex, 200, "{\"success\":true,\"sourceType\":\"" + esc(dbsourceType)
                        + "\",\"account\":\"" + esc(dbsourceAccount) + "\""
                        + ",\"batchSize\":" + dbsourceBatchSize + "}");
            } else if (path.equals("/api/dbsource/config") && "POST".equals(ex.getRequestMethod())) {
                try {
                    String body = readBody(ex);
                    String st = extractJsonString(body, "sourceType");
                    String acct = extractJsonString(body, "account");
                    String bsStr = extractJsonString(body, "batchSize");
                    if (st != null && !st.isBlank()) dbsourceType = st;
                    if (acct != null && !acct.isBlank()) dbsourceAccount = acct;
                    if (bsStr != null && !bsStr.isBlank()) {
                        try { dbsourceBatchSize = Math.max(1, Math.min(500, Integer.parseInt(bsStr.trim()))); }
                        catch (NumberFormatException ignored) {}
                    }
                    log.info("[DBSource] config saved sourceType={} account={} batchSize={}", dbsourceType, dbsourceAccount, dbsourceBatchSize);
                    if ("database".equalsIgnoreCase(dbsourceType)) ensureRealtimeListener();
                    else stopRealtimeListener();
                    sendJson(ex, 200, "{\"success\":true,\"sourceType\":\"" + esc(dbsourceType)
                            + "\",\"account\":\"" + esc(dbsourceAccount) + "\""
                            + ",\"batchSize\":" + dbsourceBatchSize + "}");
                } catch (Exception e) {
                    sendJson(ex, 200, "{\"success\":false,\"error\":\"" + esc(e.getMessage()) + "\"}");
                }
            } else if (path.equals("/api/dbsource/sync") && "POST".equals(ex.getRequestMethod())) {
                // Sync WeChat contact history. If targetWxid is provided (from "从微信读取" picker),
                // find or create the relationship for that wxid and switch current relationship to it.
                // Backward-compatible: empty body = sync current active relationship.
                try {
                    if (relationshipRepository == null) { sendJson(ex, 500, "{\"success\":false,\"error\":\"no relationship repo\"}"); return; }
                    Map<String,String> body = parseJsonBody(readBody(ex));
                    String targetWxid = body.getOrDefault("targetWxid","").trim();
                    String targetName = body.getOrDefault("name","").trim();

                    com.harbor.relationshipassistant.domain.relationship.Relationship r;
                    if (!targetWxid.isEmpty()) {
                        r = relationshipRepository.findByWechatWxid(targetWxid).orElse(null);
                        if (r == null) {
                            // Create new relationship for this contact
                            String displayName = targetName.isEmpty() ? targetWxid : targetName;
                            com.harbor.relationshipassistant.domain.relationship.Relationship created =
                                    com.harbor.relationshipassistant.domain.relationship.Relationship.createNew(
                                            displayName, null,
                                            com.harbor.relationshipassistant.domain.relationship.RelationshipStage.INITIAL_CONTACT);
                            created.setWechatWxid(targetWxid);
                            r = relationshipRepository.insert(created);
                            log.info("[DBSource] created new relationship id={} wxid={}", r.getId(), targetWxid);
                        }
                        // switch current relationship to this one
                        this.relationshipId = r.getId();
                    } else {
                        r = relationshipRepository.findById(relationshipId);
                    }

                    if (r == null || r.getWechatWxid() == null || r.getWechatWxid().isBlank()) {
                        sendJson(ex, 200, "{\"success\":false,\"error\":\"当前关系未绑定微信号\"}"); return;
                    }
                    String contactWxid = r.getWechatWxid();
                    long relId = r.getId();
                    ensureRealtimeListener();
                    log.info("[DBSource] sync relId={} contactWxid={} account={}", relId, contactWxid, dbsourceAccount);
                    new Thread(() -> {
                        try {
                            String root = projectRoot != null ? projectRoot : System.getProperty("user.dir");
                            String pythonExe = com.harbor.relationshipassistant.common.config.PythonExecutableResolver.resolve();
                            String bridge = root + "\\capture-poc\\db_bridge.py";
                            String wechatApiDir = root + "\\wechatapi-main";
                            com.harbor.capturepoc.persist.ChatMessageWriter w =
                                    new com.harbor.capturepoc.persist.ChatMessageWriter(dbUrl, dbUser, dbPass);
                            com.harbor.capturepoc.source.DatabaseMessageSource src =
                                    new com.harbor.capturepoc.source.DatabaseMessageSource(
                                            pythonExe, bridge, wechatApiDir, w, contactWxid, relId, dbsourceAccount);
                            int n = src.syncHistory(dbsourceBatchSize);
                            log.info("[DBSource] sync done relId={} inserted={}", relId, n);
                        } catch (Exception e) {
                            log.error("[DBSource][ERROR] sync failed: {}", e.toString(), e);
                        }
                    }, "dbsource-sync").start();
                    sendJson(ex, 200, "{\"success\":true,\"contactWxid\":\"" + esc(contactWxid) + "\",\"relationshipId\":" + relId + ",\"status\":\"started\"}");
                } catch (Exception e) {
                    log.error("[DBSource] sync error: {}", e.toString());
                    sendJson(ex, 200, "{\"success\":false,\"error\":\"" + esc(e.getMessage()) + "\"}");
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
            } else if (path.equals("/api/wechat/contacts") && "GET".equals(ex.getRequestMethod())) {
                if (wechatImportService == null) { sendJson(ex, 503, "{\"success\":false,\"error\":\"wechat service unavailable\"}"); return; }
                Map<String,String> cq = parseQuery(ex.getRequestURI().getQuery());
                String root = cq.get("root");
                if (root == null || root.isBlank()) { sendJson(ex, 400, "{\"success\":false,\"error\":\"root required\"}"); return; }
                try {
                    var list = wechatImportService.listContacts(java.nio.file.Path.of(root));
                    StringBuilder sb = new StringBuilder("{\"success\":true,\"contacts\":[");
                    boolean first = true;
                    for (var c : list) {
                        if (!first) sb.append(',');
                        first = false;
                        sb.append("{\"wxid\":\"").append(esc(c.getWxid()))
                          .append("\",\"displayName\":\"").append(esc(c.getDisplayName()))
                          .append("\",\"messageCount\":").append(c.getMessageCount())
                          .append(",\"firstMsgTime\":").append(c.getFirstMsgTime())
                          .append(",\"lastMsgTime\":").append(c.getLastMsgTime())
                          .append('}');
                    }
                    sb.append("]}");
                    sendJson(ex, 200, sb.toString());
                } catch (Exception e) {
                    log.error("/api/wechat/contacts failed: {}", e.toString());
                    sendJson(ex, 200, "{\"success\":false,\"error\":\"" + esc(e.getMessage()) + "\"}");
                }
            } else if (path.equals("/api/wechat/find") && "GET".equals(ex.getRequestMethod())) {
                if (wechatImportService == null) { sendJson(ex, 503, "{\"success\":false}"); return; }
                Map<String,String> fq = parseQuery(ex.getRequestURI().getQuery());
                String root = fq.get("root");
                String alias = fq.get("alias");
                if (root == null || root.isBlank() || alias == null || alias.isBlank()) {
                    sendJson(ex, 400, "{\"success\":false,\"error\":\"root and alias required\"}"); return;
                }
                try {
                    var c = wechatImportService.findContactByAlias(java.nio.file.Path.of(root), alias);
                    if (c.isEmpty()) {
                        sendJson(ex, 200, "{\"success\":false,\"error\":\"未找到该微信号对应的联系人\"}");
                    } else {
                        var v = c.get();
                        sendJson(ex, 200, "{\"success\":true,\"contact\":{\"wxid\":\"" + esc(v.getWxid())
                                + "\",\"displayName\":\"" + esc(v.getDisplayName())
                                + "\",\"messageCount\":" + v.getMessageCount()
                                + ",\"firstMsgTime\":" + v.getFirstMsgTime()
                                + ",\"lastMsgTime\":" + v.getLastMsgTime() + "}}");
                    }
                } catch (Exception e) {
                    log.error("/api/wechat/find failed: {}", e.toString());
                    sendJson(ex, 200, "{\"success\":false,\"error\":\"" + esc(e.getMessage()) + "\"}");
                }
            } else if (path.equals("/api/wechat/self-wxid") && "GET".equals(ex.getRequestMethod())) {
                if (wechatImportService == null) { sendJson(ex, 503, "{\"success\":false}"); return; }
                var v = wechatImportService.getSelfWxid();
                var root = wechatImportService.getLastRoot().orElse("");
                if (v.isEmpty()) {
                    sendJson(ex, 200, "{\"success\":true,\"selfWxid\":null,\"configured\":false,\"lastRoot\":\"" + esc(root) + "\"}");
                } else {
                    sendJson(ex, 200, "{\"success\":true,\"selfWxid\":\"" + esc(v.get()) + "\",\"configured\":true,\"lastRoot\":\"" + esc(root) + "\"}");
                }
            } else if (path.equals("/api/wechat/self-wxid") && "POST".equals(ex.getRequestMethod())) {
                if (wechatImportService == null) { sendJson(ex, 503, "{\"success\":false}"); return; }
                Map<String,String> body = parseJsonBody(readBody(ex));
                String wxid = body.getOrDefault("wxid","").trim();
                if (wxid.isEmpty()) { sendJson(ex, 400, "{\"success\":false,\"error\":\"wxid required\"}"); return; }
                try {
                    wechatImportService.setSelfWxid(wxid);
                    sendJson(ex, 200, "{\"success\":true,\"selfWxid\":\"" + esc(wxid) + "\"}");
                } catch (Exception e) {
                    sendJson(ex, 200, "{\"success\":false,\"error\":\"" + esc(e.getMessage()) + "\"}");
                }
            } else if (path.equals("/api/wechat/scan-self") && "POST".equals(ex.getRequestMethod())) {
                if (wechatImportService == null) { sendJson(ex, 503, "{\"success\":false}"); return; }
                Map<String,String> body = parseJsonBody(readBody(ex));
                String root = body.getOrDefault("root","").trim();
                if (root.isEmpty()) { sendJson(ex, 400, "{\"success\":false,\"error\":\"root required\"}"); return; }
                try {
                    var cands = wechatImportService.getSelfWxidCandidates(java.nio.file.Path.of(root));
                    StringBuilder sb = new StringBuilder("{\"success\":true,\"candidates\":[");
                    boolean first = true;
                    for (var c : cands) {
                        if (!first) sb.append(',');
                        first = false;
                        sb.append("{\"wxid\":\"").append(esc(c.wxid))
                          .append("\",\"displayName\":\"").append(esc(c.displayName == null ? "" : c.displayName))
                          .append("\",\"messageCount\":").append(c.messageCount).append('}');
                    }
                    sb.append("]}");
                    sendJson(ex, 200, sb.toString());
                } catch (Exception e) {
                    log.error("/api/wechat/scan-self failed: {}", e.toString());
                    sendJson(ex, 200, "{\"success\":false,\"error\":\"" + esc(e.getMessage()) + "\"}");
                }
            } else if (path.equals("/api/wechat/import") && "POST".equals(ex.getRequestMethod())) {
                if (wechatImportService == null) { sendJson(ex, 503, "{\"success\":false}"); return; }
                Map<String,String> body = parseJsonBody(readBody(ex));
                String root = body.getOrDefault("root","").trim();
                String targetWxid = body.getOrDefault("targetWxid","").trim();
                if (root.isEmpty() || targetWxid.isEmpty()) {
                    sendJson(ex, 400, "{\"success\":false,\"error\":\"root and targetWxid required\"}"); return;
                }
                try {
                    var r = wechatImportService.importContact(java.nio.file.Path.of(root), targetWxid);
                    sendJson(ex, 200, "{\"success\":true,\"relationshipId\":" + r.relationshipId
                            + ",\"parsed\":" + r.parsed
                            + ",\"inserted\":" + r.inserted
                            + ",\"duplicates\":" + r.duplicates
                            + ",\"targetWxid\":\"" + esc(r.targetWxid) + "\""
                            + ",\"displayName\":\"" + esc(r.displayName) + "\"}");
                } catch (com.harbor.relationshipassistant.application.wechat.WechatImportService.SelfWxidNotConfiguredException e) {
                    sendJson(ex, 200, "{\"success\":false,\"code\":\"SELF_WXID_NOT_CONFIGURED\",\"error\":\"" + esc(e.getMessage()) + "\"}");
                } catch (com.harbor.relationshipassistant.application.wechat.WechatImportService.AmbiguousRelationshipNameException e) {
                    sendJson(ex, 200, "{\"success\":false,\"code\":\"AMBIGUOUS_RELATIONSHIP_NAME\",\"error\":\"" + esc(e.getMessage()) + "\"}");
                } catch (Exception e) {
                    log.error("/api/wechat/import failed: {}", e.toString());
                    sendJson(ex, 200, "{\"success\":false,\"error\":\"" + esc(e.getMessage()) + "\"}");
                }
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

    private java.util.Set<String> parseSelectedDocIds(String body) {
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            var arr = node.get("selectedDocumentIds");
            if (arr == null || !arr.isArray()) return java.util.Set.of();
            var out = new java.util.HashSet<String>();
            arr.forEach(n -> out.add(n.asText("")));
            return out;
        } catch (Exception e) { return java.util.Set.of(); }
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

    private static String extractJsonString(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
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
