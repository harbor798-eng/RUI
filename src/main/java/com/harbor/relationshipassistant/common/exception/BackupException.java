package com.harbor.relationshipassistant.common.exception;

public class BackupException extends ApplicationException {
    public BackupException(String message, String operation, Throwable cause) {
        super("BACKUP_ERROR", message, operation, null, cause);
    }
}
