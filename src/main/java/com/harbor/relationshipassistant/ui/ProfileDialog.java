package com.harbor.relationshipassistant.ui;

import com.harbor.relationshipassistant.application.profile.ProfileService;
import com.harbor.relationshipassistant.domain.profile.OwnerType;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Profile 编辑对话框：我的资料 / 对方资料 两个 Tab。
 * 只读/保存均按 relationshipId 隔离；私密字段走 ProfileService.savePrivate（加密存储）。
 */
public class ProfileDialog extends Dialog<Boolean> {

    /** 一个可见字段定义。 */
    record FieldDef(String category, String key, String label) {}

    static final List<FieldDef> ME_FIELDS = List.of(
            new FieldDef("BASIC", "name", "姓名/称呼"),
            new FieldDef("BASIC", "nickname", "昵称"),
            new FieldDef("BASIC", "gender", "性别"),
            new FieldDef("BASIC", "age", "年龄"),
            new FieldDef("BASIC", "birthday", "生日"),
            new FieldDef("BASIC", "job", "职业"),
            new FieldDef("BASIC", "location", "所在地"),
            new FieldDef("PERSONALITY", "personality", "性格描述"),
            new FieldDef("PERSONALITY", "self_review", "自我评价"),
            new FieldDef("INTEREST", "interests", "兴趣"),
            new FieldDef("INTEREST", "hobbies", "爱好"),
            new FieldDef("INTEREST", "likes", "喜欢的事物"),
            new FieldDef("INTEREST", "dislikes", "不喜欢的事物"),
            new FieldDef("COMMUNICATION", "chat_style", "平时聊天风格"),
            new FieldDef("COMMUNICATION", "expression_habit", "表达习惯"),
            new FieldDef("COMMUNICATION", "proactive", "是否主动"),
            new FieldDef("COMMUNICATION", "common_phrases", "常用表达方式"),
            new FieldDef("GOALS", "personal_goal", "当前个人目标"),
            new FieldDef("LOVE_SELF", "love_self_goal", "我希望成为怎样的恋爱对象")
    );

    private static final List<FieldDef> OTHER_FIELDS = List.of(
            new FieldDef("BASIC", "name", "姓名/称呼"),
            new FieldDef("BASIC", "nickname", "当前昵称"),
            new FieldDef("BASIC", "remark", "当前备注"),
            new FieldDef("BASIC", "gender", "性别"),
            new FieldDef("BASIC", "age", "年龄"),
            new FieldDef("BASIC", "birthday", "生日"),
            new FieldDef("BASIC", "job", "职业"),
            new FieldDef("BASIC", "location", "所在地"),
            new FieldDef("PERSONALITY", "personality", "性格描述"),
            new FieldDef("PERSONALITY", "user_note", "用户备注"),
            new FieldDef("INTEREST", "interests", "兴趣"),
            new FieldDef("INTEREST", "hobbies", "爱好"),
            new FieldDef("INTEREST", "likes", "喜欢的事物"),
            new FieldDef("INTEREST", "dislikes", "不喜欢的事物"),
            new FieldDef("SENSITIVE", "sensitive_topics", "敏感话题/禁忌"),
            new FieldDef("IMPORTANT", "important_notes", "重要信息")
    );

    private static final List<FieldDef> PRIVATE_FIELDS = List.of(
            new FieldDef("PRIVATE", "PHONE", "手机号"),
            new FieldDef("PRIVATE", "ID_CARD", "身份证"),
            new FieldDef("PRIVATE", "HOME_ADDRESS", "家庭住址"),
            new FieldDef("PRIVATE", "WORK_ADDRESS", "工作地址"),
            new FieldDef("PRIVATE", "SHIPPING", "收货地址"),
            new FieldDef("PRIVATE", "PRIVATE_NOTE", "其他私人备注")
    );

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final long relationshipId;
    private final ProfileService service;
    private final Map<String, TextField> meFields = new LinkedHashMap<>();
    private final Map<String, Label> meStatusLabels = new LinkedHashMap<>();
    private final Map<String, TextField> otherFields = new LinkedHashMap<>();
    private final Map<String, TextField> mePrivate = new LinkedHashMap<>();
    private final Map<String, TextField> otherPrivate = new LinkedHashMap<>();
    private final ObservableList<ProfileService.NicknameRow> nicknames = FXCollections.observableArrayList();

