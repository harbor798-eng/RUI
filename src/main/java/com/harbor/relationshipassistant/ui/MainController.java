package com.harbor.relationshipassistant.ui;

import com.harbor.relationshipassistant.application.ai.AiConfigService;
import com.harbor.relationshipassistant.application.ai.ContextBuilder;
import com.harbor.relationshipassistant.application.ai.QuickReplyService;
import com.harbor.relationshipassistant.application.analysis.AnalysisService;
import com.harbor.relationshipassistant.application.analysis.AnalysisServiceFactory;
import com.harbor.relationshipassistant.application.analysis.report.DeepObservationReportService;
import com.harbor.relationshipassistant.application.profile.ProfileService;
import com.harbor.relationshipassistant.infrastructure.ai.AIProvider;
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

    public void inject(DataSourceFactory ds, String aesKey, long relationshipId, Stage stage) {
        this.ds = ds;
        this.aesKey = aesKey;
        this.relationshipId = relationshipId;
        this.stage = stage;
        initialize();
    }

    private void initialize() {
        AesCryptoService crypto = new AesCryptoService(aesKey);
        AiConfigService aiConfig = new AiConfigService(ds, crypto);
        ProfileService profiles = new ProfileService(ds, new AuditLogRepository(ds), crypto);
        this.relRepo = new RelationshipRepository(ds);
        this.chatRepo = new ChatMessageRepository(ds);
        ContextBuilder builder = new ContextBuilder(relRepo, chatRepo, profiles, new ObservationRepository(ds));
        AIProvider provider = aiConfig.buildProvider();
        this.profileService = profiles;
        this.quickReplyService = new QuickReplyService(builder, provider);
        this.analysisService = AnalysisServiceFactory.build(ds, provider);
        this.reportService = new DeepObservationReportService();

        try {
            java.nio.file.Path webDir = java.nio.file.Paths.get("src/main/resources/web").toAbsolutePath();
            if (!java.nio.file.Files.exists(webDir)) webDir = java.nio.file.Paths.get("web").toAbsolutePath();
            profileHttpServer = new ProfileHttpServer(profiles, relationshipId, 18080, webDir);
            profileHttpServer.setQuickReplyService(quickReplyService);
            profileHttpServer.setAnalysisService(analysisService);
            profileHttpServer.setRelationshipRepository(relRepo);
            profileHttpServer.setDeepReportService(reportService);
            profileHttpServer.setChatMessageRepository(chatRepo);
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
            javafx.scene.web.WebEngine engine = webView.getEngine();
            engine.setOnAlert(e -> log.info("[WebView][JS][alert] {}", e.getData()));
            DesktopBridge bridge = new DesktopBridge(stage);
            engine.getLoadWorker().stateProperty().addListener((obs, oldS, newS) -> {
                log.info("[BridgeDiag][WebView] LoadWorker state={} location={}", newS, engine.getLocation());
                if (newS == javafx.concurrent.Worker.State.SUCCEEDED) {
                    log.info("[JEVE][WebView] Load succeeded");
                    DesktopBridge.inject(engine, bridge);
                }
                else if (newS == javafx.concurrent.Worker.State.FAILED) log.warn("[JEVE][WebView] Load failed");
            });
            log.info("[JEVE][WebView] Loading: http://127.0.0.1:18080/index.html");
            engine.load("http://127.0.0.1:18080/index.html");
            contentArea.getChildren().setAll(webView);
            AnchorPane.setTopAnchor(webView, 0.0);
            AnchorPane.setBottomAnchor(webView, 0.0);
            AnchorPane.setLeftAnchor(webView, 0.0);
            AnchorPane.setRightAnchor(webView, 0.0);

            // ---- WindowDiag: event filters on WebView ----
            webView.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e ->
                log.info("[WindowDiag][JavaFX] WebView MOUSE_PRESSED sceneX={} sceneY={}", e.getSceneX(), e.getSceneY()));
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
}
