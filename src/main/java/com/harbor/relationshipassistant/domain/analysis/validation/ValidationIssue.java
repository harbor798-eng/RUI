package com.harbor.relationshipassistant.domain.analysis.validation;

/**
 * 单条验证问题。
 */
public final class ValidationIssue {

    public enum Severity { ERROR, WARNING }

    private final Severity severity;
    private final String code;
    private final String message;
    private final String path;

    public ValidationIssue(Severity severity, String code, String message, String path) {
        this.severity = severity;
        this.code = code;
        this.message = message;
        this.path = path;
    }

    public static ValidationIssue error(String code, String message, String path) {
        return new ValidationIssue(Severity.ERROR, code, message, path);
    }

    public static ValidationIssue warning(String code, String message, String path) {
        return new ValidationIssue(Severity.WARNING, code, message, path);
    }

    public Severity getSeverity() { return severity; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
}
