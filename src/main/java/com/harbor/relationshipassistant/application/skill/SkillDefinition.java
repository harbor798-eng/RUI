package com.harbor.relationshipassistant.application.skill;

import java.util.Objects;

/**
 * 一个已解析的 Skill 内容：metadata + Markdown instructions 原文。
 * instructions 保持 Markdown 原文，不做 HTML 转换或总结。
 */
public final class SkillDefinition {
    private final SkillMetadata metadata;
    private final String instructions;

    public SkillDefinition(SkillMetadata metadata, String instructions) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.instructions = instructions == null ? "" : instructions;
    }

    public SkillMetadata getMetadata() { return metadata; }
    public String getInstructions() { return instructions; }
}
