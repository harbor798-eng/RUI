package com.harbor.relationshipassistant.application.prompt;

/**
 * 组装完成的不可变 Prompt。
 */
public final class Prompt {
    private final String systemPrompt;
    private final String userPrompt;

    public Prompt(String systemPrompt, String userPrompt) {
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
        this.userPrompt = userPrompt == null ? "" : userPrompt;
    }

    public String getSystemPrompt() { return systemPrompt; }
    public String getUserPrompt() { return userPrompt; }
}
