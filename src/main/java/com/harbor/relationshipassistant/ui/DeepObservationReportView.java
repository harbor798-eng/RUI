package com.harbor.relationshipassistant.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Deep Observation 报告展示页：WebView loadContent(html) + 保存按钮。 */
public class DeepObservationReportView extends BorderPane {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public DeepObservationReportView(String html, Runnable onBack) {
        HBox top = new HBox(12);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(8));
        Button back = new Button("← 返回");
        back.setOnAction(e -> onBack.run());
        Label title = new Label("AI深度观察报告");
        title.setStyle("-fx-font-weight:bold;");
        Button save = new Button("保存报告");
        HBox.setHgrow(title, javafx.scene.layout.Priority.ALWAYS);
        save.setOnAction(e -> {
            try {
                FileChooser fc = new FileChooser();
                fc.setInitialFileName("JEVE-深度观察报告-" + LocalDateTime.now().format(FMT) + ".html");
                fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML", "*.html"));
                File f = fc.showSaveDialog(getScene().getWindow());
                if (f != null) {
                    Files.writeString(f.toPath(), html, StandardCharsets.UTF_8);
                    save.setText("已保存");
                }
            } catch (Exception ex) {
                save.setText("保存失败");
                ex.printStackTrace();
            }
        });
        top.getChildren().addAll(back, title, save);
        setTop(top);

        WebView web = new WebView();
        web.getEngine().loadContent(html);
        setCenter(web);
    }
}
