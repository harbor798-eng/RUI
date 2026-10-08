package com.harbor.relationshipassistant.ui;

import com.harbor.relationshipassistant.application.ai.AiConfigService;
import com.harbor.relationshipassistant.application.relationship.RelationshipService;
import com.harbor.relationshipassistant.application.chat.ChatMessageRevisionView;
import com.harbor.relationshipassistant.application.chat.ChatPage;
import com.harbor.relationshipassistant.application.chat.ChatService;
import com.harbor.relationshipassistant.common.config.AppConfig;
import com.harbor.relationshipassistant.domain.chat.ChatMessage;
import com.harbor.relationshipassistant.domain.chat.MessageType;
import com.harbor.relationshipassistant.domain.chat.SenderType;
import com.harbor.relationshipassistant.infrastructure.persistence.AuditLogRepository;
import com.harbor.relationshipassistant.infrastructure.persistence.ChatMessageRepository;
import com.harbor.relationshipassistant.infrastructure.persistence.DataSourceFactory;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ListCell;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 微信风格聊天界面（阶段2：编辑 + 手动新增 + 本地输入 + 软删除 + 修改历史）。
 * ME 右侧 / OTHER 左侧 / SYSTEM 居中；数据来自 MySQL 当前有效 chat_message；
 * 修改先写 revision 再更新（事务）；原始 source_* 不被覆盖；时间口径 Asia/Shanghai。
 */
public class ChatViewApplication extends Application {

    /** Set after stage.show(); used by single-instance activator to bring window forward. */
    public static volatile javafx.stage.Stage primaryStage;
    private MainController controllerRef;

    private static final Logger log = LoggerFactory.getLogger(ChatViewApplication.class);
    private static final int PAGE_SIZE = 50;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private ChatService chat;
    private long relationshipId;
    private int total;
    private int currentOffset;

    private VBox messageBox;
    private ScrollPane scrollPane;
    private Button earlierButton;
    private Label pageLabel;
    private TextField inputField;
    private Button sendButton;
    private QuickReplyPanel replyPanel;
    private Label obsStatusLabel;
    private VBox chatBottom;
    private Label chatHeader;
    private Label sessionName;
    private StackPane centerHost;
    private com.harbor.relationshipassistant.infrastructure.persistence.RelationshipRepository relRepo;
    private AppConfig appConfig;
    private DataSourceFactory appDs;

    // Phase 23B: 实时采集服务
    private com.harbor.capturepoc.RealtimeCaptureService realtimeCaptureService;
    private Label captureStatusLabel;
    private Button captureToggleBtn;

    // Phase 24: Skill 动态发现
    private com.harbor.relationshipassistant.application.skill.SkillManager skillManager;
    private com.harbor.relationshipassistant.application.skill.SkillWatcher skillWatcher;

    @Override
    public void start(Stage stage) {
        AppConfig config = new AppConfig();
        appConfig = config;
        appDs = new DataSourceFactory(config);
        appDs.migrate();
        DataSourceFactory ds = appDs;
        this.relRepo = new com.harbor.relationshipassistant.infrastructure.persistence.RelationshipRepository(ds);
        chat = new ChatService(new ChatMessageRepository(ds), new AuditLogRepository(ds));

        // Phase 24: 启动 Skill 动态发现（先 Watcher 再 initialize，依靠 debounce+generation 合并启动期事件）
        try {
            java.nio.file.Path skillsRoot = java.nio.file.Paths.get("skills");
            skillManager = new com.harbor.relationshipassistant.application.skill.SkillManager(
                    skillsRoot, new com.harbor.relationshipassistant.application.skill.DefaultSkillLoader());
            skillWatcher = new com.harbor.relationshipassistant.application.skill.SkillWatcher(skillsRoot, skillManager);
            skillWatcher.start();
            skillManager.initialize();
        } catch (Throwable t) {
            log.warn("[Skill] startup failed: {}", t.toString());
        }

        // ---- Phase 21: 首次启动检查 AI 配置 ----
        com.harbor.relationshipassistant.infrastructure.security.AesCryptoService crypto =
                new com.harbor.relationshipassistant.infrastructure.security.AesCryptoService(config.aesKey());
        AiConfigService aiCfg = new AiConfigService(ds, crypto);
        boolean aiReady = aiCfg.loadView().filter(v -> v.hasKey()).isPresent();
        log.info("[JEVE][Startup] AI configuration detected={}", aiReady);
        if (!aiReady) {
            log.info("[JEVE][Onboarding] Opening welcome + AI setup");
            boolean ok = showOnboarding(stage, ds, config);
            if (!ok) {
                log.info("[JEVE][Onboarding] User cancelled, exiting");
                Platform.exit();
                return;
            }
            log.info("[JEVE][Onboarding] AI setup completed");
        }

        // 开发调试参数 --rel=N 仍可用；正常启动从数据库选第一个 active
        String relParam = getParameters().getNamed().get("rel");
        if (relParam != null) {
            relationshipId = Long.parseLong(relParam);
            log.info("[JEVE][Startup] Using debug rel parameter={}", relationshipId);
        } else {
            List<com.harbor.relationshipassistant.domain.relationship.Relationship> actives = relRepo.listActive();
            log.info("[JEVE][Relationship] Loaded active relationships: {}", actives.size());
            if (actives.isEmpty()) {
                relationshipId = -1; // 空关系态
                log.info("[JEVE][Relationship] No active relationships, entering empty state");
            } else {
                relationshipId = actives.get(0).getId();
                log.info("[JEVE][Relationship] Current relationship: {} ({})", relationshipId, actives.get(0).getName());
            }
        }
        // ---------- Load FXML main view ----------
        try {
            java.net.URL fxmlUrl = getClass().getResource("/fxml/MainView.fxml");
            log.info("[UI] Loading FXML from: {}", fxmlUrl);
            if (fxmlUrl == null) throw new IllegalStateException("Cannot find /fxml/MainView.fxml");
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();
            MainController controller = loader.getController();
            controllerRef = controller;
            controller.setSkillManager(skillManager);
            controller.inject(ds, config.aesKey(), relationshipId, stage);

            stage.initStyle(StageStyle.TRANSPARENT);
            stage.setTitle("JEVE");
            // Wrap FXML root in a rounded frame so the whole window has soft corners.
            javafx.scene.layout.StackPane frame = new javafx.scene.layout.StackPane(root);
            frame.setStyle("-fx-background-color: #F6F5F2; -fx-background-radius: 18; -fx-background-insets: 0;");
            javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
            double r = 18;
            clip.setArcWidth(r * 2);
            clip.setArcHeight(r * 2);
            clip.widthProperty().bind(frame.widthProperty());
            clip.heightProperty().bind(frame.heightProperty());
            frame.setClip(clip);
            Scene scene = new Scene(frame, 350, 650, javafx.scene.paint.Color.TRANSPARENT);
            stage.setScene(scene);
            stage.centerOnScreen();
            try {
                var iconUrl = getClass().getResource("/web/rui-icon.png");
                if (iconUrl != null) stage.getIcons().add(new javafx.scene.image.Image(iconUrl.toExternalForm()));
            } catch (Exception e) {
                log.warn("[UI] icon load failed: {}", e.toString());
            }
            stage.show();
            primaryStage = stage;
            log.info("[UI_SIZE] width={} height={}", String.format("%.0f", stage.getWidth()), String.format("%.0f", stage.getHeight()));
            log.info("[UI] Main FXML window shown, UNDECORATED");
        } catch (Exception e) {
            log.error("[UI] Failed to load FXML: {}", e.getMessage(), e);
            Platform.exit();
        }
    }