    public ProfileDialog(Window owner, long relationshipId, ProfileService service) {
        this.relationshipId = relationshipId;
        this.service = service;
        initOwner(owner);
        setTitle("档案资料（关系 ID=" + relationshipId + "）");
        setHeaderText("我的资料 / 对方资料 —— 仅当前关系隔离保存");

        TabPane tabs = new TabPane();
        tabs.getTabs().add(buildMeTab());
        tabs.getTabs().add(buildOtherTab());
        getDialogPane().setContent(tabs);
        getDialogPane().setPrefSize(720, 720);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        loadAll();
    }

    // ---------------- Tab: 我的资料 ----------------

    /** 我的资料分节顺序：category -> UI 分组标题。 */
    static final List<java.util.Map.Entry<String, String>> ME_SECTIONS = List.of(
            Map.entry("BASIC", "基本信息"),
            Map.entry("PERSONALITY", "性格"),
            Map.entry("INTEREST", "兴趣与偏好"),
            Map.entry("COMMUNICATION", "表达风格"),
            Map.entry("GOALS", "个人目标"),
            Map.entry("LOVE_SELF", "恋爱中的自我"));

    /** 对方资料分节顺序。 */
    private static final List<java.util.Map.Entry<String, String>> OTHER_SECTIONS = List.of(
            Map.entry("BASIC", "基本信息（含当前昵称 / 备注）"),
            Map.entry("PERSONALITY", "性格"),
            Map.entry("INTEREST", "兴趣与偏好"),
            Map.entry("SENSITIVE", "敏感话题 / 禁忌"),
            Map.entry("IMPORTANT", "重要信息"));

    private void addSections(VBox box, List<java.util.Map.Entry<String, String>> sections,
                                    List<FieldDef> defs, Map<String, TextField> target) {
        addSections(box, sections, defs, target, null);
    }

    private void addSections(VBox box, List<java.util.Map.Entry<String, String>> sections,
                                    List<FieldDef> defs, Map<String, TextField> target, Map<String, Label> statusTarget) {
        for (Map.Entry<String, String> e : sections) {
            List<FieldDef> inCat = defs.stream().filter(f -> f.category().equals(e.getKey())).toList();
            if (!inCat.isEmpty()) box.getChildren().add(section(e.getValue(), inCat, target, statusTarget));
        }
    }

