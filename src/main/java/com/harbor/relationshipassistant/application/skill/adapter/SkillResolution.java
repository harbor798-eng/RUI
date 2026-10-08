package com.harbor.relationshipassistant.application.skill.adapter;

import com.harbor.relationshipassistant.application.skill.SkillDescriptor;

import java.util.Optional;

/**
 * 一次 Function 调用最终解析出的 Skill 及其来源。
 * 不携带 Runtime 内部状态（generation / runtimeState / directory / manager）。
 */
public record SkillResolution(SkillSelectionSource source, Optional<SkillDescriptor> skill) {

    public static SkillResolution none() {
        return new SkillResolution(SkillSelectionSource.NONE, Optional.empty());
    }

    public static SkillResolution functionDefault(SkillDescriptor descriptor) {
        return new SkillResolution(SkillSelectionSource.FUNCTION_DEFAULT, Optional.of(descriptor));
    }

    public static SkillResolution userSelected(SkillDescriptor descriptor) {
        return new SkillResolution(SkillSelectionSource.USER_SELECTED, Optional.of(descriptor));
    }

    public boolean isUserSelected() {
        return source == SkillSelectionSource.USER_SELECTED;
    }

    public String resolvedSkillName() {
        return skill().map(SkillDescriptor::getName).orElse("");
    }
}
