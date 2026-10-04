package com.harbor.relationshipassistant.application.skill;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 一个已加载并通过校验的 Skill 内容快照。不可变。
 * 运行时状态（generation/loading/error）仍在 SkillRuntimeState。
 */
public final class SkillDescriptor {
    private final SkillDefinition definition;
    private final SkillResources resources;

    public SkillDescriptor(SkillDefinition definition, SkillResources resources) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    public SkillDefinition getDefinition() { return definition; }
    public SkillResources getResources() { return resources; }

    /** 兼容旧 API：name 来自 metadata。 */
    public String getName() { return definition.getMetadata().getName(); }
    /** 兼容旧 API：skillDirectory 来自 resources。 */
    public Path getSkillDirectory() { return resources.getSkillDirectory(); }
    public String getDescription() { return definition.getMetadata().getDescription(); }

    @Override
    public String toString() {
        return "SkillDescriptor{name=" + getName() + ", dir=" + getSkillDirectory() + "}";
    }
}
