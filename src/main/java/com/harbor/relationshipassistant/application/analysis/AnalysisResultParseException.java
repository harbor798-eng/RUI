package com.harbor.relationshipassistant.application.analysis;

/** AI 输出 JSON 解析失败的明确业务异常。 */
public class AnalysisResultParseException extends RuntimeException {
    public AnalysisResultParseException(String message) { super(message); }
    public AnalysisResultParseException(String message, Throwable cause) { super(message, cause); }
}
