package com.harbor.relationshipassistant.application.prompt;

/**
 * 准备发送给未来 LLM Client 的不可变请求。本阶段不发送任何 HTTP。
 */
public final class LLMRequest {
    private final String systemPrompt;
    private final String userPrompt;

    public LLMRequest(String systemPrompt, String userPrompt) {
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
        this.userPrompt = userPrompt == null ? "" : userPrompt;
    }

    public static LLMRequest from(Prompt p) {
        return new LLMRequest(p.getSystemPrompt(), p.getUserPrompt());
    }

    public String getSystemPrompt() { return systemPrompt; }
    public String getUserPrompt() { return userPrompt; }
}
