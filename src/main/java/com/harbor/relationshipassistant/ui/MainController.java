package com.harbor.relationshipassistant.ui;

import com.harbor.relationshipassistant.application.ai.AiConfigService;
import com.harbor.relationshipassistant.application.ai.ContextBuilder;
import com.harbor.relationshipassistant.application.ai.QuickReplyService;
import com.harbor.relationshipassistant.application.analysis.AnalysisApplicationService;
import com.harbor.relationshipassistant.application.analysis.AnalysisContextAdapter;
import com.harbor.relationshipassistant.application.analysis.AnalysisService;
import com.harbor.relationshipassistant.application.analysis.AnalysisServiceFactory;
import com.harbor.relationshipassistant.application.analysis.AnalysisTaskFactory;
import com.harbor.relationshipassistant.application.analysis.report.DeepObservationReportService;
import com.harbor.relationshipassistant.application.knowledge.InMemoryKnowledgeRetriever;
import com.harbor.relationshipassistant.application.knowledge.KnowledgeRouter;
import com.harbor.relationshipassistant.application.llm.AiProviderLLMClient;
import com.harbor.relationshipassistant.application.llm.LLMService;
import com.harbor.relationshipassistant.application.prompt.PromptAssembler;
import com.harbor.relationshipassistant.application.profile.ProfileService;
import com.harbor.relationshipassistant.application.relationship.RelationshipService;
import com.harbor.relationshipassistant.application.importjob.ImportService;
import com.harbor.relationshipassistant.application.config.AppConfigService;
import com.harbor.relationshipassistant.application.wechat.WechatImportService;
import com.harbor.relationshipassistant.application.skill.SkillManager;
import com.harbor.relationshipassistant.application.skill.SkillRouter;
import com.harbor.relationshipassistant.infrastructure.ai.AIProvider;
import com.harbor.relationshipassistant.infrastructure.importer.wechat.SelfWxidCandidates;
import com.harbor.relationshipassistant.infrastructure.importer.wechat.WechatContactDiscovery;
import com.harbor.relationshipassistant.infrastructure.importer.wechat.WechatSqliteImporter;
import com.harbor.relationshipassistant.infrastructure.persistence.*;
import com.harbor.relationshipassistant.infrastructure.security.AesCryptoService;
import javafx.fxml.FXML;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    @FXML private AnchorPane contentArea;

    private DataSourceFactory ds;
    private String aesKey;
    private long relationshipId;
    private Stage stage;

    private QuickReplyService quickReplyService;
    private AnalysisService analysisService;
    private DeepObservationReportService reportService;
    private RelationshipRepository relRepo;
    private ChatMessageRepository chatRepo;
    private ProfileService profileService;
    private ProfileHttpServer profileHttpServer;

    // ---- Phase 12: new Analysis Runtime wiring ----
    private SkillManager skillManager;
    private AnalysisApplicationService analysisApplicationService;
    private AnalysisTaskFactory analysisTaskFactory;
    private AnalysisContextAdapter contextAdapter;

    private AIProvider lastProvider;
    public void setSkillManager(SkillManager skillManager) {
        this.skillManager = skillManager;
        diag("setSkillManager: ready=" + (skillManager == null ? -1 : skillManager.getReadySkills().size())
                + " lastProvider=" + (lastProvider != null));
        if (skillManager != null && lastProvider != null) {
            try { wireNewAnalysisRuntime(lastProvider); } catch (Exception e) {
                diag("setSkillManager rewire failed: " + e);
            }
        }
    }

    /** Full shutdown: stop HTTP server, release resources. Called by ChatViewApplication.stop(). */
    public void shutdown() {
        try { if (profileHttpServer != null) profileHttpServer.stop(); } catch (Exception ignored) {}
    }

    public void inject(DataSourceFactory ds, String aesKey, long relationshipId, Stage stage) {
        this.ds = ds;
        this.aesKey = aesKey;
        this.relationshipId = relationshipId;
        this.stage = stage;
        initialize();
    }

    private void initialize() {
        // Ensure WebView localStorage (theme, aiContextSize, etc.) persists across launches.
        // Without a writable userDataDirectory, WebEngine localStorage may be ephemeral,
        // causing the theme to reset on every restart.
        try {
            java.nio.file.Path webDataDir = java.nio.file.Paths.get(
                    System.getProperty("user.home"), ".rui", "webview-data").toAbsolutePath();
            java.nio.file.Files.createDirectories(webDataDir);
            System.setProperty("javafx.web.userDataDirectory", webDataDir.toString());
            log.info("[JEVE][WebView] userDataDirectory = {}", webDataDir);
        } catch (Exception e) {
            log.warn("[JEVE][WebView] failed to set userDataDirectory: {}", e.toString());
        }
        AesCryptoService crypto = new AesCryptoService(aesKey);
        AiConfigService aiConfig = new AiConfigService(ds, crypto);
        ProfileService profiles = new ProfileService(ds, new AuditLogRepository(ds), crypto);
        this.relRepo = new RelationshipRepository(ds);
        this.chatRepo = new ChatMessageRepository(ds);
        ContextBuilder builder = new ContextBuilder(relRepo, chatRepo, profiles, new ObservationRepository(ds));
        AIProvider provider = null;
        try {
            provider = aiConfig.buildProvider();
        } catch (Exception ex) {
            log.warn("[AI] Provider not configured; main UI will load without AI: {}", ex.toString());
        }
        this.profileService = profiles;
        this.quickReplyService = new QuickReplyService(builder, provider);
        this.analysisService = AnalysisServiceFactory.build(ds, provider);
        this.reportService = new DeepObservationReportService();

        try {
            // Resolve web resources: dev (src/main/resources/web) or packaged (app/web relative to working dir).
            java.nio.file.Path webDir = java.nio.file.Paths.get("src/main/resources/web").toAbsolutePath();
            if (!java.nio.file.Files.exists(webDir)) webDir = java.nio.file.Paths.get("web").toAbsolutePath();
            if (!java.nio.file.Files.exists(webDir)) webDir = java.nio.file.Paths.get("app/web").toAbsolutePath();
            if (!java.nio.file.Files.exists(webDir)) webDir = java.nio.file.Paths.get(System.getProperty("user.home"), ".rui", "web");
            log.info("[JEVE][Bootstrap] webDir={} exists={}", webDir, java.nio.file.Files.exists(webDir));
            profileHttpServer = new ProfileHttpServer(profiles, relationshipId, 18080, webDir);
            profileHttpServer.setQuickReplyService(quickReplyService);
            profileHttpServer.setAnalysisService(analysisService);
            profileHttpServer.setRelationshipRepository(relRepo);
            profileHttpServer.setDeepReportService(reportService);
            profileHttpServer.setChatMessageRepository(chatRepo);
            profileHttpServer.setDataSourceFactory(ds);
            profileHttpServer.setAiConfigService(aiConfig);
            profileHttpServer.setOnAiConfigChanged(() -> {
                try {
                    AIProvider p = aiConfig.buildProvider();
                    wireNewAnalysisRuntime(p);
                    log.info("[JEVE][Bootstrap] Analysis runtime rewired after AI config save");
                } catch (Exception e) {
                    log.warn("[JEVE][Bootstrap] rewire failed: {}", e.toString());
                }
            });
            // Always wire SkillManager to HTTP layer so /api/skills works regardless of AI provider
            if (skillManager != null) {
                profileHttpServer.setSkillManager(skillManager);
                log.info("[JEVE][Bootstrap] SkillManager wired to HTTP layer: ready={}", skillManager.getReadySkills().size());
            }
            // Wire DistillationService
            try {
                var distillSkillsRoot = java.nio.file.Paths.get("skills").toAbsolutePath().normalize();
                var distillService = new com.harbor.relationshipassistant.application.distill.DistillationService(
                        chatRepo, relRepo, provider, distillSkillsRoot);
                profileHttpServer.setDistillationService(distillService);
                log.info("[JEVE][Bootstrap] DistillationService injected: true");
                // Wire ExDistillService (前任 Skill，基于 ex-skill-main)
                try {
                    var exMaterialSource = new com.harbor.relationshipassistant.application.distill.ExMaterialSource(chatRepo);
                    var exPublisher = new com.harbor.relationshipassistant.application.distill.ExSkillPublisher(distillSkillsRoot);
                    var exDistillService = new com.harbor.relationshipassistant.application.distill.ExDistillService(
                            provider, exMaterialSource, exPublisher);
                    profileHttpServer.setExDistillService(exDistillService);
                    log.info("[JEVE][Bootstrap] ExDistillService injected: true");
                } catch (Exception exDistillEx) {
                    log.warn("[JEVE][Bootstrap] ExDistillService wiring failed: {}", exDistillEx.toString());
                }
            } catch (Exception distillEx) {
                log.warn("[JEVE][Bootstrap] DistillationService wiring failed: {}", distillEx.toString());
            }
            // Phase 1: DatabaseMessageSource wiring
            try {
                com.harbor.relationshipassistant.common.config.AppConfig cfg0 =
                        new com.harbor.relationshipassistant.common.config.AppConfig();
                profileHttpServer.setDbConfig(cfg0.dbUrl(), cfg0.dbUsername(), cfg0.dbPassword());
                profileHttpServer.setProjectRoot(System.getProperty("user.dir"));
                log.info("[JEVE][Bootstrap] DbConfig + projectRoot injected into ProfileHttpServer");
            } catch (Exception ex) {
                log.warn("[JEVE][Bootstrap] DbConfig injection failed: {}", ex.toString());
            }

            // Phase 7: wire WechatImportService into ProfileHttpServer
            try {
                AuditLogRepository auditRepo = new AuditLogRepository(ds);
                AppConfigService appConfigService = new AppConfigService(new AppConfigRepository(ds));
                RelationshipService relService = new RelationshipService(ds, relRepo, auditRepo);
                ImportService importService = new ImportService(ds, chatRepo, auditRepo,
                        java.util.List.of(new WechatSqliteImporter()));
                WechatContactDiscovery discovery = new WechatContactDiscovery();
                SelfWxidCandidates selfCandidates = new SelfWxidCandidates();
                WechatImportService wechatImportService = new WechatImportService(
                        discovery, selfCandidates, appConfigService, relService, importService);
                profileHttpServer.setWechatImportService(wechatImportService);
                log.info("[JEVE][Bootstrap] WechatImportService injected: true");
            } catch (Exception bootEx) {
                log.warn("[JEVE][Bootstrap] WechatImportService wiring failed: {}", bootEx.toString());
            }

            wireNewAnalysisRuntime(provider);
            log.info("[JEVE][Bootstrap] QuickReplyService injected: true");
            log.info("[JEVE][Bootstrap] AnalysisService injected: true");
            log.info("[JEVE][Bootstrap] RelationshipRepository injected: true");
            log.info("[JEVE][Bootstrap] DeepObservationReportService injected: true");
            log.info("[JEVE][Bootstrap] ChatMessageRepository injected: true");
            log.info("[JEVE][Bootstrap] RelationshipId synchronized: {}", relationshipId);
            profileHttpServer.start();
            log.info("[PROFILE_HTTP] Profile page available at: http://127.0.0.1:18080/profile.html");

            log.info("[JEVE][WebView] Initializing WebView...");
            javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
            webView.setStyle("-fx-background-color: transparent;");
            // Read persisted theme BEFORE page load to avoid white flash on glass startup.
            String persistedTheme = null;
            try {
                java.nio.file.Path tp = java.nio.file.Paths.get(System.getProperty("user.home"), ".rui", "theme.properties");
                if (java.nio.file.Files.exists(tp)) {
                    java.util.Properties p = new java.util.Properties();
                    try (java.io.InputStream in = java.nio.file.Files.newInputStream(tp)) { p.load(in); }
                    persistedTheme = p.getProperty("theme");
                    log.info("[JEVE][WebView] persisted theme at startup = {}", persistedTheme);
                }
            } catch (Exception e) {
                log.warn("[JEVE][WebView] cannot read persisted theme: {}", e.toString());
            }
            boolean startGlass = "desktop-glass".equals(persistedTheme);
            webView.setPageFill(startGlass ? javafx.scene.paint.Color.TRANSPARENT : javafx.scene.paint.Color.WHITE);
            // Explicitly set all containers transparent (FXML inline styles may not apply reliably)
            contentArea.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
            if (contentArea.getParent() instanceof javafx.scene.layout.Region parentRegion) {
                parentRegion.setStyle("-fx-background-color: transparent;");
                log.info("[JEVE][WebView] parent region set transparent: {}", parentRegion.getClass().getSimpleName());
            }
            javafx.scene.web.WebEngine engine = webView.getEngine();
            engine.setOnAlert(e -> log.info("[WebView][JS][alert] {}", e.getData()));
            engine.setOnError(e -> log.warn("[WebView][ERROR] {}", e.getMessage()));
            engine.setOnStatusChanged(e -> {
                if (e.getData() != null && !e.getData().isEmpty()) {
                    log.info("[WebView][status] {}", e.getData());
                }
            });
            log.info("[JEVE][WebView] userDataDirectory effective = {}",
                    System.getProperty("javafx.web.userDataDirectory"));
            DesktopBridge bridge = new DesktopBridge(stage, webView);
            // Wire WebView 采集开关 to the real RealtimeCaptureService (PrintWindow + OCR + SQLite).
            try {
                final com.harbor.relationshipassistant.common.config.AppConfig cfg =
                        new com.harbor.relationshipassistant.common.config.AppConfig();
                final com.harbor.capturepoc.RealtimeCaptureService[] svc = new com.harbor.capturepoc.RealtimeCaptureService[1];
                bridge.setCollectorCallbacks(
                        () -> {
                            try {
                                log.info("[COLLECTOR] callback: starting RealtimeCaptureService");
                                com.harbor.capturepoc.RealtimeCaptureService s =
                                        new com.harbor.capturepoc.RealtimeCaptureService(
                                                cfg.dbUrl(), cfg.dbUsername(), cfg.dbPassword(), false);
                                s.setStateListener(st -> log.info("[COLLECTOR] RealtimeCapture state={}", st));
                                s.setMessageListener((mid, relId) -> log.info("[COLLECTOR] message inserted mid={} relId={}", mid, relId));
                                s.start();
                                svc[0] = s;
                            } catch (Throwable t) {
                                log.error("[COLLECTOR] failed to start RealtimeCaptureService", t);
                            }
                        },
                        () -> {
                            try {
                                log.info("[COLLECTOR] callback: stopping RealtimeCaptureService");
                                if (svc[0] != null) { svc[0].stop(); svc[0] = null; }
                            } catch (Throwable t) {
                                log.error("[COLLECTOR] failed to stop RealtimeCaptureService", t);
                            }
                        });
            } catch (Throwable t) {
                log.error("[COLLECTOR] failed to wire RealtimeCaptureService callbacks", t);
            }
            engine.getLoadWorker().stateProperty().addListener((obs, oldS, newS) -> {
                log.info("[BridgeDiag][WebView] LoadWorker state={} location={}", newS, engine.getLocation());
                if (newS == javafx.concurrent.Worker.State.SUCCEEDED) {
                    log.info("[JEVE][WebView] Load succeeded");
                    DesktopBridge.inject(engine, bridge);
                }
                else if (newS == javafx.concurrent.Worker.State.FAILED) log.warn("[JEVE][WebView] Load failed");
            });
            String startPage = System.getProperty("rui.testPage", "/index.html");
            log.info("[JEVE][WebView] Loading: http://127.0.0.1:18080{}", startPage);
            engine.load("http://127.0.0.1:18080" + startPage);
            contentArea.getChildren().setAll(webView);
            AnchorPane.setTopAnchor(webView, 0.0);
            AnchorPane.setBottomAnchor(webView, 0.0);
            AnchorPane.setLeftAnchor(webView, 0.0);
            AnchorPane.setRightAnchor(webView, 0.0);

            // Window drag handled by bridge.startDrag/dragTo/endDrag from JS (if needed).
            // Removed temporary scene-level drag filter that blocked About close clicks on header.

            // ---- WindowDiag: event filters on WebView ----
            webView.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
                log.info("[WindowDiag][JavaFX] WebView MOUSE_PRESSED sceneX={} sceneY={}", e.getSceneX(), e.getSceneY());
            });
            webView.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e ->
                log.info("[WindowDiag][JavaFX] WebView MOUSE_RELEASED sceneX={} sceneY={}", e.getSceneX(), e.getSceneY()));
            webView.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e ->
                log.info("[WindowDiag][JavaFX] WebView MOUSE_CLICKED sceneX={} sceneY={}", e.getSceneX(), e.getSceneY()));

            // ---- WindowDiag: Stage state listeners ----
            stage.iconifiedProperty().addListener((obs, o, v) ->
                log.info("[WindowDiag][JavaFX] iconified={}", v));
            stage.showingProperty().addListener((obs, o, v) ->
                log.info("[WindowDiag][JavaFX] showing={}", v));
            stage.focusedProperty().addListener((obs, o, v) ->
                log.info("[WindowDiag][JavaFX] focused={}", v));
        } catch (Exception e) {
            log.error("[PROFILE_HTTP] Failed to start: {}", e.getMessage(), e);
        }
    }

    /**
     * Phase 12：把新 Analysis Runtime 接起来。
     * SkillManager 由 ChatViewApplication 注入（与 SkillWatcher 共享同一 Registry）。
     * 如果 skillManager 为 null，新链不可用，旧 QuickReplyService 继续工作。
     */
    private static void diag(String msg) {
        try {
            var p = java.nio.file.Paths.get(System.getProperty("user.home"), ".rui", "logs", "wire-diag.log");
            java.nio.file.Files.createDirectories(p.getParent());
            try (var w = java.nio.file.Files.newBufferedWriter(p, java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
                w.write(java.time.LocalDateTime.now() + " [" + Thread.currentThread().getName() + "] " + msg + "\n");
            }
        } catch (Exception ignored) {}
    }

    private void wireNewAnalysisRuntime(AIProvider provider) {
        this.lastProvider = provider;
        diag("wireNewAnalysisRuntime: provider=" + (provider != null) + " skillManager=" + (skillManager != null)
                + " hashCode=" + System.identityHashCode(this));
        if (skillManager == null) {
            diag("wireNewAnalysisRuntime: SKIP skillManager==null");
            return;
        }
        try {
            SkillRouter skillRouter = new SkillRouter(skillManager);
            // Phase 15: seed 新 Knowledge Runtime with global knowledge/*.md
            // Phase 25-C: prefer project-root knowledge/; fallback to legacy skills/goutoujunshi/knowledge/.
            java.nio.file.Path globalKnowledgeRoot = resolveKnowledgeRoot();
            var gtjKnowledge = com.harbor.relationshipassistant.application.knowledge.GoutoujunshiKnowledgeSeeder
                    .load(globalKnowledgeRoot);
            KnowledgeRouter knowledgeRouter = new KnowledgeRouter(
                    new InMemoryKnowledgeRetriever(gtjKnowledge));
            PromptAssembler promptAssembler = new PromptAssembler();
            var outputRuntime = new com.harbor.relationshipassistant.application.output.OutputRuntime(
                    new com.harbor.relationshipassistant.application.llm.AiProviderLLMClient(provider));
            this.analysisApplicationService = new AnalysisApplicationService(
                    skillRouter, knowledgeRouter, promptAssembler, outputRuntime);
            // Phase 15: quick-reply → quick-reply Skill；detailed/deep-observation → goutoujunshi
            // Single source of truth for Function→default Skill mapping; consumed by both Factory and Adapter.
            String defaultQuickReplySkill = "quick-reply";
            String defaultDetailAnalysisSkill = "goutoujunshi";
            String defaultDeepObservationSkill = "goutoujunshi";
            this.analysisTaskFactory = new AnalysisTaskFactory(
                    defaultQuickReplySkill, defaultDetailAnalysisSkill, defaultDeepObservationSkill);
            this.contextAdapter = new AnalysisContextAdapter(relRepo, chatRepo, profileService);
            profileHttpServer.setAnalysisApplicationService(analysisApplicationService, analysisTaskFactory, contextAdapter);
            profileHttpServer.setSkillManager(skillManager);
            // Skill Adapter V1: 把 "用户选 Skill + Function 默认 Skill" 统一解析成 SkillResolution。
            var defaultSkillByFunction = java.util.Map.of(
                    com.harbor.relationshipassistant.application.skill.context.AnalysisFunction.QUICK_REPLY, defaultQuickReplySkill,
                    com.harbor.relationshipassistant.application.skill.context.AnalysisFunction.DETAILED_ANALYSIS, defaultDetailAnalysisSkill,
                    com.harbor.relationshipassistant.application.skill.context.AnalysisFunction.DEEP_OBSERVATION, defaultDeepObservationSkill
            );
            var skillAdapter = new com.harbor.relationshipassistant.application.skill.adapter.SkillAdapter(
                    skillRouter, defaultSkillByFunction);
            profileHttpServer.setSkillAdapter(skillAdapter);
            profileHttpServer.setSystemHostPlanner(
                    new com.harbor.relationshipassistant.application.systemhost.SystemHostPlanner());
            diag("wireNewAnalysisRuntime: SUCCESS readySkills=" + skillManager.getReadySkills().size()
                    + " knowledgeItems=" + gtjKnowledge.size() + " analysisAppService=" + (analysisApplicationService != null));
        } catch (Exception e) {
            diag("wireNewAnalysisRuntime: FAILED " + e);
        }
    }

    /**
     * Phase 25-C: JEVE global knowledge root resolution.
     * Prefer project-root {@code knowledge/}; fall back to legacy {@code skills/goutoujunshi/knowledge/}.
     */
    private static java.nio.file.Path resolveKnowledgeRoot() {
        java.nio.file.Path global = java.nio.file.Paths.get("knowledge").toAbsolutePath().normalize();
        if (java.nio.file.Files.isDirectory(global)) {
            org.slf4j.LoggerFactory.getLogger(MainController.class)
                    .info("[JEVE][Bootstrap] Knowledge root resolved: {}", global);
            return global;
        }
        java.nio.file.Path legacy = java.nio.file.Paths.get("skills", "goutoujunshi", "knowledge")
                .toAbsolutePath().normalize();
        org.slf4j.LoggerFactory.getLogger(MainController.class)
                .info("[JEVE][Bootstrap] Global knowledge/ missing; fallback to legacy root: {}", legacy);
        return legacy;
    }
}