    private String currentRelationshipName() {        try {
            for (var r : relRepo.listActive()) {
                if (r.getId() == relationshipId) return r.getName();
            }
        } catch (Exception ignored) {}
        return "未知";
    }

    private void openObservationList() {
        try {
            var repo = new com.harbor.relationshipassistant.infrastructure.persistence.ObservationRepository(appDs);
            new ObservationListDialog(relationshipId, repo).showAndWait();
        } catch (Exception e) {
            log.error("[UI] openObservationList failed: {}", e.getMessage());
        }
    }

    private void openSettings() {
        try {
            var crypto = new com.harbor.relationshipassistant.infrastructure.security.AesCryptoService(appConfig.aesKey());
            var aiService = new AiConfigService(appDs, crypto);
            var profiles = new com.harbor.relationshipassistant.application.profile.ProfileService(
                    appDs, new AuditLogRepository(appDs), crypto);
            new AiSettingsDialog(null, aiService, profiles).showAndWait();
        } catch (Exception e) {
            log.error("[UI] openSettings failed: {}", e.getMessage());
        }
    }

    private static Button menuBtn(String text, boolean primary) {
        Button b = new Button(text);
        b.setStyle(primary
                ? "-fx-base:#7C3AED; -fx-text-fill:white; -fx-font-size:12px; -fx-padding:6 14;"
                : "-fx-background-color:#FFFFFF; -fx-text-fill:#5B21B6; -fx-border-color:#DDD0FA; -fx-border-radius:6; -fx-background-radius:6; -fx-font-size:12px; -fx-padding:6 14;");
        return b;
    }

    private void loadRelationships(ComboBox<com.harbor.relationshipassistant.domain.relationship.Relationship> combo) {
        try {
            List<com.harbor.relationshipassistant.domain.relationship.Relationship> list = relRepo.listActive();
            combo.getItems().addAll(list);
            // 选中当前 relationshipId
            for (var r : list) {
                if (r.getId() == relationshipId) {
                    combo.getSelectionModel().select(r);
                    chatHeader.setText(r.getName());
        if (sessionName != null) sessionName.setText(r.getName());
                    log.info("[UI][RELATIONSHIP] loaded id={} name={}", r.getId(), r.getName());
                    return;
                }
            }
            log.warn("[UI][RELATIONSHIP] relationship not found id={}", relationshipId);
            chatHeader.setText("(未知关系)");
        } catch (Exception e) {
            log.error("[UI][RELATIONSHIP] load failed: {}", e.getMessage());
            chatHeader.setText("(加载失败)");
        }
    }

    // ---------------- 加载 / 分页 ----------------

