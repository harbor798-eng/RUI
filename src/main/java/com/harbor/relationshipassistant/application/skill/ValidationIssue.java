package com.harbor.relationshipassistant.application.skill;

public final class ValidationIssue {
    public final ValidationLevel level;
    public final String message;

    public ValidationIssue(ValidationLevel level, String message) {
        this.level = level;
        this.message = message;
    }
}
