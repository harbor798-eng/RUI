package com.harbor.relationshipassistant.ui;

import com.harbor.relationshipassistant.domain.observation.Observation;
import com.harbor.relationshipassistant.domain.observation.ObservationBatch;
import com.harbor.relationshipassistant.domain.observation.ObservationEvidence;
import com.harbor.relationshipassistant.infrastructure.persistence.ObservationRepository;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Phase 4C：历史长期观察列表 + Evidence + 申请修改 AI 观察。 */
public class ObservationListDialog extends Dialog<Void> {

    private static final Logger log = LoggerFactory.getLogger(ObservationListDialog.class);

    public ObservationListDialog(long relationshipId, ObservationRepository repo) {
        this(relationshipId, repo, null);
    }

    /** 带「新建观察」入口的构造：onNewObservation 非空时在顶部显示按钮。 */
    public ObservationListDialog(long relationshipId, ObservationRepository repo, Runnable onNewObservation) {
        setTitle("AI 观察");
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        VBox root = new VBox(10);
        root.setPadding(new Insets(14));
        root.setPrefWidth(560);

        if (onNewObservation != null) {
            Button newBtn = new Button("＋ 新建观察");
            newBtn.setStyle("-fx-base:#7C3AED; -fx-text-fill:white; -fx-font-size:12px;");
            newBtn.setOnAction(e -> {
                close();
                onNewObservation.run();
            });
            root.getChildren().add(newBtn);
        }

        List<ObservationBatch> batches = repo.listBatches(relationshipId).stream()
                .sorted((a, b) -> Long.compare(b.getId(), a.getId())).toList();
        log.info("[OBSERVATION_LIST] rel={} batches={}", relationshipId, batches.size());
        if (batches.isEmpty()) {
            root.getChildren().add(new Label("暂时还没有完成的 AI 长期观察。"));
        }

        for (ObservationBatch b : batches) {
            TitledPane pane = new TitledPane();
            String subjectLabel = labelTargets(b.getTargets());
            pane.setText("第 " + b.getId() + " 次观察　·　" + subjectLabel
                    + "　·　" + b.getRangeStart() + " ～ " + b.getRangeEnd()
                    + "　·　状态：" + b.getStatus());
            VBox body = new VBox(8);
            List<Observation> obs = repo.listObservations(b.getId());
            for (Observation o : obs) {
                body.getChildren().add(observationBlock(o, repo));
            }
            pane.setContent(body);
            root.getChildren().add(pane);
        }
        ScrollPane sp = new ScrollPane(root);
        getDialogPane().setContent(sp);
        getDialogPane().setPrefSize(620, 700);
    }

    private VBox observationBlock(Observation o, ObservationRepository repo) {
        VBox box = new VBox(4);
        String subjectCn = switch (o.getSubject()) {
            case "SELF" -> "关于我的观察";
            case "OTHER" -> "关于对方的观察";
            default -> "关于这段关系的观察";
        };
        Label head = new Label("【" + subjectCn + "】");
        head.setStyle("-fx-font-weight:bold;");

        log.info("[OBSERVATION_VIEW] obsId={} hasUserEdit={} hasEditReason={} includeInLongterm={} editSubmitted={}",
                o.getId(), o.getUserEditedText() != null, o.getEditReason() != null,
                o.isIncludeInLongterm(), o.getEditSubmittedAt() != null);

        VBox sections = new VBox(3);
        sections.getChildren().add(labeledArea("【AI 原始观察】", o.getAiRawText(), false));
        if (o.getUserEditedText() != null && !o.getUserEditedText().isBlank()) {
            sections.getChildren().add(labeledArea("【用户修改后的观察】", o.getUserEditedText(), true));
            if (o.getEditReason() != null && !o.getEditReason().isBlank()) {
                sections.getChildren().add(labeledArea("【申请理由】", o.getEditReason(), true));
            }
            String lt = o.isIncludeInLongterm() ? "已申请纳入长期观察" : "未纳入长期观察";
            Label ltLabel = new Label("【长期观察】" + lt);
            ltLabel.setStyle("-fx-text-fill:#666;");
            sections.getChildren().add(ltLabel);
        }

        // Evidence 展开
        List<ObservationEvidence> evs = repo.listEvidence(o.getId());
        log.info("[OBSERVATION_EVIDENCE] obsId={} count={}", o.getId(), evs.size());
        VBox evBox = new VBox(2);
        for (ObservationEvidence e : evs) {
            TextArea t = new TextArea("[" + e.getEvidenceType() + "]\n" + e.getEvidenceSnapshot());
            t.setEditable(false);
            t.setWrapText(true);
            t.setPrefRowCount(4);
            evBox.getChildren().add(t);
        }
        TitledPane evPane = new TitledPane("查看依据", evBox);
        evPane.setExpanded(false);

        // 申请修改
        Button apply = new Button("申请修改 AI 观察");
        if (o.getEditSubmittedAt() != null) {
            apply.setDisable(true);
            apply.setText("已提交修改申请");
        }
        apply.setOnAction(ev -> openEditDialog(o, repo, apply));

        box.getChildren().addAll(head, sections, evPane, apply);
        return box;
    }

