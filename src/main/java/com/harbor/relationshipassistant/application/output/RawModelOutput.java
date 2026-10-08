package com.harbor.relationshipassistant.application.output;

import com.harbor.relationshipassistant.application.skill.context.AnalysisFunction;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 模型原始输出载体。
 *
 * <p>Output Runtime 的最底层数据：保留 LLM 返回的原始文本，不做任何业务解析、
 * 不假设 Skill 输出格式、不读取业务 JSON、不判断三策略/情绪词。</p>
 *
 * <p>该对象是不可变的；上层（Quick Reply Adapter / Detail Analysis Adapter / UI）
 * 负责决定如何把 content 解释成业务模型。</p>
 */
public final class RawModelOutput {
    private final String content;
    private final String model;
    private final AnalysisFunction function;
    private final String skillName;
    private final LocalDateTime createdAt;

    public RawModelOutput(String content, String model, AnalysisFunction function,
                          String skillName, LocalDateTime createdAt) {
        this.content = Objects.requireNonNull(content, "content");
        this.model = model;
        this.function = function;
        this.skillName = skillName;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }

    public String getContent() { return content; }
    public String getModel() { return model; }
    public AnalysisFunction getFunction() { return function; }
    public String getSkillName() { return skillName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
