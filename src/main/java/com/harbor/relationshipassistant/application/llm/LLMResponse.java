package com.harbor.relationshipassistant.application.llm;

/**
 * 模型通信层结果。不包含分析业务字段。
 */
public record LLMResponse(String content, String model, String finishReason) {
    public LLMResponse {
        if (content == null || content.isBlank()) {
            throw new LLMException("LLM returned empty content");
        }
    }
}
