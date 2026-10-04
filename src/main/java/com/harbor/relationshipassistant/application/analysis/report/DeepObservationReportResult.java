package com.harbor.relationshipassistant.application.analysis.report;

import com.harbor.relationshipassistant.domain.analysis.report.DeepObservationReport;

/** Deep Observation 报告生成结果。 */
public record DeepObservationReportResult(boolean success,
                                         FailureType failureType,
                                         DeepObservationReport report,
                                         String html,
                                         String failureMessage) {

    public enum FailureType {
        NONE,
        ANALYSIS_FAILED,
        NOT_DEEP_OBSERVATION,
        REPORT_BUILD_FAILED,
        HTML_RENDER_FAILED
    }

    public static DeepObservationReportResult ok(DeepObservationReport report, String html) {
        return new DeepObservationReportResult(true, FailureType.NONE, report, html, null);
    }

    public static DeepObservationReportResult fail(FailureType t, String msg) {
        return new DeepObservationReportResult(false, t, null, null, msg);
    }
}
