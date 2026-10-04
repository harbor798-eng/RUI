package com.harbor.relationshipassistant.ui;

import com.harbor.relationshipassistant.application.profile.ProfileService;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局默认「我的资料」编辑对话框。
 * 写入 profile.relationship_id IS NULL + owner_type=ME。
 * 不含 PRIVATE，不含 OTHER。
 */
public class GlobalProfileDialog extends Dialog<Boolean> {

    private final ProfileService service;
    private final Map<String, TextField> fields = new LinkedHashMap<>();

    public GlobalProfileDialog(Window owner, ProfileService service) {
        this.service = service;
        initOwner(owner);
        setTitle("我的默认资料（全局）");
        setHeaderText("编辑所有关系共享的「我的资料」默认值。留空即删除该全局条目。");

        VBox box = new VBox(8);
        box.setPadding(new Insets(12));

        for (Map.Entry<String, String> e : ProfileDialog.ME_SECTIONS) {
            var inCat = ProfileDialog.ME_FIELDS.stream().filter(f -> f.category().equals(e.getKey())).toList();
            if (inCat.isEmpty()) continue;
            GridPane g = new GridPane();
            g.setVgap(6); g.setHgap(10);
            int row = 0;
            for (ProfileDialog.FieldDef f : inCat) {
                g.add(new Label(f.label()), 0, row);
                TextField tf = new TextField();
                tf.setPrefWidth(420);
                fields.put(f.key(), tf);
                g.add(tf, 1, row);
                row++;
            }
            TitledPane tp = new TitledPane(e.getValue(), g);
            tp.setExpanded(true);
            box.getChildren().add(tp);
        }

        Button save = new Button("保存全局默认资料");
        save.setOnAction(e -> saveAll());
        box.getChildren().add(save);

        getDialogPane().setContent(new ScrollPane(box));
        getDialogPane().setPrefSize(640, 720);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        loadAll();
    }

    private void loadAll() {
        try {
            Map<String, String> global = service.loadGlobalMeItems();
            fields.forEach((k, v) -> v.setText(global.getOrDefault(k, "")));
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "全局资料加载失败：" + ex.getMessage()).showAndWait();
        }
    }

    private void saveAll() {
        try {
            for (ProfileDialog.FieldDef f : ProfileDialog.ME_FIELDS) {
                TextField tf = fields.get(f.key());
                service.saveGlobalMeItem(f.category(), f.key(), tf == null ? "" : tf.getText());
            }
            new Alert(Alert.AlertType.INFORMATION, "全局默认资料已保存。").showAndWait();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "保存失败：" + ex.getMessage()).showAndWait();
        }
    }
}
