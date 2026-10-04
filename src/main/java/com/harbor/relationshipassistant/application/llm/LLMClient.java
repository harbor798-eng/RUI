package com.harbor.relationshipassistant.application.llm;

import com.harbor.relationshipassistant.application.prompt.LLMRequest;

/**
 * LLM 通信抽象。只认识 LLMRequest → LLMResponse，不认识 Skill/Knowledge/Repository。
 */
public interface LLMClient {
    LLMResponse complete(LLMRequest request);
}
