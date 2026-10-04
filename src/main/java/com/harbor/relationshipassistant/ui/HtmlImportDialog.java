package com.harbor.relationshipassistant.ui;

import com.harbor.relationshipassistant.application.importjob.ImportService;
import com.harbor.relationshipassistant.domain.chat.MessageType;
import com.harbor.relationshipassistant.domain.chat.SenderType;
import com.harbor.relationshipassistant.infrastructure.importer.HtmlChatImporter;
import com.harbor.relationshipassistant.infrastructure.importer.ImportPreview;
import com.harbor.relationshipassistant.infrastructure.importer.ImportRequest;
import com.harbor.relationshipassistant.infrastructure.importer.ImportedRawMessage;
import com.harbor.relationshipassistant.infrastructure.persistence.AuditLogRepository;
import com.harbor.relationshipassistant.infrastructure.persistence.ChatMessageRepository;
import com.harbor.relationshipassistant.infrastructure.persistence.DataSourceFactory;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * HTML 聊天导入 MVP 对话框：选择文件 → 解析 → 身份确认 → 预览/修改 → 确认导入。
 * 原始 HTML 只读；写库统一走 ImportService.confirm（事务 + 幂等）。
 */
public class HtmlImportDialog extends Dialog<ImportPreview> {

    private static final Logger log = LoggerFactory.getLogger(HtmlImportDialog.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final long relationshipId;
    private final ImportService importService;
    private final Window owner;

    private final TextField selfNick = new TextField("我");
    private final Label fileLabel = new Label("未选择文件");
    private final Label statsLabel = new Label();
    private final TableView<ImportedRawMessage> table = new TableView<>();
    private final ObservableList<ImportedRawMessage> rows = FXCollections.observableArrayList();
    private File chosen;
    private ImportPreview current;

    public HtmlImportDialog(Window owner, long relationshipId, DataSourceFactory ds) {
        this.owner = owner;
        this.relationshipId = relationshipId;
        ChatMessageRepository repo = new ChatMessageRepository(ds);
        this.importService = new ImportService(ds, repo, new AuditLogRepository(ds),
                List.of(new HtmlChatImporter()));

        setTitle("导入 HTML 聊天记录");
        setHeaderText("关系 ID=" + relationshipId + "：选择 HTML 文件 → 解析 → 核对身份 → 修改 → 确认导入");

        ButtonType confirm = new ButtonType("确认导入", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(confirm, ButtonType.CANCEL);

        // --- 文件选择 ---
        Button chooseFile = new Button("选择 HTML 文件…");
        chooseFile.setOnAction(e -> chooseFile());
        fileLabel.setStyle("-fx-text-fill:#555;");
        HBox fileRow = new HBox(10, chooseFile, fileLabel);

        // --- 身份确认 ---
        selfNick.setPromptText("导出文件里“我”的昵称，用于判定 ME/OTHER");
        HBox identityRow = new HBox(10, new Label("我的昵称："), selfNick);

        Button reparse = new Button("重新解析（按上面的昵称）");
        reparse.setOnAction(e -> parseCurrent());

        statsLabel.setWrapText(true);
        statsLabel.setStyle("-fx-text-fill:#333;");
        statsLabel.setMaxWidth(760);

        // --- 预览表 ---
        table.setItems(rows);
        table.setPrefHeight(320);
        TableColumn<ImportedRawMessage, String> timeCol = new TableColumn<>("时间");
        timeCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getMessageTime() == null ? "" : DT_FMT.format(d.getValue().getMessageTime())));
        timeCol.setPrefWidth(150);
        TableColumn<ImportedRawMessage, String> senderCol = new TableColumn<>("发送者");
        senderCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getSenderType() == null ? "" : d.getValue().getSenderType().name()));
        senderCol.setPrefWidth(80);
        TableColumn<ImportedRawMessage, String> typeCol = new TableColumn<>("类型");
        typeCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getMessageType() == null ? "" : d.getValue().getMessageType().name()));
        typeCol.setPrefWidth(80);
        TableColumn<ImportedRawMessage, String> contentCol = new TableColumn<>("内容");
        contentCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getContent()));
        contentCol.setPrefWidth(420);
        table.getColumns().addAll(timeCol, senderCol, typeCol, contentCol);

        Button editRow = new Button("修改选中行");
        editRow.setOnAction(e -> editSelected());
        Button delRow = new Button("删除选中行");
        delRow.setOnAction(e -> {
            ImportedRawMessage sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) { rows.remove(sel); refreshStats(); }
        });
        Button addRow = new Button("新增空行");
        addRow.setOnAction(e -> {
            ImportedRawMessage m = new ImportedRawMessage();
            m.setMessageTime(LocalDateTime.now());
            m.setSenderType(SenderType.OTHER);
            m.setMessageType(MessageType.TEXT);
            m.setContent("");
            // sourceMessageId 留空：确认时按内容哈希兜底（用户手动补的行）
            rows.add(m);
        });
        HBox rowOps = new HBox(8, editRow, delRow, addRow);

        VBox box = new VBox(8, fileRow, identityRow, reparse, statsLabel, rowOps, table);
        box.setPadding(new Insets(12));
        VBox.setVgrow(table, Priority.ALWAYS);
        getDialogPane().setContent(box);
        getDialogPane().setPrefWidth(820);

        // 确认导入
        Button okBtn = (Button) getDialogPane().lookupButton(confirm);
        okBtn.setOnAction(e -> {
            if (current == null || rows.isEmpty()) {
                Alert a = new Alert(Alert.AlertType.WARNING, "请先选择文件并解析出消息", ButtonType.OK);
                a.showAndWait();
                return;
            }
            current.getMessages().clear();
            current.getMessages().addAll(rows);
            try {
                int inserted = importService.confirm(relationshipId, current);
                long dup = current.getTotalParsed() - inserted;
                Alert done = new Alert(Alert.AlertType.INFORMATION,
                        "导入完成：inserted=" + inserted + " duplicates=" + dup, ButtonType.OK);
                done.showAndWait();
                log.info("[HTML_CONFIRM] UI 完成 rel={} inserted={}", relationshipId, inserted);
            } catch (Exception ex) {
                log.error("[HTML_ERROR] UI 导入失败: {}", ex.getMessage(), ex);
                Alert err = new Alert(Alert.AlertType.ERROR, "导入失败：" + ex.getMessage(), ButtonType.OK);
                err.showAndWait();
            }
        });
    }

    private void chooseFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("选择导出的 HTML 聊天记录");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML", "*.html", "*.htm"));
        chosen = fc.showOpenDialog(owner);
        if (chosen != null) {
            fileLabel.setText(chosen.getAbsolutePath());
            parseCurrent();
        }
    }

    private void parseCurrent() {
        if (chosen == null) {
            statsLabel.setText("请先选择 HTML 文件");
            return;
        }
        try {
            ImportRequest req = new ImportRequest();
            req.setImporterType("HTML");
            req.setSourceLocation(chosen.getAbsolutePath());
            req.setSelfNickname(selfNick.getText().trim());
            current = new HtmlChatImporter().parse(req);
            rows.setAll(current.getMessages());
            refreshStats();
        } catch (Exception ex) {
            log.error("[HTML_ERROR] 解析失败: {}", ex.getMessage(), ex);
            statsLabel.setText("解析失败：" + ex.getMessage());
        }
    }

    private void refreshStats() {
        if (current == null) return;
        current.getMessages().clear();
        current.getMessages().addAll(rows);
        StringBuilder sb = new StringBuilder();
        sb.append("总数=").append(current.getTotalParsed())
          .append("  ME=").append(current.getMeCount())
          .append("  OTHER=").append(current.getOtherCount())
          .append("  SYSTEM=").append(current.getSystemCount());
        sb.append("  时间范围=").append(current.getEarliest() == null ? "-" : current.getEarliest().toString())
          .append(" ~ ").append(current.getLatest() == null ? "-" : current.getLatest().toString());
        if (!current.getParseErrors().isEmpty()) {
            sb.append("  解析告警=").append(current.getParseErrors().size())
              .append("（").append(current.getParseErrors().get(0)).append("）");
        }
        statsLabel.setText(sb.toString());
    }

    private void editSelected() {
        ImportedRawMessage m = table.getSelectionModel().getSelectedItem();
        if (m == null) return;
        Dialog<Object[]> d = new Dialog<>();
        d.setTitle("修改预览消息");
        ButtonType ok = new ButtonType("确定", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);

        DatePicker dp = new DatePicker(m.getMessageTime() == null ? LocalDate.now() : m.getMessageTime().toLocalDate());
        TextField tf = new TextField(m.getMessageTime() == null ? "12:00" : m.getMessageTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        ChoiceBox<SenderType> senderBox = new ChoiceBox<>(FXCollections.observableArrayList(SenderType.values()));
        senderBox.setValue(m.getSenderType());
        ChoiceBox<MessageType> typeBox = new ChoiceBox<>(FXCollections.observableArrayList(MessageType.values()));
        typeBox.setValue(m.getMessageType());
        TextArea content = new TextArea(m.getContent());
        content.setPrefRowCount(3);

        VBox v = new VBox(8,
                new HBox(8, new Label("时间:"), dp, tf),
                new HBox(8, new Label("发送者:"), senderBox, new Label("类型:"), typeBox),
                new Label("内容（原始值保留在 source_content，此处只改当前显示值）:"), content);
        v.setPadding(new Insets(12));
        d.getDialogPane().setContent(v);
        d.setResultConverter(bt -> bt.getButtonData() == ButtonBar.ButtonData.OK_DONE
                ? new Object[]{dp.getValue(), tf.getText(), senderBox.getValue(), typeBox.getValue(), content.getText()}
                : null);
        d.showAndWait().ifPresent(r -> {
            try {
                m.setMessageTime(LocalDateTime.of((LocalDate) r[0],
                        LocalTime.parse(((String) r[1]).trim(), DateTimeFormatter.ofPattern("HH:mm"))));
            } catch (Exception ignored) { /* 时间非法保持原值 */ }
            m.setSenderType((SenderType) r[2]);
            m.setMessageType((MessageType) r[3]);
            m.setContent((String) r[4]);
            refreshStats();
            table.refresh();
        });
    }
}
