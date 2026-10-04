package com.harbor.relationshipassistant.common.exception;

public class SecurityException extends ApplicationException {
    public SecurityException(String message, String operation) {
        super("SECURITY_ERROR", message, operation);
    }
}
