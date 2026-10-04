package com.harbor.relationshipassistant.application.skill;

import java.util.Objects;

/**
 * SKILL.md frontmatter 中解析出的元数据。不可变。
 * V1 只保留 name + description；其他字段忽略。
 */
public final class SkillMetadata {
    private final String name;
    private final String description;

    public SkillMetadata(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SkillMetadata)) return false;
        SkillMetadata that = (SkillMetadata) o;
        return Objects.equals(name, that.name) && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() { return Objects.hash(name, description); }
}
