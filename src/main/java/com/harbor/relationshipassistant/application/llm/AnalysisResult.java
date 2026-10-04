package com.harbor.relationshipassistant.application.llm;

import com.harbor.relationshipassistant.application.skill.context.AnalysisFunction;

import java.time.LocalDateTime;

/**
 * JEVE 业务层分析结果。不保存 Prompt、API Key、聊天原文以外的敏感上下文。
 */
public final class AnalysisResult {
    private final AnalysisFunction function;
    private final String skillName;
    private final String content;
    private final String model;
    private final LocalDateTime createdAt;

    public AnalysisResult(AnalysisFunction function, String skillName, String content, String model, LocalDateTime createdAt) {
        this.function = function;
        this.skillName = skillName;
        this.content = content;
        this.model = model;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }

    public AnalysisFunction getFunction() { return function; }
    public String getSkillName() { return skillName; }
    public String getContent() { return content; }
    public String getModel() { return model; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
