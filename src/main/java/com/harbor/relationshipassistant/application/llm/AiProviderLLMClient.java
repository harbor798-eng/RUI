package com.harbor.relationshipassistant.application.llm;

import com.harbor.relationshipassistant.application.prompt.LLMRequest;
import com.harbor.relationshipassistant.infrastructure.ai.AIProvider;
import com.harbor.relationshipassistant.infrastructure.ai.AIRequest;
import com.harbor.relationshipassistant.infrastructure.ai.AIResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 把现有 infrastructure 层 AIProvider（DeepSeek / OpenAI 兼容）适配为 LLMClient。
 * 不重复实现 HTTP/JSON/Key 管理；API Key 仍只存在于 AIProvider 内部。
 */
public final class AiProviderLLMClient implements LLMClient {
    private static final Logger log = LoggerFactory.getLogger(AiProviderLLMClient.class);

    private final AIProvider provider;

    public AiProviderLLMClient(AIProvider provider) {
        this.provider = provider;
    }

    @Override
    public LLMResponse complete(LLMRequest request) {
        if (request == null) throw new LLMException("LLMRequest is required");
        AIRequest aiReq = AIRequest.of(
                request.getSystemPrompt(),
                List.of(new AIRequest.Turn("user", request.getUserPrompt())));
        long t0 = System.currentTimeMillis();
        log.info("[LLM] execute provider={}", provider.getProviderName());
        AIResponse resp;
        try {
            resp = provider.generate(aiReq);
        } catch (com.harbor.relationshipassistant.common.exception.AIException e) {
            log.warn("[LLM] request failed: {}", e.getMessage());
            throw new LLMException("LLM request failed: " + e.getMessage(), e);
        }
        if (resp == null || resp.text() == null || resp.text().isBlank()) {
            throw new LLMException("LLM returned empty content");
        }
        log.info("[LLM] response received model={} contentLength={} elapsedMs={}",
                resp.model(), resp.text().length(), System.currentTimeMillis() - t0);
        return new LLMResponse(resp.text(), resp.model(), null);
    }
}
