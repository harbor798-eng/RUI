package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.infrastructure.ai.AIProvider;
import com.harbor.relationshipassistant.infrastructure.ai.AIRequest;
import com.harbor.relationshipassistant.infrastructure.ai.AIResponse;

/**
 * 统一执行一次 AI 调用。本类不选择 Provider、不读配置、不做 JSON Parse、不做 Retry。
 * Provider 由调用方注入（选择逻辑属于配置层）。
 */
public class AnalysisExecutor {

    private final AIProvider provider;

    public AnalysisExecutor(AIProvider provider) {
        if (provider == null) throw new IllegalArgumentException("provider must not be null");
        this.provider = provider;
    }

    public AIResponse execute(AIRequest request) {
        if (request == null) throw new IllegalArgumentException("AIRequest must not be null");
        System.out.println("[AnalysisExecutor] start. provider=" + provider.getProviderName()
                + ", model=" + request.getModel()
                + ", temperature=" + request.getTemperature()
                + ", maxTokens=" + request.getMaxTokens());
        System.out.println("[AnalysisExecutor] calling provider");

        AIResponse resp;
        try {
            resp = provider.generate(request);
        } catch (RuntimeException e) {
            System.out.println("[AnalysisExecutor][ERROR] type=" + e.getClass().getSimpleName()
                    + ", message=" + e.getMessage());
            throw e;
        }

        if (resp == null) {
            throw new IllegalStateException("AIProvider returned null response");
        }
        String text = resp.text();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("AIProvider returned empty content");
        }
        System.out.println("[AnalysisExecutor] completed. provider=" + resp.provider()
                + ", model=" + resp.model() + ", contentLength=" + text.length());
        return resp;
    }
}
