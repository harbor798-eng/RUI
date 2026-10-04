package com.harbor.relationshipassistant.common.exception;

public class ValidationException extends ApplicationException {
    public ValidationException(String message, String operation) {
        super("VALIDATION_ERROR", message, operation);
    }
}
