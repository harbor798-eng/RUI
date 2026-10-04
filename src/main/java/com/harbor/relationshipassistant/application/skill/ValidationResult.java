package com.harbor.relationshipassistant.application.skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ValidationResult {
    private final List<ValidationIssue> issues;

    private ValidationResult(List<ValidationIssue> issues) {
        this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
    }

    public static ValidationResult ok() {
        return new ValidationResult(Collections.emptyList());
    }

    public static ValidationResult error(String message) {
        return new ValidationResult(List.of(new ValidationIssue(ValidationLevel.ERROR, message)));
    }

    public static ValidationResult of(List<ValidationIssue> issues) {
        return new ValidationResult(issues);
    }

    public boolean isValid() {
        return issues.stream().noneMatch(i -> i.level == ValidationLevel.ERROR);
    }

    public List<ValidationIssue> getIssues() { return issues; }

    public String firstErrorMessage() {
        return issues.stream()
                .filter(i -> i.level == ValidationLevel.ERROR)
                .map(i -> i.message)
                .findFirst()
                .orElse(null);
    }
}