    private void loadInitialPage() {
        try {
            ChatPage first = chat.loadPage(relationshipId, 0, 1);
            total = first.getTotal();
            String forced = getParameters().getNamed().get("offset");
            if (forced != null) {
                currentOffset = Math.max(0, Math.min(Integer.parseInt(forced), total));
                ChatPage page = chat.loadPage(relationshipId, currentOffset, PAGE_SIZE);
                appendRows(page.getMessages());
                updateStatus();
                log.info("[Chat] UI 加载完成 relationship={} offset={} 已渲染={}",
                        relationshipId, currentOffset, page.getMessages().size());
            } else {
                currentOffset = Math.max(0, total - PAGE_SIZE);
                ChatPage page = chat.loadPage(relationshipId, currentOffset, PAGE_SIZE);
                appendRows(page.getMessages());
                updateStatus();
                Platform.runLater(() -> scrollPane.setVvalue(1.0));
                log.info("[Chat] UI 加载完成 relationship={} 已渲染={}",
                        relationshipId, page.getMessages().size());
            }
        } catch (Exception e) {
            log.error("[Chat] 查询异常（初始加载）: {}", e.getMessage(), e);
        }
    }

    private void loadEarlier() {
        try {
            int newOffset = Math.max(0, currentOffset - PAGE_SIZE);
            ChatPage page = chat.loadPage(relationshipId, newOffset, currentOffset - newOffset);
            List<ChatMessage> rows = page.getMessages();
            double oldHeight = messageBox.getHeight();
            prependRows(rows);
            currentOffset = newOffset;
            updateStatus();
            Platform.runLater(() -> {
                double newHeight = messageBox.getHeight();
                double delta = newHeight - oldHeight;
                double v = scrollPane.getVvalue();
                double max = scrollPane.getVmax();
                scrollPane.setVvalue(Math.min(max, v + delta / Math.max(1, newHeight)));
            });
        } catch (Exception e) {
            log.error("[Chat] 查询异常（加载更早）: {}", e.getMessage(), e);
        }
    }

    /** 修改/新增/删除后重载当前页：按新 message_time + id 重新排序。 */
    private void reloadCurrentPage() {
        try {
            ChatPage p = chat.loadPage(relationshipId, currentOffset, PAGE_SIZE);
            total = p.getTotal();
            messageBox.getChildren().clear();
            appendRows(p.getMessages());
            updateStatus();
            log.info("[Chat] UI 重新加载完成 relationship={} offset={} 已渲染={}",
                    relationshipId, currentOffset, p.getMessages().size());
        } catch (Exception e) {
            log.error("[Chat] 查询异常（刷新页面）: {}", e.getMessage(), e);
        }
    }

    private void appendRows(List<ChatMessage> rows) {
        for (ChatMessage m : rows) messageBox.getChildren().add(renderRow(m));
    }

    private void prependRows(List<ChatMessage> rows) {
        int idx = 0;
        for (ChatMessage m : rows) messageBox.getChildren().add(idx++, renderRow(m));
    }

    // ---------------- 渲染 + 右键菜单 ----------------

