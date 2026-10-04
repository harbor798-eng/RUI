package com.harbor.relationshipassistant.ui;

import com.harbor.relationshipassistant.application.ai.BehaviorStatsService;
import com.harbor.relationshipassistant.infrastructure.persistence.DataSourceFactory;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Map;

/** Phase 4：只读行为统计面板（只展示客观计数，无任何 AI 解读）。 */
public class BehaviorStatsDialog extends Dialog<Void> {

    public BehaviorStatsDialog(Stage owner, long relationshipId, DataSourceFactory ds) {
        setTitle("行为统计");
        setHeaderText("与 T高改芸 的 AI 辅助表达统计（按当前关系实时聚合）");
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        BehaviorStatsService.Summary s = new BehaviorStatsService(ds).getSummary(relationshipId);

        VBox box = new VBox(8);
        box.setPadding(new Insets(14));
        box.setPrefWidth(380);

        box.getChildren().add(section("【AI 使用概览】"));
        box.getChildren().add(row("AI 生成次数", s.generationCount()));
        box.getChildren().add(row("最终使用候选次数", s.selectedCount()));
        box.getChildren().add(row("AI 辅助发送次数", s.aiAssistedSent()));
        box.getChildren().add(row("直接输入发送次数", s.directInputSent()));

        box.getChildren().add(section("【候选使用策略】"));
        if (s.strategyBreakdown().isEmpty()) {
            box.getChildren().add(new Label("（暂无候选使用记录）"));
        } else {
            for (Map.Entry<String, Long> e : s.strategyBreakdown().entrySet()) {
                box.getChildren().add(row(e.getKey(), e.getValue()));
            }
        }

        box.getChildren().add(section("【表达修改】"));
        box.getChildren().add(row("未修改", s.unmodifiedCount()));
        box.getChildren().add(row("修改", s.modifiedCount()));
        box.getChildren().add(rowText("修改率", pct(s.modificationRate())));

        box.getChildren().add(section("【发送结果】"));
        box.getChildren().add(row("使用候选后已发送", s.usedAndSent()));
        box.getChildren().add(row("使用候选后未发送", s.usedButNotSent()));
        box.getChildren().add(rowText("最终发送率", pct(s.usedSendRate())));

        getDialogPane().setContent(box);
    }

    private static Label section(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-weight:bold; -fx-padding:6 0 2 0;");
        return l;
    }

    private static Label row(String k, long v) {
        return new Label(k + "：" + v);
    }

    private static Label rowText(String k, String v) {
        return new Label(k + "：" + v);
    }

    private static String pct(double r) {
        return String.format("%.0f%%", r * 100);
    }
}
