package com.harbor.relationshipassistant.infrastructure.ai;

/** AI 返回结果。 */
public record AIResponse(String text, String provider, String model,
                         Integer promptTokens, Integer completionTokens) {
}
