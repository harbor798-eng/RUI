package com.harbor.relationshipassistant.application.analysis.report;

import com.harbor.relationshipassistant.application.analysis.AnalysisExecutionResult;
import com.harbor.relationshipassistant.domain.analysis.AnalysisContext;
import com.harbor.relationshipassistant.domain.analysis.AnalysisTaskType;
import com.harbor.relationshipassistant.domain.analysis.deep.DeepAnalysisResult;
import com.harbor.relationshipassistant.domain.analysis.knowledge.KnowledgeSelection;
import com.harbor.relationshipassistant.domain.analysis.report.DeepObservationReport;

import java.util.List;

/**
 * Deep Observation 报告生成服务：DeepAnalysisResult + Context + KnowledgeSelection
 * → DeepObservationReport → HTML。
 * <p>
 * 不调用 LLM、不访问 DB、不写文件。失败类型与分析层分离。
 * </p>
 */
public class DeepObservationReportService {

    private final DeepObservationReportBuilder builder = new DeepObservationReportBuilder();
    private final DeepObservationHtmlRenderer renderer = new DeepObservationHtmlRenderer();

    public DeepObservationReportResult generate(DeepObservationPipelineResult pipeline) {
        if (pipeline == null) return DeepObservationReportResult.fail(
                DeepObservationReportResult.FailureType.ANALYSIS_FAILED, "pipeline is null");

        AnalysisExecutionResult exec = pipeline.executionResult();
        AnalysisContext ctx = pipeline.context();
        List<KnowledgeSelection> selected = pipeline.knowledgeSelections();

        System.out.println("[DeepObservationReportService] Starting report generation. relationshipId="
                + ctx.getTask().getRelationshipId());

        if (exec == null || !exec.success()) {
            System.out.println("[DeepObservationReportService][ERROR] Analysis result unavailable");
            return DeepObservationReportResult.fail(
                    DeepObservationReportResult.FailureType.ANALYSIS_FAILED,
                    exec == null ? "null executionResult" : exec.failureType());
        }
        if (ctx.getTask().getTaskType() != AnalysisTaskType.DEEP_OBSERVATION) {
            System.out.println("[DeepObservationReportService][ERROR] Not DEEP_OBSERVATION");
            return DeepObservationReportResult.fail(
                    DeepObservationReportResult.FailureType.NOT_DEEP_OBSERVATION,
                    "taskType=" + ctx.getTask().getTaskType());
        }
        if (!(exec.output() instanceof DeepAnalysisResult deepResult)) {
            System.out.println("[DeepObservationReportService][ERROR] output is not DeepAnalysisResult: "
                    + (exec.output() == null ? "null" : exec.output().getClass().getName()));
            return DeepObservationReportResult.fail(
                    DeepObservationReportResult.FailureType.ANALYSIS_FAILED, "output is not DeepAnalysisResult");
        }

        System.out.println("[DeepObservationReportService] Building structured report");
        DeepObservationReport report;
        try {
            report = builder.build(deepResult, ctx, selected == null ? List.of() : selected);
        } catch (Exception e) {
            System.out.println("[DeepObservationReportService][ERROR] Report build failed: " + e.getMessage());
            return DeepObservationReportResult.fail(
                    DeepObservationReportResult.FailureType.REPORT_BUILD_FAILED, e.getMessage());
        }
        System.out.println("[DeepObservationReportService] Structured report built. sections="
                + report.getSections().size());

        System.out.println("[DeepObservationReportService] Rendering HTML");
        String html;
        try {
            html = renderer.render(report, ctx.getEvidenceWindows());
        } catch (Exception e) {
            System.out.println("[DeepObservationReportService][ERROR] HTML render failed: " + e.getMessage());
            return DeepObservationReportResult.fail(
                    DeepObservationReportResult.FailureType.HTML_RENDER_FAILED, e.getMessage());
        }
        System.out.println("[DeepObservationReportService] Report generation completed. htmlLength=" + html.length());
        return DeepObservationReportResult.ok(report, html);
    }
}
