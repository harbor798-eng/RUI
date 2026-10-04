package com.harbor.relationshipassistant.common.exception;

public class DatabaseException extends ApplicationException {
    public DatabaseException(String message, String operation, Throwable cause) {
        super("DB_ERROR", message, operation, null, cause);
    }
}
