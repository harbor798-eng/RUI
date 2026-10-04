package com.harbor.relationshipassistant.application.analysis;

/**
 * AnalysisResult 结构校验失败。消息只指出字段名，不包含 content/敏感内容。
 */
public class AnalysisResultValidationException extends RuntimeException {
    public AnalysisResultValidationException(String message) {
        super(message);
    }
}
