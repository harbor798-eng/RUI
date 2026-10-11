package com.harbor.relationshipassistant.application.distill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

/**
 * 原子发布 ex-skill：
 *   1. 在 skillsRoot 之外的 ./.rui-staging/ex_{slug}-{ts}/ 写全部文件
 *   2. 校验 SKILL.md frontmatter、meta.json、memories.md、persona.md
 *   3. 如果 ./skills/ex_{slug}/ 已存在 → 直接报错，不覆盖
 *   4. ATOMIC_MOVE staging → ./skills/ex_{slug}/（同文件系统）
 *   5. 任一步失败清理 staging
 */
public class ExSkillPublisher {
    private static final Logger log = LoggerFactory.getLogger(ExSkillPublisher.class);

    private final Path skillsRoot;

    public ExSkillPublisher(Path skillsRoot) {
        this.skillsRoot = skillsRoot.toAbsolutePath().normalize();
    }

    public record Payload(String skillMarkdown, String memoriesMarkdown,
                          String personaMarkdown, String metaJson) {}

    public Path publish(String slug, Payload payload) throws IOException {
        Path target = skillsRoot.resolve(slug);
        if (Files.exists(target)) {
            throw new IOException("Skill 目录已存在: " + target + "。请更换昵称/slug，版本更新暂未实现。");
        }

        Path staging = Paths.get(".", ".rui-staging", slug + "-" + System.currentTimeMillis())
                .toAbsolutePath().normalize();
        Files.createDirectories(staging);

        try {
            // 写文件到 staging
            Files.writeString(staging.resolve("SKILL.md"), payload.skillMarkdown());
            Files.writeString(staging.resolve("memories.md"), payload.memoriesMarkdown());
            Files.writeString(staging.resolve("persona.md"), payload.personaMarkdown());
            Files.writeString(staging.resolve("meta.json"), payload.metaJson());

            // 校验
            validate(staging, payload);

            // 原子移动（同文件系统）
            Files.createDirectories(skillsRoot);
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                // 同文件系统下 ATOMIC_MOVE 一般支持；不支持时显式报错而不是静默降级
                throw new IOException("ATOMIC_MOVE 不支持（staging=" + staging.getFileSystem()
                        + " target=" + target.getFileSystem() + "），拒绝静默降级", e);
            }
            log.info("[EX-PUBLISH] published {} -> {}", slug, target);
            return target;
        } catch (IOException e) {
            cleanup(staging);
            throw e;
        } catch (RuntimeException e) {
            cleanup(staging);
            throw e;
        }
    }

    private void validate(Path staging, Payload p) {
        // SKILL.md 必须有 frontmatter
        String skill = p.skillMarkdown();
        if (skill == null || !skill.startsWith("---\n")) {
            throw new IllegalStateException("SKILL.md 缺少 frontmatter");
        }
        int end = skill.indexOf("\n---\n", 4);
        if (end < 0) throw new IllegalStateException("SKILL.md frontmatter 未闭合");
        String fm = skill.substring(4, end);
        if (!fm.contains("name:") || !fm.contains("description:")) {
            throw new IllegalStateException("SKILL.md frontmatter 缺少 name 或 description");
        }
        if (p.memoriesMarkdown() == null || p.memoriesMarkdown().isBlank()) {
            throw new IllegalStateException("memories.md 为空");
        }
        if (p.personaMarkdown() == null || p.personaMarkdown().isBlank()) {
            throw new IllegalStateException("persona.md 为空");
        }
        if (p.metaJson() == null || !p.metaJson().contains("\"slug\"")) {
            throw new IllegalStateException("meta.json 缺少 slug 字段");
        }
    }

    private void cleanup(Path staging) {
        try {
            if (!Files.exists(staging)) return;
            Files.walk(staging)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException e) {
            log.warn("[EX-PUBLISH] cleanup failed: {}", e.getMessage());
        }
    }
}
