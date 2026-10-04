package com.harbor.relationshipassistant.infrastructure.ai;

import java.util.List;

/** 发给 AI Provider 的请求（已脱敏后的 Context，不含 API Key）。 */
public class AIRequest {

    public record Turn(String role, String content) {}

    private String model;
    private double temperature = 0.8;
    private int maxTokens = 400;
    private String systemPrompt;
    private List<Turn> turns;

    public static AIRequest of(String systemPrompt, List<Turn> turns) {
        AIRequest r = new AIRequest();
        r.systemPrompt = systemPrompt;
        r.turns = turns;
        return r;
    }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public List<Turn> getTurns() { return turns; }
}