    private static VBox labeledArea(String title, String content, boolean highlight) {
        Label l = new Label(title);
        l.setStyle(highlight ? "-fx-font-weight:bold; -fx-text-fill:#1a6e3c;" : "-fx-font-weight:bold;");
        TextArea t = new TextArea(content == null ? "" : content);
        t.setEditable(false);
        t.setWrapText(true);
        t.setPrefRowCount(content == null ? 1 : Math.max(2, Math.min(6, content.length() / 40)));
        VBox v = new VBox(2, l, t);
        if (highlight) v.setStyle("-fx-background-color:#f0f7f2; -fx-padding:4;");
        return v;
    }

    private void openEditDialog(Observation o, ObservationRepository repo, Button applyBtn) {
        log.info("[OBSERVATION_EDIT_REQUEST] obsId={}", o.getId());
        Dialog<Boolean> d = new Dialog<>();
        d.setTitle("申请修改 AI 观察");
        d.setHeaderText("AI 观察不是普通文本，不能直接修改。你可以提交修改申请并说明原因。");
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextArea edited = new TextArea(o.getAiRawText());
        edited.setWrapText(true);
        edited.setPrefRowCount(4);
        TextArea reason = new TextArea();
        reason.setWrapText(true);
        reason.setPrefRowCount(3);
        CheckBox include = new CheckBox("允许 AI 将本次申请理由作为未来长期观察的参考");
        include.setSelected(true);
        RadioButton rawVersion = new RadioButton("纳入时使用 AI 原始观察");
        RadioButton editedVersion = new RadioButton("纳入时使用我修改后的观察");
        ToggleGroup vg = new ToggleGroup();
        rawVersion.setToggleGroup(vg); editedVersion.setToggleGroup(vg);
        rawVersion.setSelected(true);
        VBox versionBox = new VBox(4, new Label("纳入长期观察时使用："), rawVersion, editedVersion);
        versionBox.setVisible(false);
        versionBox.setManaged(false);
        Runnable syncVersion = () -> {
            boolean show = include.isSelected() && !edited.getText().isBlank();
            versionBox.setVisible(show);
            versionBox.setManaged(show);
        };
        include.selectedProperty().addListener(ignored -> syncVersion.run());
        edited.textProperty().addListener(ignored -> syncVersion.run());

        VBox v = new VBox(6,
                new Label("AI 原始观察：" + o.getAiRawText()),
                new Label("修改后的观察："), edited,
                new Label("申请理由："), reason, include, versionBox);
        v.setPadding(new Insets(12));
        v.setPrefWidth(480);
        d.getDialogPane().setContent(v);

        d.setResultConverter(bt -> {
            if (bt != ButtonType.OK) return false;
            if (edited.getText().isBlank() || reason.getText().isBlank()) {
                new Alert(Alert.AlertType.WARNING, "修改内容和申请理由都不能为空。").showAndWait();
                return false;
            }
            String version = null;
            if (include.isSelected()) {
                version = editedVersion.isSelected() ? "USER_EDITED" : "AI_RAW";
            }
            boolean ok = repo.submitEdit(o.getId(), edited.getText().trim(), reason.getText().trim(),
                    include.isSelected(), version);
            if (ok) {
                log.info("[OBSERVATION_EDIT_SUCCESS] obsId={} includeLongterm={}", o.getId(), include.isSelected());
                applyBtn.setDisable(true);
                applyBtn.setText("已提交修改申请");
                new Alert(Alert.AlertType.INFORMATION, include.isSelected()
                        ? "已提交，已按你的选择纳入 AI 长期观察素材。"
                        : "已提交。本次申请不会纳入未来 AI 长期观察，仅用于保存你对本次观察的修改意见。").showAndWait();
            } else {
                log.warn("[OBSERVATION_EDIT_REJECTED] obsId={}", o.getId());
                new Alert(Alert.AlertType.WARNING, "这条观察已经提交过修改申请，不能重复提交。").showAndWait();
            }
            return ok;
        });
        d.showAndWait();
    }

    private static String labelTargets(List<String> t) {
        if (t == null) return "";
        return String.join("+", t.stream().map(s -> switch (s) {
            case "SELF" -> "自己";
            case "OTHER" -> "对方";
            default -> "关系";
        }).toList());
    }
}