    private Tab buildMeTab() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        addSections(box, ME_SECTIONS, ME_FIELDS, meFields, meStatusLabels);
        box.getChildren().add(section("私密信息（加密存储，默认不进 AI）", PRIVATE_FIELDS, mePrivate));
        Button save = new Button("保存我的资料");
        save.setOnAction(e -> save(OwnerType.ME, meFields, mePrivate, box));
        box.getChildren().add(save);
        Tab t = new Tab("我的资料", new ScrollPane(box));
        t.setClosable(false);
        return t;
    }

    private Tab buildOtherTab() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        addSections(box, OTHER_SECTIONS, OTHER_FIELDS, otherFields);
        box.getChildren().add(section("私密信息（加密存储，默认不进 AI）", PRIVATE_FIELDS, otherPrivate));

        // 历史昵称
        Label h = new Label("历史昵称（不覆盖当前昵称）");
        TableView<ProfileService.NicknameRow> table = new TableView<>(nicknames);
        TableColumn<ProfileService.NicknameRow, String> nCol = new TableColumn<>("昵称");
        nCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().nickname()));
        TableColumn<ProfileService.NicknameRow, String> sCol = new TableColumn<>("开始");
        sCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().startedAt() == null ? "" : d.getValue().startedAt().format(MONTH)));
        TableColumn<ProfileService.NicknameRow, String> eCol = new TableColumn<>("结束");
        eCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().endedAt() == null ? "至今" : d.getValue().endedAt().format(MONTH)));
        table.getColumns().addAll(nCol, sCol, eCol);
        table.setPrefHeight(140);

        TextField nName = new TextField(); nName.setPromptText("昵称");
        TextField nStart = new TextField(); nStart.setPromptText("yyyy-MM");
        TextField nEnd = new TextField(); nEnd.setPromptText("yyyy-MM（留空=至今）");
        Button add = new Button("添加历史昵称");
        add.setOnAction(e -> {
            try {
                YearMonth st = YearMonth.parse(nStart.getText().trim(), MONTH);
                YearMonth en = nEnd.getText().isBlank() ? null : YearMonth.parse(nEnd.getText().trim(), MONTH);
                service.addNickname(relationshipId, nName.getText().trim(),
                        st.atDay(1).atStartOfDay(), en == null ? null : en.atDay(1).atStartOfDay());
                nName.clear(); nStart.clear(); nEnd.clear();
                refreshNicknames();
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "历史昵称添加失败：" + ex.getMessage()).showAndWait();
            }
        });
        box.getChildren().addAll(h, table, new HBox(8, nName, nStart, nEnd, add));

        Button save = new Button("保存对方资料");
        save.setOnAction(e -> save(OwnerType.OTHER, otherFields, otherPrivate, box));
        box.getChildren().add(save);
        Tab t = new Tab("对方资料", new ScrollPane(box));
        t.setClosable(false);
        return t;
    }

    private TitledPane section(String title, List<FieldDef> defs, Map<String, TextField> target) {
        return section(title, defs, target, null);
    }

    private TitledPane section(String title, List<FieldDef> defs, Map<String, TextField> target, Map<String, Label> statusTarget) {
        GridPane g = new GridPane();
        g.setVgap(6); g.setHgap(10);
        int row = 0;
        for (FieldDef f : defs) {
            Label l = new Label(f.label());
            TextField tf = new TextField();
            tf.setPrefWidth(360);
            target.put(f.key(), tf);
            g.add(l, 0, row);
            g.add(tf, 1, row);
            if (statusTarget != null) {
                Label st = new Label();
                st.setStyle("-fx-font-size:10px; -fx-text-fill:#888;");
                statusTarget.put(f.key(), st);
                g.add(st, 2, row);
            }
            row++;
        }
        TitledPane p = new TitledPane(title, g);
        p.setExpanded(true);
        return p;
    }

    // ---------------- 加载 / 保存 ----------------

    private void loadAll() {
        try {
            Map<String, String> globalMe = service.loadGlobalMeItems();
            Map<String, String> relationMe = service.loadRelationMeItems(relationshipId);
            // 显示值 = 关系优先，否则全局
            for (FieldDef f : ME_FIELDS) {
                String k = f.key();
                String relVal = relationMe.get(k);
                String globVal = globalMe.get(k);
                String display = relVal != null ? relVal : (globVal != null ? globVal : "");
                meFields.get(k).setText(display == null ? "" : display);
                Label st = meStatusLabels.get(k);
                if (st != null) {
                    if (relVal != null && globVal != null && !relVal.equals(globVal)) {
                        st.setText("关系专属 · 已覆盖全局");
                        st.setStyle("-fx-font-size:10px; -fx-text-fill:#7C3AED;");
                    } else if (relVal != null) {
                        st.setText("关系专属");
                        st.setStyle("-fx-font-size:10px; -fx-text-fill:#666;");
                    } else if (globVal != null) {
                        st.setText("使用全局默认");
                        st.setStyle("-fx-font-size:10px; -fx-text-fill:#888;");
                    } else {
                        st.setText("");
                    }
                }
            }
            Map<String, String> other = service.loadItems(relationshipId, OwnerType.OTHER);
            otherFields.forEach((k, v) -> v.setText(other.getOrDefault(k, "")));
            Map<String, String> mePrv = service.loadPrivate(relationshipId, OwnerType.ME);
            mePrivate.forEach((k, v) -> v.setText(mePrv.getOrDefault(k, "")));
            Map<String, String> otherPrv = service.loadPrivate(relationshipId, OwnerType.OTHER);
            otherPrivate.forEach((k, v) -> v.setText(otherPrv.getOrDefault(k, "")));
            refreshNicknames();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "档案加载失败：" + e.getMessage()).showAndWait();
        }
    }

    private void refreshNicknames() {
        nicknames.setAll(service.loadNicknames(relationshipId));
    }

    private void save(OwnerType owner, Map<String, TextField> fields, Map<String, TextField> privateFields, VBox box) {
        try {
            for (FieldDef f : owner == OwnerType.ME ? ME_FIELDS : OTHER_FIELDS) {
                TextField tf = fields.get(f.key());
                service.saveItem(relationshipId, owner, f.category(), f.key(), tf == null ? "" : tf.getText());
            }
            for (FieldDef f : PRIVATE_FIELDS) {
                TextField tf = privateFields.get(f.key());
                service.savePrivate(relationshipId, owner, f.key(), tf == null ? "" : tf.getText());
            }
            new Alert(Alert.AlertType.INFORMATION, "保存成功（relationshipId=" + relationshipId + "）").showAndWait();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "保存失败：" + e.getMessage()).showAndWait();
        }
    }
}
