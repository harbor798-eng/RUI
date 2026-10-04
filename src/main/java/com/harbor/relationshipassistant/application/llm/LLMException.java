package com.harbor.relationshipassistant.application.llm;

/**
 * LLM 调用异常。消息安全，绝不包含 API Key / Authorization / 完整 Prompt。
 */
public class LLMException extends RuntimeException {
    public LLMException(String message) { super(message); }
    public LLMException(String message, Throwable cause) { super(message, cause); }
}
