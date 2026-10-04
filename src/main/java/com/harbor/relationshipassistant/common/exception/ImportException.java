package com.harbor.relationshipassistant.common.exception;

public class ImportException extends ApplicationException {
    public ImportException(String message, String operation) {
        super("IMPORT_ERROR", message, operation);
    }
    public ImportException(String message, String operation, Throwable cause) {
        super("IMPORT_ERROR", message, operation, null, cause);
    }
}
