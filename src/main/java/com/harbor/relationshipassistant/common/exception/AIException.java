package com.harbor.relationshipassistant.common.exception;

public class AIException extends ApplicationException {
    public AIException(String message, String operation) {
        super("AI_ERROR", message, operation);
    }
    public AIException(String message, String operation, Throwable cause) {
        super("AI_ERROR", message, operation, null, cause);
    }
}
