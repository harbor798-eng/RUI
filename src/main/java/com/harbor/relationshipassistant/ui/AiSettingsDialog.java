package com.harbor.relationshipassistant.ui;

import com.harbor.relationshipassistant.application.ai.AiConfigService;
import com.harbor.relationshipassistant.application.profile.ProfileService;
import com.harbor.relationshipassistant.infrastructure.ai.DeepSeekProvider;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

/** 设置对话框：AI Provider + 我的默认资料。 */
public class AiSettingsDialog extends Dialog<Boolean> {

    public AiSettingsDialog(Window owner, AiConfigService aiService, ProfileService profileService) {
        initOwner(owner);
        setTitle("设置");
        setHeaderText("AI 配置与全局默认资料。");

        TabPane tabs = new TabPane();

        // ---- AI Tab ----
        ComboBox<String> provider = new ComboBox<>();
        provider.getItems().add(DeepSeekProvider.NAME);
        provider.getSelectionModel().select(DeepSeekProvider.NAME);
        provider.setDisable(true);

        PasswordField apiKey = new PasswordField();
        apiKey.setPromptText("sk-...（留空 = 不修改已保存的 Key）");
        TextField model = new TextField(DeepSeekProvider.DEFAULT_MODEL);
        TextField baseUrl = new TextField(DeepSeekProvider.DEFAULT_BASE_URL);
        Label keyStatus = new Label("尚未配置");

        Optional<AiConfigService.ProviderView> view = aiService.loadView();
        view.ifPresent(v -> {
            baseUrl.setText(v.baseUrl());
            model.setText(v.model());
            keyStatus.setText(v.hasKey() ? "已配置：" + v.maskedKey() : "尚未配置");
        });

        GridPane g = new GridPane();
        g.setVgap(8); g.setHgap(10);
        g.addRow(0, new Label("Provider"), provider);
        g.addRow(1, new Label("API Key"), apiKey);
        g.addRow(2, new Label(""), keyStatus);
        g.addRow(3, new Label("Model"), model);
        g.addRow(4, new Label("Base URL"), baseUrl);
        g.setPadding(new Insets(12));

        Button save = new Button("保存");
        save.setOnAction(e -> {
            try {
                aiService.save(provider.getValue(), baseUrl.getText(), model.getText(), apiKey.getText());
                keyStatus.setText("已保存：" + aiService.loadView().map(AiConfigService.ProviderView::maskedKey).orElse(""));
                new Alert(Alert.AlertType.INFORMATION, "保存成功").showAndWait();
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "保存失败：" + ex.getMessage()).showAndWait();
            }
        });

        Button test = new Button("测试连接");
        test.setOnAction(e -> {
            test.setDisable(true);
            try {
                String result = aiService.testConnection();
                new Alert(Alert.AlertType.INFORMATION, "DeepSeek 连接成功\nAI 回复：" + result).showAndWait();
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "DeepSeek 连接失败：" + ex.getMessage()).showAndWait();
            } finally {
                test.setDisable(false);
            }
        });

        HBox actions = new HBox(10, save, test);
        actions.setPadding(new Insets(0, 12, 12, 12));
        VBox aiTabContent = new VBox(10, g, actions);
        Tab aiTab = new Tab("AI", aiTabContent);
        aiTab.setClosable(false);

        // ---- 我的默认资料 Tab ----
        Tab profileTab = new Tab("我的默认资料", new Button("打开全局默认资料编辑") {{
            setOnAction(e -> {
                GlobalProfileDialog d = new GlobalProfileDialog(owner, profileService);
                d.showAndWait();
            });
        }});
        profileTab.setClosable(false);
        // 直接内嵌 GlobalProfileDialog 的内容，避免嵌套 Dialog
        GlobalProfileDialog inner = new GlobalProfileDialog(owner, profileService);
        profileTab.setContent(inner.getDialogPane().getContent());

        tabs.getTabs().addAll(aiTab, profileTab);
        getDialogPane().setContent(tabs);
        getDialogPane().setPrefSize(640, 640);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    }
}
