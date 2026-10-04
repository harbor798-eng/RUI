package com.harbor.relationshipassistant.ui;

import com.harbor.relationshipassistant.domain.analysis.AnalysisRange;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * AI 深度观察配置页（纯 Java 构建，不修改 FXML）。
 * 仅负责 UI → 选定 AnalysisRange / CUSTOM 日期 → 触发回调。
 */
public class DeepObservationView extends BorderPane {

    private final ToggleGroup rangeGroup = new ToggleGroup();
    private final RadioButton allBtn = new RadioButton("全部聊天记录");
    private final RadioButton yearBtn = new RadioButton("最近一年");
    private final RadioButton threeMoBtn = new RadioButton("最近三个月");
    private final RadioButton customBtn = new RadioButton("自定义");
    private final DatePicker startPicker = new DatePicker();
    private final DatePicker endPicker = new DatePicker();
    private final Button startBtn = new Button("开始深度观察");
    private final Button backBtn = new Button("← 返回");
    private final Label statusLabel = new Label();
    private final Label dataRangeLabel = new Label("当前可分析聊天数据：未知");

    public DeepObservationView(long relationshipId, Consumer<DeepObservationView> onStart) {
        allBtn.setToggleGroup(rangeGroup);
        yearBtn.setToggleGroup(rangeGroup);
        threeMoBtn.setToggleGroup(rangeGroup);
        customBtn.setToggleGroup(rangeGroup);
        allBtn.setSelected(true);

        startPicker.setDisable(true);
        endPicker.setDisable(true);
        startPicker.setValue(LocalDate.now().minusYears(1));
        endPicker.setValue(LocalDate.now());
        customBtn.selectedProperty().addListener((o, a, b) -> {
            startPicker.setDisable(!b);
            endPicker.setDisable(!b);
        });

        VBox content = new VBox(14);
        content.setPadding(new Insets(28));

        Label title = new Label("AI 深度观察");
        title.setFont(Font.font("System", FontWeight.BOLD, 22));
        Label sub = new Label("基于你选择的聊天范围，分析这段关系中的沟通模式、情绪变化、行为变化、重要事件和长期关系变化。");
        sub.setWrapText(true);
        sub.setStyle("-fx-text-fill:#666;");

        VBox rangeBox = new VBox(8, allBtn, yearBtn, threeMoBtn, customBtn,
                new HBox(8, new Label("开始日期:"), startPicker, new Label("至"), endPicker));
        TitledPane rangePane = new TitledPane("分析范围", rangeBox);
        rangePane.setCollapsible(false);

        VBox dataBox = new VBox(6, dataRangeLabel);
        TitledPane dataPane = new TitledPane("当前可分析聊天数据", dataBox);
        dataPane.setCollapsible(false);

        VBox focusBox = new VBox(4,
                new Label("• 沟通模式"), new Label("• 情绪变化"), new Label("• 行为变化"),
                new Label("• 关系变化"), new Label("• 重要事件"), new Label("• 长期行为模式"));
        TitledPane focusPane = new TitledPane("本次分析将重点观察", focusBox);
        focusPane.setCollapsible(false);

        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-text-fill:#444;");

        startBtn.setStyle("-fx-background-color:#6d5bd0; -fx-text-fill:white; -fx-padding:8 22; -fx-background-radius:6;");
        HBox actionRow = new HBox(12, startBtn, statusLabel);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        content.getChildren().addAll(title, sub, rangePane, dataPane, focusPane, actionRow);
        setCenter(content);

        HBox top = new HBox(12, backBtn);
        top.setPadding(new Insets(10));
        setTop(top);

        startBtn.setOnAction(e -> {
            if (customBtn.isSelected()) {
                LocalDate s = startPicker.getValue();
                LocalDate en = endPicker.getValue();
                if (s == null || en == null) {
                    statusLabel.setText("请选择开始和结束日期。");
                    return;
                }
                if (s.isAfter(en)) {
                    statusLabel.setText("开始日期不能晚于结束日期。");
                    return;
                }
            }
            startBtn.setDisable(true);
            statusLabel.setText("正在分析……");
            onStart.accept(this);
        });
    }

    public AnalysisRange getSelectedRange() {
        if (yearBtn.isSelected()) return AnalysisRange.of(AnalysisRange.Kind.RECENT_YEAR);
        if (threeMoBtn.isSelected()) return AnalysisRange.of(AnalysisRange.Kind.RECENT_3_MONTHS);
        if (customBtn.isSelected()) return AnalysisRange.custom(getCustomStart(), getCustomEnd());
        return AnalysisRange.of(AnalysisRange.Kind.ALL);
    }

    public LocalDateTime getCustomStart() {
        return startPicker.getValue() == null ? null : startPicker.getValue().atStartOfDay();
    }

    public LocalDateTime getCustomEnd() {
        return endPicker.getValue() == null ? null : endPicker.getValue().atTime(23, 59, 59);
    }

    public void onAnalysisFinished(boolean success, String message) {
        startBtn.setDisable(false);
        statusLabel.setText(message);
    }

    public void setOnBack(Runnable r) { backBtn.setOnAction(e -> r.run()); }

    public void setDataRange(String text) { dataRangeLabel.setText("当前可分析聊天数据：" + text); }

    public void setStartEnabled(boolean enabled) { startBtn.setDisable(!enabled); }
}
