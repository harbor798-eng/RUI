package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.application.llm.AnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 对 AnalysisResult 做结构完整性校验。
 * 只做运行时边界检查，不做内容质量/事实判断/二次 LLM 审核。
 */
public final class AnalysisResultValidator {
    private static final Logger log = LoggerFactory.getLogger(AnalysisResultValidator.class);

    public AnalysisResult validate(AnalysisResult result) {
        if (result == null) throw fail("result must not be null");
        if (result.getFunction() == null) throw fail("function must not be null");
        if (isBlank(result.getSkillName())) throw fail("skillName must not be blank");
        if (isBlank(result.getContent())) throw fail("content must not be blank");
        if (isBlank(result.getModel())) throw fail("model must not be blank");
        if (result.getCreatedAt() == null) throw fail("createdAt must not be null");
        log.info("[ANALYSIS] result validation passed skill={}", result.getSkillName());
        return result;
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static AnalysisResultValidationException fail(String field) {
        log.warn("[ANALYSIS] result validation failed field={}", field);
        return new AnalysisResultValidationException("AnalysisResult " + field);
    }
}
