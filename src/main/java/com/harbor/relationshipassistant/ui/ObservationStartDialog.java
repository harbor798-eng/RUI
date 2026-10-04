package com.harbor.relationshipassistant.ui;

import com.harbor.relationshipassistant.application.ai.AiConfigService;
import com.harbor.relationshipassistant.application.ai.BehaviorStatsService;
import com.harbor.relationshipassistant.application.observation.FactStatsComputer;
import com.harbor.relationshipassistant.application.observation.ObservationAnalysisRunner;
import com.harbor.relationshipassistant.application.observation.ObservationService;
import com.harbor.relationshipassistant.application.observation.ObservationTimeEstimator;
import com.harbor.relationshipassistant.infrastructure.ai.AIProvider;
import com.harbor.relationshipassistant.infrastructure.persistence.DataSourceFactory;
import com.harbor.relationshipassistant.infrastructure.persistence.ObservationRepository;
import com.harbor.relationshipassistant.infrastructure.security.AesCryptoService;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Phase 4B：开始一次长期观察 Dialog。 */
public class ObservationStartDialog extends Dialog<Long> {

    private static final Logger log = LoggerFactory.getLogger(ObservationStartDialog.class);

    public ObservationStartDialog(Stage owner, long relationshipId, DataSourceFactory ds,
                                  String aesKey, AIProvider provider) {
        setTitle("开始 AI 长期观察");
        setHeaderText("本次观察基于所选时间范围内的聊天记录和已有资料；分析开始后数据被固定，不受后续修改影响。");
        getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);

        ObservationRepository repo = new ObservationRepository(ds);
        ObservationService svc = new ObservationService(repo, new FactStatsComputer(repo), provider);
        ObservationAnalysisRunner runner = new ObservationAnalysisRunner(repo, svc);
        ObservationTimeEstimator estimator = new ObservationTimeEstimator();

        ToggleGroup group = new ToggleGroup();
        RadioButton rSelf = new RadioButton("只分析自己（SELF）");
        RadioButton rOther = new RadioButton("只分析对方（OTHER）");
        RadioButton rRel = new RadioButton("只分析这段关系（RELATIONSHIP）");
        RadioButton rAll = new RadioButton("双方都分析（SELF + OTHER + RELATIONSHIP）");
        rAll.setSelected(true);
        rSelf.setToggleGroup(group); rOther.setToggleGroup(group); rRel.setToggleGroup(group); rAll.setToggleGroup(group);

        DatePicker start = new DatePicker(LocalDate.now().minusDays(90));
        DatePicker end = new DatePicker(LocalDate.now());
        Label estimate = new Label();
        estimate.setStyle("-fx-text-fill:#555;");

        Runnable refreshEstimate = () -> {
            LocalDate s = start.getValue(), e = end.getValue();
            if (s == null || e == null || s.isAfter(e)) { estimate.setText(""); return; }
            int n = repo.countLiveChat(relationshipId, s.atStartOfDay(), e.atTime(23, 59, 59));
            int targets = group.getSelectedToggle() == rAll ? 3 : 1;
            estimate.setText("当前范围内约 " + n + " 条消息；" + estimator.estimate(n, targets).rangeLabel());
            log.info("[OBS_TIME_ESTIMATE] rel={} rows={} targets={}", relationshipId, n, targets);
        };
        start.valueProperty().addListener(o -> refreshEstimate.run());
        end.valueProperty().addListener(o -> refreshEstimate.run());
        group.selectedToggleProperty().addListener(o -> refreshEstimate.run());

        Button go = new Button("开始长期观察");
        go.setOnAction(ev -> {
            LocalDate s = start.getValue(), e = end.getValue();
            if (s == null || e == null) { alert("请选择开始与结束日期"); return; }
            if (s.isAfter(e)) { alert("开始时间必须早于结束时间"); return; }
            int n = repo.countLiveChat(relationshipId, s.atStartOfDay(), e.atTime(23, 59, 59));
            if (n == 0) { alert("所选时间范围内没有可用于分析的聊天记录，请调整时间范围。"); return; }
            List<String> targets = group.getSelectedToggle() == rSelf ? List.of("SELF")
                    : group.getSelectedToggle() == rOther ? List.of("OTHER")
                    : group.getSelectedToggle() == rRel ? List.of("RELATIONSHIP")
                    : List.of("SELF", "OTHER", "RELATIONSHIP");
            log.info("[OBS_BATCH_CREATE_REQUEST] rel={} targets={} range={}~{} rows={}",
                    relationshipId, targets, s, e, n);
            try {
                var batch = svc.createBatch(relationshipId, targets,
                        s.atStartOfDay(), e.atTime(23, 59, 59),
                        "{\"note\":\"ui created\"}");
                log.info("[OBS_BATCH_CREATED] batchId={}", batch.getId());
                runner.submit(batch.getId());
                log.info("[OBS_RUNNER_SUBMIT] batchId={}", batch.getId());
                setResult(batch.getId());
                close();
            } catch (Exception ex) {
                log.error("[OBS_UI_FAILED] rel={} err={}", relationshipId, ex.getMessage());
                alert("创建观察失败：" + ex.getMessage());
            }
        });

        VBox box = new VBox(10,
                new Label("分析对象："), rSelf, rOther, rRel, rAll,
                new Label("时间范围："), new VBox(4, start, end),
                estimate, go);
        box.setPadding(new Insets(14));
        getDialogPane().setContent(box);
        getDialogPane().setPrefWidth(420);
        log.info("[OBS_UI_OPEN] rel={}", relationshipId);
        refreshEstimate.run();
    }

    private static void alert(String m) {
        new Alert(Alert.AlertType.WARNING, m, ButtonType.OK).showAndWait();
    }
}
