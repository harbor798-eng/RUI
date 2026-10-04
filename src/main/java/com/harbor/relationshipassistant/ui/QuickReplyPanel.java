package com.harbor.relationshipassistant.ui;

import com.harbor.relationshipassistant.application.ai.AiConfigService;
import com.harbor.relationshipassistant.application.ai.ContextBuilder;
import com.harbor.relationshipassistant.application.ai.GenerationService;
import com.harbor.relationshipassistant.application.ai.QuickReplyService;
import com.harbor.relationshipassistant.application.profile.ProfileService;
import com.harbor.relationshipassistant.infrastructure.ai.AIProvider;
import com.harbor.relationshipassistant.infrastructure.persistence.AuditLogRepository;
import com.harbor.relationshipassistant.infrastructure.persistence.ChatMessageRepository;
import com.harbor.relationshipassistant.infrastructure.persistence.DataSourceFactory;
import com.harbor.relationshipassistant.infrastructure.persistence.GenerationRecordRepository;
import com.harbor.relationshipassistant.infrastructure.persistence.RelationshipRepository;
import com.harbor.relationshipassistant.infrastructure.security.AesCryptoService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * AI 快速回复面板：3 种策略 × 1 条候选，每条带独立刷新。
 */
public class QuickReplyPanel extends BorderPane {

    private static final Logger log = LoggerFactory.getLogger(QuickReplyPanel.class);

    private final long relationshipId;
    private final TextField inputField;
    private final QuickReplyService service;
    private final GenerationService generationService;

    private final Button generateButton = new Button("生成回复");
    private final Label statusLabel = new Label();
    private final VBox candidateArea = new VBox(10);

    private Long activeRecordId;
    private String selectedOriginalText;
    private final List<Button> useButtons = new ArrayList<>();

    /** 当前每策略的第一条候选缓存：strategy -> [text, reason] */
    private final Map<String, String[]> currentCandidates = new LinkedHashMap<>();
    /** 每策略的刷新按钮 */
    private final Map<String, Button> refreshButtons = new LinkedHashMap<>();
    /** 每策略的回复文本 Label */
    private final Map<String, Label> candidateLabels = new LinkedHashMap<>();

    public QuickReplyPanel(long relationshipId, DataSourceFactory ds, TextField inputField, String aesKey) {
        this.relationshipId = relationshipId;
        this.inputField = inputField;

        AesCryptoService crypto = new AesCryptoService(aesKey);
        AiConfigService aiConfig = new AiConfigService(ds, crypto);
        ProfileService profiles = new ProfileService(ds, new AuditLogRepository(ds), crypto);
        ContextBuilder builder = new ContextBuilder(new RelationshipRepository(ds),
                new ChatMessageRepository(ds), profiles,
                new com.harbor.relationshipassistant.infrastructure.persistence.ObservationRepository(ds));
        AIProvider provider = aiConfig.buildProvider();
        this.service = new QuickReplyService(builder, provider);
        this.generationService = new GenerationService(new GenerationRecordRepository(ds));

        buildUi();
    }

    private void buildUi() {
        setStyle("-fx-background-color:#FAFAFE;");

        Label title = new Label("AI 快速回复");
        title.setFont(Font.font(15));
        title.setStyle("-fx-text-fill:#4C1D95; -fx-font-weight:bold;");
        generateButton.setStyle("-fx-base:#7C3AED; -fx-text-fill:white; -fx-font-weight:bold; -fx-background-radius:8; -fx-padding:6 16;");
        HBox topRow = new HBox(10, title, generateButton);
        topRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);

        statusLabel.setTextFill(Color.web("#7C3AED"));
        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-font-size:11px;");

        VBox body = new VBox(10, topRow, statusLabel, candidateArea);
        body.setPadding(new Insets(12));
        VBox.setVgrow(candidateArea, Priority.ALWAYS);
        setCenter(body);

