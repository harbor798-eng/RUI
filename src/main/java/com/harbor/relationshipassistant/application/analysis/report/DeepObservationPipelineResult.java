package com.harbor.relationshipassistant.application.analysis.report;

import com.harbor.relationshipassistant.application.analysis.AnalysisExecutionResult;
import com.harbor.relationshipassistant.domain.analysis.AnalysisContext;
import com.harbor.relationshipassistant.domain.analysis.knowledge.KnowledgeSelection;

import java.util.List;

/**
 * Deep Observation 专属的管线结果：携带分析层输出与报告层所需上下文。
 * 不复制 Evidence/Statistics/Patterns；它们仍可从 context 取得。
 */
public record DeepObservationPipelineResult(AnalysisExecutionResult executionResult,
                                            AnalysisContext context,
                                            List<KnowledgeSelection> knowledgeSelections) {
}
