package com.harbor.relationshipassistant.application.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 真正的 SKILL.md 加载器：
 * 读取 UTF-8 SKILL.md → FrontmatterParser → SkillDefinition + SkillResources → SkillDescriptor。
 * 不做校验（校验由 SkillValidator 负责）。
 */
public class DefaultSkillLoader implements SkillLoader {
    private static final Logger log = LoggerFactory.getLogger(DefaultSkillLoader.class);
    private final FrontmatterParser parser = new FrontmatterParser();

    @Override
    public SkillDescriptor load(Path skillDirectory) throws Exception {
        Path dir = skillDirectory.toAbsolutePath().normalize();
        Path md = dir.resolve("SKILL.md");
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("not a directory: " + dir);
        }
        if (!Files.isRegularFile(md)) {
            throw new IllegalStateException("SKILL.md not found in " + dir);
        }
        String markdown;
        try {
            markdown = Files.readString(md, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read SKILL.md: " + e.getMessage());
        }

        FrontmatterParser.Result parsed = parser.parse(markdown);
        SkillDefinition definition = new SkillDefinition(parsed.metadata, parsed.instructions);

        Path refs = subIfExists(dir, "references");
        Path scripts = subIfExists(dir, "scripts");
        Path assets = subIfExists(dir, "assets");
        SkillResources resources = new SkillResources(dir, refs, scripts, assets);

        log.info("[SKILL-LOAD] loaded skill name={} references={} scripts={} assets={}",
                definition.getMetadata().getName(), refs != null, scripts != null, assets != null);
        return new SkillDescriptor(definition, resources);
    }

    private static Path subIfExists(Path dir, String name) {
        Path p = dir.resolve(name);
        return Files.isDirectory(p) ? p : null;
    }
}
