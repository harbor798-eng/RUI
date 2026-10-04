package com.harbor.relationshipassistant.common.exception;

public class OCRException extends ApplicationException {
    public OCRException(String message, String operation, Throwable cause) {
        super("OCR_ERROR", message, operation, null, cause);
    }
}