        generateButton.setOnAction(e -> generate());
    }

    private void generate() {
        flushPendingFinalText();
        generateButton.setDisable(true);
        statusLabel.setText("正在生成……");
        log.info("[UI][GENERATE_START] relationshipId={}", relationshipId);

        long rel = relationshipId;
        new Thread(() -> {
            try {
                QuickReplyService.GenerationResult r = service.generateCandidates(rel, null);
                var record = generationService.createFromResult(rel, r);
                Platform.runLater(() -> {
                    activeRecordId = record.getId();
                    selectedOriginalText = null;
                    renderCandidates(r);
                    generateButton.setDisable(false);
                    statusLabel.setText("生成完成，点「使用这条」填入输入框");
                    log.info("[UI][GENERATE_SUCCESS] generationId={}", record.getId());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    generateButton.setDisable(false);
                    statusLabel.setText(friendly(ex));
                    log.error("[UI][GENERATE_ERROR] {}", ex.getMessage());
                });
            }
        }, "quick-reply-gen").start();
    }

    /** 单策略刷新：只重新生成一次完整请求，取该策略第一条替换。 */
    private void refreshStrategy(String strategyName) {
        Button rb = refreshButtons.get(strategyName);
        Label lb = candidateLabels.get(strategyName);
        if (rb != null) rb.setDisable(true);
        if (lb != null) lb.setText("生成中…");
        long rel = relationshipId;
        new Thread(() -> {
            try {
                QuickReplyService.GenerationResult r = service.generateCandidates(rel, null);
                // 找到该策略的第一条
                String newText = null, newReason = null;
                for (Map<String, String> m : r.candidates().flatten()) {
                    if (strategyName.equals(m.get("strategy"))) {
                        newText = m.get("text");
                        newReason = m.get("reason");
                        break;
                    }
                }
                if (newText != null) {
                    final String fText = newText;
                    final String fReason = newReason;
                    Platform.runLater(() -> {
                        currentCandidates.put(strategyName, new String[]{fText, fReason});
                        if (lb != null) lb.setText(fText);
                        if (rb != null) rb.setDisable(false);
                        log.info("[UI][REFRESH] strategy={} done", strategyName);
                    });
                }
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (lb != null) {
                        String[] old = currentCandidates.get(strategyName);
                        if (old != null) lb.setText(old[0]);
                    }
                    if (rb != null) rb.setDisable(false);
                    statusLabel.setText(friendly(ex));
                });
            }
        }, "quick-reply-refresh-" + strategyName).start();
    }

    private void renderCandidates(QuickReplyService.GenerationResult r) {
        candidateArea.getChildren().clear();
        useButtons.clear();
        currentCandidates.clear();
        refreshButtons.clear();
        candidateLabels.clear();

        // 按策略分组，每策略取第一条
        Map<String, Map<String, String>> firstPerStrategy = new LinkedHashMap<>();
        for (Map<String, String> m : r.candidates().flatten()) {
            firstPerStrategy.putIfAbsent(m.get("strategy"), m);
        }

        int idx = 0;
        for (var e : firstPerStrategy.entrySet()) {
            String strategy = e.getKey();
            String text = e.getValue().get("text");
            String reason = e.getValue().get("reason");
            currentCandidates.put(strategy, new String[]{text, reason});

            Label strategyTitle = new Label(strategy);
            strategyTitle.setStyle("-fx-font-size:12px; -fx-text-fill:#5B21B6; -fx-font-weight:bold;");

            Label textLabel = new Label(text);
            textLabel.setWrapText(true);
            textLabel.setMaxWidth(Double.MAX_VALUE);
            textLabel.setStyle("-fx-font-size:13px; -fx-text-fill:#222;");
            candidateLabels.put(strategy, textLabel);

            Button refreshBtn = new Button("↻");
            refreshBtn.setStyle("-fx-font-size:14px; -fx-base:#FFFFFF; -fx-text-fill:#7C3AED;");
            refreshBtn.setOnAction(ev -> refreshStrategy(strategy));
            refreshButtons.put(strategy, refreshBtn);

            Button useBtn = new Button("使用");
            useBtn.setStyle("-fx-base:#7C3AED; -fx-text-fill:white; -fx-font-size:11px; -fx-background-radius:6;");
            final int candidateIdx = idx;
            useBtn.setOnAction(ev -> useCandidate(strategy, text, candidateIdx));
            useButtons.add(useBtn);

            HBox actionRow = new HBox(8, refreshBtn, useBtn);
            actionRow.setAlignment(Pos.CENTER_LEFT);

            VBox cardBody = new VBox(6, textLabel, actionRow);
            cardBody.setPadding(new Insets(10));
            cardBody.setStyle("-fx-background-color:#FFFFFF; -fx-background-radius:10; -fx-border-color:#DDD6FE; -fx-border-radius:10;");

            VBox strategyBlock = new VBox(6, strategyTitle, cardBody);
            candidateArea.getChildren().add(strategyBlock);
            idx++;
        }
    }

    private void useCandidate(String strategy, String text, int index) {
        if (activeRecordId == null) return;
        flushPendingFinalText();
        generationService.selectCandidate(activeRecordId, strategy, index * 3, text);
        selectedOriginalText = text;
        inputField.setText(text);
        log.info("[CANDIDATE][USE] generationId={} strategy={}", activeRecordId, strategy);
    }

    // ---------- 与 ChatViewApplication 的发送汇合 ----------

    public Long getActiveRecordId() { return activeRecordId; }

    public void flushBeforeSend() {
        if (activeRecordId != null) {
            generationService.flushFinalText(activeRecordId, selectedOriginalText, inputField.getText());
        }
    }

    public void onSent(Long chatMessageId) {
        if (activeRecordId != null) {
            generationService.markSent(activeRecordId, chatMessageId);
            activeRecordId = null;
            selectedOriginalText = null;
        }
        for (Button b : useButtons) {
            b.setDisable(true);
            b.setText("已发送");
        }
        statusLabel.setText("已发送");
    }

    public void flushPendingFinalText() {
        if (activeRecordId != null && selectedOriginalText != null && inputField.getText() != null) {
            generationService.flushFinalText(activeRecordId, selectedOriginalText, inputField.getText());
        }
    }

    private static String friendly(Exception ex) {
        String m = ex.getMessage();
        if (m == null) return "生成失败";
        if (m.contains("API Key") || m.contains("未授权")) return "AI API Key 无效";
        if (m.contains("频率")) return "请求过于频繁";
        if (m.contains("超时")) return "AI 请求超时";
        if (m.contains("无法连接")) return "无法连接 AI 服务";
        if (m.contains("JSON") || m.contains("校验")) return "AI 回复格式异常";
        if (m.contains("请先配置")) return "请先配置 AI";
        return "生成失败";
    }
}
