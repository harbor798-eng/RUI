package com.harbor.relationshipassistant.common.exception;

/**
 * 业务异常基类（技术设计文档 §44）。
 * 携带 errorCode / operation / relationshipId，便于日志与 UI 提示。
 */
public class ApplicationException extends RuntimeException {

    private final String errorCode;
    private final String operation;
    private final Long relationshipId;

    public ApplicationException(String errorCode, String message, String operation) {
        this(errorCode, message, operation, null, null);
    }

    public ApplicationException(String errorCode, String message, String operation, Long relationshipId, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.operation = operation;
        this.relationshipId = relationshipId;
    }

    public String getErrorCode() { return errorCode; }
    public String getOperation() { return operation; }
    public Long getRelationshipId() { return relationshipId; }
}
