package com.harbor.relationshipassistant.infrastructure.ai;

import java.util.List;

/**
 * AI Provider 抽象（技术设计 §27 / PRD §33）。
 * 不绑定单一厂商：OpenAI 兼容协议可覆盖 DeepSeek / Doubao / 多数网关。
 */
public interface AIProvider {

    AIResponse generate(AIRequest request);

    String getProviderName();

    List<String> getSupportedModels();
}
