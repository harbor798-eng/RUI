package com.harbor.relationshipassistant.domain.analysis.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AnalysisResult 验证结果。存在 ERROR 即 invalid；只有 WARNING 视为 valid。
 */
public final class ValidationResult {

    private final List<ValidationIssue> issues;

    public ValidationResult(List<ValidationIssue> issues) {
        this.issues = (issues == null) ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(issues));
    }

    public static ValidationResult ok() {
        return new ValidationResult(Collections.emptyList());
    }

    public boolean isValid() {
        for (ValidationIssue i : issues) if (i.getSeverity() == ValidationIssue.Severity.ERROR) return false;
        return true;
    }

    public List<ValidationIssue> getIssues() { return issues; }
}
