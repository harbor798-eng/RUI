package com.harbor.relationshipassistant.application.skill;

import java.nio.file.Path;

/**
 * Skill 项目的资源目录定位。不可变。目录不存在时对应字段为 null。
 */
public final class SkillResources {
    private final Path skillDirectory;
    private final Path referencesDirectory;
    private final Path scriptsDirectory;
    private final Path assetsDirectory;

    public SkillResources(Path skillDirectory, Path referencesDirectory, Path scriptsDirectory, Path assetsDirectory) {
        this.skillDirectory = skillDirectory;
        this.referencesDirectory = referencesDirectory;
        this.scriptsDirectory = scriptsDirectory;
        this.assetsDirectory = assetsDirectory;
    }

    public Path getSkillDirectory() { return skillDirectory; }
    public Path getReferencesDirectory() { return referencesDirectory; }
    public Path getScriptsDirectory() { return scriptsDirectory; }
    public Path getAssetsDirectory() { return assetsDirectory; }
}