    private Node renderRow(ChatMessage m) {
        try {
            String time = m.getMessageTime() == null ? "" : TIME_FMT.format(m.getMessageTime());
            if (m.getSenderType() == SenderType.SYSTEM) {
                VBox box = new VBox(2);
                box.setAlignment(Pos.CENTER);
                Label sys = new Label(m.getContent());
                sys.setTextFill(Color.GRAY);
                sys.setStyle("-fx-font-size:11px; -fx-font-style:italic;");
                sys.setWrapText(true);
                sys.setMaxWidth(560);
                sys.setAlignment(Pos.CENTER);
                Label t = new Label(time);
                t.setTextFill(Color.GRAY);
                t.setStyle("-fx-font-size:10px;");
                box.getChildren().addAll(sys, t);
                attachMenu(box, m, false);
                return box;
            }
            boolean me = m.getSenderType() == SenderType.ME;
            Label bubble = new Label(m.getContent());
            bubble.setWrapText(true);
            bubble.setMaxWidth(430);
            bubble.setPadding(new Insets(9, 12, 9, 12));
            bubble.setFont(Font.font(14));
            String bg = me ? "#95EC69" : "#FFFFFF";
            bubble.setBackground(new javafx.scene.layout.Background(
                    new javafx.scene.layout.BackgroundFill(Color.web(bg),
                            new javafx.scene.layout.CornerRadii(6), Insets.EMPTY)));
            bubble.setBorder(new javafx.scene.layout.Border(
                    new javafx.scene.layout.BorderStroke(Color.web("#D8D8D8"),
                            javafx.scene.layout.BorderStrokeStyle.SOLID,
                            new javafx.scene.layout.CornerRadii(6), javafx.scene.layout.BorderWidths.DEFAULT)));

            Label t = new Label(time);
            t.setTextFill(Color.GRAY);
            t.setStyle("-fx-font-size:10px;");
            VBox col = new VBox(3, bubble, t);
            HBox row = new HBox(col);
            row.setAlignment(me ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            HBox.setHgrow(col, Priority.ALWAYS);
            col.setAlignment(me ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            attachMenu(row, m, true);
            return row;
        } catch (Exception e) {
            log.error("[Chat] 消息渲染异常 id={} sid={} : {}", m.getId(), m.getSourceMessageId(), e.getMessage(), e);
            Label fallback = new Label("[渲染失败] " + (m.getSourceMessageId() == null ? "" : m.getSourceMessageId()));
            fallback.setTextFill(Color.GRAY);
            return fallback;
        }
    }

    private void attachMenu(Node row, ChatMessage m, boolean editable) {
        ContextMenu menu = new ContextMenu();
        MenuItem editContent = new MenuItem("修改内容");
        editContent.setOnAction(e -> editContent(m));
        MenuItem editSender = new MenuItem("修改发送者");
        editSender.setOnAction(e -> editSender(m));
        MenuItem editType = new MenuItem("修改类型");
        editType.setOnAction(e -> editType(m));
        MenuItem editTime = new MenuItem("修改时间");
        editTime.setOnAction(e -> editTime(m));
        MenuItem delete = new MenuItem("删除消息（软删除）");
        delete.setOnAction(e -> softDelete(m));
        MenuItem history = new MenuItem("查看修改历史");
        history.setOnAction(e -> showHistory(m));

        if (editable) {
            menu.getItems().addAll(editContent, editSender, editType, editTime, delete, history);
        } else {
            menu.getItems().add(history); // SYSTEM 仅可查看历史
        }
        row.setOnContextMenuRequested(ev -> menu.show(row, ev.getScreenX(), ev.getScreenY()));
    }

    // ---------------- 编辑操作 ----------------

    private void editContent(ChatMessage m) {
        TextInputDialog d = new TextInputDialog(m.getContent());
        d.setTitle("修改消息内容");
        d.setHeaderText("消息 ID=" + m.getId());
        d.setContentText("新内容：");
        Optional<String> r = d.showAndWait();
        if (r.isPresent() && !r.get().equals(m.getContent())) {
            chat.editMessage(m.getId(), m.getSenderType(), m.getMessageType(), r.get(), m.getMessageTime());
            reloadCurrentPage();
        }
    }

    private void editSender(ChatMessage m) {
        ChoiceDialog<SenderType> d = new ChoiceDialog<>(m.getSenderType(), SenderType.ME, SenderType.OTHER);
        d.setTitle("修改发送者");
        d.setHeaderText("消息 ID=" + m.getId() + "（仅 ME/OTHER）");
        d.setContentText("发送者：");
        Optional<SenderType> r = d.showAndWait();
        if (r.isPresent() && r.get() != m.getSenderType()) {
            chat.editMessage(m.getId(), r.get(), m.getMessageType(), m.getContent(), m.getMessageTime());
            reloadCurrentPage();
        }
    }

    private void editType(ChatMessage m) {
        ChoiceDialog<MessageType> d = new ChoiceDialog<>(m.getMessageType(), MessageType.values());
        d.setTitle("修改消息类型");
        d.setHeaderText("消息 ID=" + m.getId());
        d.setContentText("类型：");
        Optional<MessageType> r = d.showAndWait();
        if (r.isPresent() && r.get() != m.getMessageType()) {
            chat.editMessage(m.getId(), m.getSenderType(), r.get(), m.getContent(), m.getMessageTime());
            reloadCurrentPage();
        }
    }

    private void editTime(ChatMessage m) {
        LocalDateTime r = askDateTime("修改消息时间", "消息 ID=" + m.getId(), m.getMessageTime());
        if (r != null) {
            chat.editMessage(m.getId(), m.getSenderType(), m.getMessageType(), m.getContent(), r);
            reloadCurrentPage();
        }
    }

    private void softDelete(ChatMessage m) {
        Alert c = new Alert(Alert.AlertType.CONFIRMATION);
        c.setTitle("删除消息");
        c.setHeaderText("消息 ID=" + m.getId());
        c.setContentText("将执行软删除：界面不再显示，原始 source 信息与修改历史保留，之后可恢复。确定？");
        Optional<ButtonType> r = c.showAndWait();
        if (r.isPresent() && r.get() == ButtonType.OK) {
            chat.softDeleteMessage(m.getId());
            reloadCurrentPage();
        }
    }

    private void showHistory(ChatMessage m) {
        List<ChatMessageRevisionView> revs = chat.listRevisions(m.getId());
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        if (revs.isEmpty()) {
            box.getChildren().add(new Label("暂无修改历史"));
        } else {
            for (ChatMessageRevisionView r : revs) {
                Label l = new Label(String.format(
                        "[%s] %s  修改前：发送者=%s 类型=%s 时间=%s 内容=%s",
                        r.getModifiedAt() == null ? "?" : DT_FMT.format(r.getModifiedAt()),
                        r.getOperation(),
                        r.getBeforeSender(), r.getBeforeType(),
                        r.getBeforeTime() == null ? "?" : DT_FMT.format(r.getBeforeTime()),
                        r.getBeforeContent() == null ? "null" : r.getBeforeContent()));
                l.setWrapText(true);
                box.getChildren().add(l);
            }
        }
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("修改历史");
        a.setHeaderText("消息 ID=" + m.getId() + "（共 " + revs.size() + " 条）");
        a.getDialogPane().setContent(box);
        a.getDialogPane().setPrefWidth(640);
        a.showAndWait();
    }

    // ---------------- 添加历史消息 / 本地输入 ----------------

    private void showAddManualDialog() {
        Dialog<Object[]> d = new Dialog<>();
        d.setTitle("添加历史消息");
        d.setHeaderText("手动补录（source_type=MANUAL，原始 source_message_id 为空）");
        ButtonType ok = new ButtonType("确定", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);

        DatePicker dp = new DatePicker(LocalDate.now());
        TextField tf = new TextField(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        ChoiceDialog<SenderType> senderPicker = new ChoiceDialog<>(SenderType.OTHER, SenderType.ME, SenderType.OTHER);
        senderPicker.setTitle("发送者");
        senderPicker.setContentText("发送者：");
        senderPicker.showAndWait();
        ChoiceDialog<MessageType> typePicker = new ChoiceDialog<>(MessageType.TEXT, MessageType.values());
        typePicker.setTitle("消息类型");
        typePicker.setContentText("类型：");
        typePicker.showAndWait();
        TextField content = new TextField();
        content.setPromptText("消息内容");

        VBox v = new VBox(8,
                new Label("时间（Asia/Shanghai 墙钟）："), new HBox(8, dp, tf),
                new Label("发送者："), new Label(senderPicker.getSelectedItem() == null ? "OTHER" : senderPicker.getSelectedItem().name()),
                new Label("类型："), new Label(typePicker.getSelectedItem() == null ? "TEXT" : typePicker.getSelectedItem().name()),
                new Label("内容："), content);
        v.setPadding(new Insets(12));
        d.getDialogPane().setContent(v);

        d.setResultConverter(bt -> {
            if (bt.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                try {
                    LocalDateTime t = LocalDateTime.of(dp.getValue(),
                            LocalTime.parse(tf.getText().trim(), DateTimeFormatter.ofPattern("HH:mm")));
                    return new Object[]{t, senderPicker.getSelectedItem(), typePicker.getSelectedItem(), content.getText()};
                } catch (Exception ex) {
                    return null;
                }
            }
            return null;
        });

        Optional<Object[]> r = d.showAndWait();
        if (r.isPresent() && r.get()[3] != null && !((String) r.get()[3]).isBlank()) {
            LocalDateTime t = (LocalDateTime) r.get()[0];
            SenderType s = (SenderType) r.get()[1];
            MessageType ty = (MessageType) r.get()[2];
            String c = (String) r.get()[3];
            ChatMessage added = chat.addManualMessage(relationshipId, s, ty, c, t);
            // 按 (message_time, id) 定位新增消息所在页
            int rank = chat.rankOf(relationshipId, added.getId());
            if (rank >= 0) {
                currentOffset = Math.max(0, (rank / PAGE_SIZE) * PAGE_SIZE);
            }
            reloadCurrentPage();
        }
    }

    private void sendLocal() {
        String text = inputField.getText();
        if (text == null || text.isBlank()) return;
        ChatMessage sent;
        Long genId = replyPanel.getActiveRecordId();
        if (genId != null) {
            // AI 辅助：先把最终文本落库，再建消息并反向关联
            replyPanel.flushBeforeSend();
            sent = chat.addAiAssistedMessage(relationshipId, text, genId);
            replyPanel.onSent(sent.getId());
        } else {
            sent = chat.addLocalMessage(relationshipId, text);
        }
        // 复制最终文本到系统剪贴板（不是 AI 原文）
        boolean copied = false;
        try {
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(text);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
            copied = true;
            log.info("[UI][COPY] final text copied to clipboard, len={}", text.length());
        } catch (Exception e) {
            log.error("[UI][COPY] clipboard failed: {}", e.getMessage());
        }
        inputField.clear();
        // 更新按钮状态
        if (copied) {
            sendButton.setText("已记录并复制");
            sendButton.setDisable(true);
        } else {
            sendButton.setText("复制失败，请手动复制");
            sendButton.setDisable(true);
        }
        ChatPage first = chat.loadPage(relationshipId, 0, 1);
        total = first.getTotal();
        currentOffset = Math.max(0, total - PAGE_SIZE);
        reloadCurrentPage();
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    /** 时间输入对话框：日期 + HH:mm，按 Asia/Shanghai 墙钟理解。 */
    private LocalDateTime askDateTime(String title, String header, LocalDateTime current) {
        Dialog<LocalDateTime> d = new Dialog<>();
        d.setTitle(title);
        d.setHeaderText(header);
        ButtonType ok = new ButtonType("确定", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        DatePicker dp = new DatePicker(current == null ? LocalDate.now() : current.toLocalDate());
        TextField tf = new TextField(current == null
                ? LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                : current.format(DateTimeFormatter.ofPattern("HH:mm")));
        VBox v = new VBox(8, new Label("时间（Asia/Shanghai 墙钟，与微信界面一致）："),
                new HBox(8, dp, tf));
        v.setPadding(new Insets(12));
        d.getDialogPane().setContent(v);
        d.setResultConverter(bt -> {
            if (bt.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                try {
                    return LocalDateTime.of(dp.getValue(),
                            LocalTime.parse(tf.getText().trim(), DateTimeFormatter.ofPattern("HH:mm")));
                } catch (Exception ex) {
                    return null;
                }
            }
            return null;
        });
        return d.showAndWait().orElse(null);
    }

    // ---------------- 状态栏 ----------------

    private void updateStatus() {
        earlierButton.setDisable(currentOffset == 0);
        earlierButton.setText(currentOffset == 0 ? "已是最早消息"
                : "加载更早消息（剩余 " + currentOffset + " 条）");
        pageLabel.setText(String.format("共 %d 条，当前显示 %d–%d",
                total, currentOffset + 1, Math.min(total, currentOffset + PAGE_SIZE)));
    }

    // ================= Phase 21: Onboarding / Empty State / New Relationship =================

    /** 空关系态：centerHost 占位。 */
    private VBox buildEmptyState(DataSourceFactory ds) {
        Label title = new Label("还没有关系");
        title.setStyle("-fx-font-size:20px; -fx-font-weight:bold; -fx-text-fill:#333;");
        Label sub = new Label("创建一段关系后开始使用 JEVE");
        sub.setStyle("-fx-font-size:13px; -fx-text-fill:#888;");
        Button create = new Button("＋ 新建关系");
        create.setStyle("-fx-base:#7C3AED; -fx-text-fill:white; -fx-font-size:13px; -fx-padding:8 20;");
        create.setOnAction(e -> {
            com.harbor.relationshipassistant.domain.relationship.Relationship r = showNewRelationshipDialog(ds);
            if (r != null) {
                log.info("[JEVE][Relationship] Created new relationship id={} name={}", r.getId(), r.getName());
                onRelationshipSelected(r);
            }
        });
        Label hint = new Label("你也可以稍后再创建");
        hint.setStyle("-fx-font-size:11px; -fx-text-fill:#aaa;");
        VBox box = new VBox(14, title, sub, create, hint);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    /** 新建关系 Dialog。 */
    private com.harbor.relationshipassistant.domain.relationship.Relationship showNewRelationshipDialog(DataSourceFactory ds) {
        Dialog<com.harbor.relationshipassistant.domain.relationship.Relationship> d = new Dialog<>();
        d.setTitle("新建关系");
        d.setHeaderText("填写对方称呼，开始一段新关系");
        TextField name = new TextField();
        name.setPromptText("例如：小雨、女朋友、她");
        TextField myName = new TextField();
        myName.setPromptText("你希望对方怎么称呼你（可选）");
        ComboBox<com.harbor.relationshipassistant.domain.relationship.RelationshipStage> stage = new ComboBox<>();
        stage.getItems().addAll(com.harbor.relationshipassistant.domain.relationship.RelationshipStage.values());
        stage.setValue(com.harbor.relationshipassistant.domain.relationship.RelationshipStage.INITIAL_CONTACT);
        stage.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(com.harbor.relationshipassistant.domain.relationship.RelationshipStage s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : s.getLabel());
            }
        });
        stage.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(com.harbor.relationshipassistant.domain.relationship.RelationshipStage s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : s.getLabel());
            }
        });
        GridPane g = new GridPane();
        g.setVgap(10); g.setHgap(10); g.setPadding(new Insets(20));
        g.addRow(0, new Label("对方称呼"), name);
        g.addRow(1, new Label("我的称呼"), myName);
        g.addRow(2, new Label("当前阶段"), stage);
        d.getDialogPane().setContent(g);
        ButtonType ok = new ButtonType("创建", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        d.setResultConverter(bt -> {
            if (bt != ok) return null;
            if (name.getText() == null || name.getText().isBlank()) {
                new Alert(Alert.AlertType.ERROR, "对方称呼不能为空").showAndWait();
                return null;
            }
            try {
                RelationshipService svc = new RelationshipService(ds,
                        new com.harbor.relationshipassistant.infrastructure.persistence.RelationshipRepository(ds),
                        new AuditLogRepository(ds));
                return svc.create(name.getText().trim(),
                        myName.getText() == null || myName.getText().isBlank() ? "我" : myName.getText().trim(),
                        stage.getValue());
            } catch (Exception ex) {
                log.error("[JEVE][Relationship] create failed: {}", ex.getMessage());
                new Alert(Alert.AlertType.ERROR, "创建关系失败：" + ex.getMessage()).showAndWait();
                return null;
            }
        });
        return d.showAndWait().orElse(null);
    }

    /** 选中/新建关系后：更新当前 relationshipId、聊天头、重建 replyPanel、刷新聊天。 */
    private void onRelationshipSelected(com.harbor.relationshipassistant.domain.relationship.Relationship r) {
        relationshipId = r.getId();
        chatHeader.setText(r.getName());
        if (sessionName != null) sessionName.setText(r.getName());
        // 重建 replyPanel
        replyPanel = new QuickReplyPanel(relationshipId, appDs, inputField, appConfig.aesKey());
        replyPanel.setStyle("-fx-background-color:#FFFFFF;");
        centerHost.getChildren().setAll(replyPanel);
        StackPane.setMargin(replyPanel, new Insets(8, 20, 16, 20));
        reloadCurrentPage();
        log.info("[JEVE][Relationship] switched to id={} name={}", r.getId(), r.getName());
    }

    // Phase 23B/23C: 实时采集开关 + 状态同步 + 消息自动刷新
    private void onCaptureToggle() {
        if (realtimeCaptureService != null && realtimeCaptureService.isRunning()) {
            log.info("[RealtimeCaptureUI] Stop requested");
            try {
                realtimeCaptureService.stop();
                // UI 更新由 StateListener 回调处理，这里直接兜底
                updateCaptureUiState("STOPPED");
                log.info("[RealtimeCaptureUI] Capture service stopped");
            } catch (Exception ex) {
                log.error("[RealtimeCaptureUI] Stop failed: {}", ex.getMessage());
            }
        } else {
            log.info("[RealtimeCaptureUI] Start requested");
            try {
                String url = appConfig.dbUrl();
                String user = appConfig.dbUsername();
                String pass = appConfig.dbPassword();
                realtimeCaptureService = new com.harbor.capturepoc.RealtimeCaptureService(url, user, pass, false);
                realtimeCaptureService.setStateListener(state -> Platform.runLater(() -> updateCaptureUiState(state)));
                realtimeCaptureService.setMessageListener((mid, relId) -> Platform.runLater(() -> {
                    log.info("[RealtimeCaptureUI] Message inserted id={} relId={}", mid, relId);
                    if (relId == relationshipId) {
                        log.info("[RealtimeCaptureUI] Refresh chat for relationshipId={}", relId);
                        reloadCurrentPage();
                    }
                }));
                realtimeCaptureService.start();
                updateCaptureUiState("RUNNING");
                log.info("[RealtimeCaptureUI] Capture service started");
            } catch (Exception ex) {
                updateCaptureUiState("ERROR");
                log.error("[RealtimeCaptureUI] Failed to start: {}", ex.getMessage());
            }
        }
    }

    private void updateCaptureUiState(String state) {
        switch (state) {
            case "RUNNING" -> {
                captureStatusLabel.setText("实时采集：● 运行中");
                captureStatusLabel.setStyle("-fx-font-size:12px; -fx-text-fill:#16A34A;");
                captureToggleBtn.setText("停止实时采集");
            }
            case "STOPPED" -> {
                captureStatusLabel.setText("实时采集：○ 未启动");
                captureStatusLabel.setStyle("-fx-font-size:12px; -fx-text-fill:#666;");
                captureToggleBtn.setText("开始实时采集");
            }
            case "ERROR" -> {
                captureStatusLabel.setText("实时采集：⚠ 采集异常");
                captureStatusLabel.setStyle("-fx-font-size:12px; -fx-text-fill:#DC2626;");
                captureToggleBtn.setText("重新启动");
            }
        }
    }

    private void runDetailAnalysis(VBox container, long relId, DataSourceFactory ds, AppConfig config,
                                   com.harbor.relationshipassistant.infrastructure.ai.AIProvider provider) {
        container.getChildren().clear();
        container.getChildren().add(new Label("正在分析…"));
        new Thread(() -> {
            try {
                var profiles = new com.harbor.relationshipassistant.application.profile.ProfileService(
                        ds, new AuditLogRepository(ds),
                        new com.harbor.relationshipassistant.infrastructure.security.AesCryptoService(config.aesKey()));
                var builder = new com.harbor.relationshipassistant.application.ai.ContextBuilder(
                        new com.harbor.relationshipassistant.infrastructure.persistence.RelationshipRepository(ds),
                        new com.harbor.relationshipassistant.infrastructure.persistence.ChatMessageRepository(ds),
                        profiles,
                        new com.harbor.relationshipassistant.infrastructure.persistence.ObservationRepository(ds));
                var ctx = builder.build(relId, null);
                String sys = "你是恋爱关系分析助手。根据聊天上下文判断对方当前情绪。只返回JSON："
                        + "{\"primaryEmotion\":\"高兴\",\"emotions\":[{\"name\":\"高兴\",\"probability\":0.6}],"
                        + "\"reason\":\"50-100字中文分析\"}。probability 0~1，总和约1，最多3-5个。";
                var req = com.harbor.relationshipassistant.infrastructure.ai.AIRequest.of(sys,
                        java.util.List.of(new com.harbor.relationshipassistant.infrastructure.ai.AIRequest.Turn("user", ctx.humanContext())));
                req.setMaxTokens(400); req.setTemperature(0.3);
                var resp = provider.generate(req);
                String text = resp.text().replaceAll("```json","").replaceAll("```","").trim();
                var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(text);
                String primary = json.path("primaryEmotion").asText("未知");
                String reason = json.path("reason").asText("");
                StringBuilder emos = new StringBuilder();
                for (var e : json.path("emotions")) {
                    emos.append(e.path("name").asText()).append("  ")
                        .append(String.format("%.0f%%", e.path("probability").asDouble(0)*100)).append("\n");
                }
                Platform.runLater(() -> {
                    container.getChildren().clear();
                    container.getChildren().add(new Label("对方当前状态"));
                    container.getChildren().add(new Label(primary));
                    container.getChildren().add(new Separator());
                    container.getChildren().add(new Label("AI 判断"));
                    container.getChildren().add(new Label(emos.toString()));
                    container.getChildren().add(new Separator());
                    container.getChildren().add(new Label("分析依据"));
                    container.getChildren().add(new Label(reason));
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    container.getChildren().clear();
                    container.getChildren().add(new Label("暂时无法完成分析"));
                });
            }
        }, "emotion-analysis").start();
    }

    public void stop() throws Exception {
        if (realtimeCaptureService != null && realtimeCaptureService.isRunning()) {
            log.info("[RealtimeCaptureUI] Application stopping, stopping capture service");
            try { realtimeCaptureService.stop(); } catch (Exception ignored) {}
        }
        try { if (controllerRef != null) controllerRef.shutdown(); } catch (Exception ignored) {}
        try { ChatViewLauncher.closeLock(); } catch (Exception ignored) {}
        primaryStage = null;
        super.stop();
        // Ensure JVM exits even if some non-daemon thread lingers.
        javafx.application.Platform.exit();
    }

    /** 首次启动：Welcome + AI Setup。返回 true 表示配置完成可继续。 */
    private boolean showOnboarding(Stage stage, DataSourceFactory ds, AppConfig config) {
        // Welcome
        Dialog<ButtonType> welcome = new Dialog<>();
        welcome.setTitle("JEVE");
        VBox w = new VBox(20);
        w.setAlignment(Pos.CENTER);
        w.setPadding(new Insets(40));
        Label appName = new Label("JEVE");
        appName.setStyle("-fx-font-size:36px; -fx-font-weight:bold; -fx-text-fill:#7C3AED;");
        Label tag = new Label("AI 恋爱关系助手");
        tag.setStyle("-fx-font-size:14px; -fx-text-fill:#666;");
        Label desc = new Label("帮助你理解关系，辅助表达");
        desc.setStyle("-fx-font-size:12px; -fx-text-fill:#999;");
        w.getChildren().addAll(appName, tag, desc);
        welcome.getDialogPane().setContent(w);
        ButtonType start = new ButtonType("开始使用", ButtonBar.ButtonData.OK_DONE);
        welcome.getDialogPane().getButtonTypes().addAll(start, ButtonType.CANCEL);
        Optional<ButtonType> wr = welcome.showAndWait();
        if (wr.isEmpty() || wr.get() != start) return false;

        // AI Setup
        Dialog<Boolean> setup = new Dialog<>();
        setup.setTitle("配置 AI");
        setup.setHeaderText("JEVE 需要一个 AI Provider 才能生成回复");
        ComboBox<String> provider = new ComboBox<>();
        provider.getItems().add(com.harbor.relationshipassistant.infrastructure.ai.DeepSeekProvider.NAME);
        provider.getSelectionModel().select(com.harbor.relationshipassistant.infrastructure.ai.DeepSeekProvider.NAME);
        provider.setDisable(true);
        PasswordField apiKey = new PasswordField();
        apiKey.setPromptText("sk-...");
        TextField model = new TextField("deepseek-chat");
        TextField baseUrl = new TextField("https://api.deepseek.com");
        Label status = new Label("未测试");
        Button test = new Button("测试连接");
        test.setOnAction(e -> {
            test.setDisable(true);
            status.setText("测试中...");
            try {
                com.harbor.relationshipassistant.infrastructure.security.AesCryptoService crypto =
                        new com.harbor.relationshipassistant.infrastructure.security.AesCryptoService(config.aesKey());
                AiConfigService svc = new AiConfigService(ds, crypto);
                // 先临时保存再测，或直接构造 provider 测
                // 最小做法：临时用输入构造 DeepSeekProvider 测
                com.harbor.relationshipassistant.infrastructure.ai.AIProvider p =
                        new com.harbor.relationshipassistant.infrastructure.ai.DeepSeekProvider(
                                baseUrl.getText(), apiKey.getText(), model.getText());
                var resp = p.generate(com.harbor.relationshipassistant.infrastructure.ai.AIRequest.of(
                        "You are a helpful assistant.",
                        java.util.List.of(new com.harbor.relationshipassistant.infrastructure.ai.AIRequest.Turn("user", "请回复：AI连接测试成功"))));
                status.setText("连接成功：" + (resp.text() == null ? "" : resp.text().trim()));
                status.setStyle("-fx-text-fill:#16a34a;");
            } catch (Exception ex) {
                status.setText("连接失败：" + ex.getMessage());
                status.setStyle("-fx-text-fill:#dc2626;");
                log.error("[JEVE][AI] test connection failed: {}", ex.getMessage());
            } finally {
                test.setDisable(false);
            }
        });
        GridPane g = new GridPane();
        g.setVgap(10); g.setHgap(10); g.setPadding(new Insets(20));
        g.addRow(0, new Label("服务商"), provider);
        g.addRow(1, new Label("API Key"), apiKey);
        g.addRow(2, new Label("Model"), model);
        g.addRow(3, new Label("Base URL"), baseUrl);
        g.addRow(4, new Label(""), test);
        g.addRow(5, new Label("状态"), status);
        setup.getDialogPane().setContent(g);
        ButtonType save = new ButtonType("保存并继续", ButtonBar.ButtonData.OK_DONE);
        setup.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        setup.setResultConverter(bt -> {
            if (bt != save) return false;
            if (apiKey.getText() == null || apiKey.getText().isBlank()) {
                new Alert(Alert.AlertType.ERROR, "请填写 API Key").showAndWait();
                return false;
            }
            try {
                com.harbor.relationshipassistant.infrastructure.security.AesCryptoService crypto =
                        new com.harbor.relationshipassistant.infrastructure.security.AesCryptoService(config.aesKey());
                AiConfigService svc = new AiConfigService(ds, crypto);
                svc.save(provider.getValue(), baseUrl.getText(), model.getText(), apiKey.getText());
                log.info("[JEVE][AI] configuration saved");
                return true;
            } catch (Exception ex) {
                log.error("[JEVE][AI] save failed: {}", ex.getMessage());
                new Alert(Alert.AlertType.ERROR, "保存失败：" + ex.getMessage()).showAndWait();
                return false;
            }
        });
        Optional<Boolean> sr = setup.showAndWait();
        return sr.orElse(false);
    }
}
