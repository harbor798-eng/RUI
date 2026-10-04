package com.harbor.relationshipassistant.application.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.Map;

/**
 * 解析 SKILL.md 的 YAML frontmatter。
 * 只负责 frontmatter → SkillMetadata；不负责提取 instructions。
 */
public final class FrontmatterParser {
    private static final Logger log = LoggerFactory.getLogger(FrontmatterParser.class);

    public static final class Result {
        public final SkillMetadata metadata;
        public final String instructions;
        public Result(SkillMetadata metadata, String instructions) {
            this.metadata = metadata;
            this.instructions = instructions;
        }
    }

    /**
     * @throws IllegalArgumentException frontmatter 缺失/格式错误/字段缺失时抛出
     */
    public Result parse(String markdown) {
        if (markdown == null) throw new IllegalArgumentException("SKILL.md is empty");
        String text = markdown.replace("\r\n", "\n");
        if (!text.startsWith("---\n")) {
            throw new IllegalArgumentException("Missing frontmatter (must start with ---)");
        }
        int end = text.indexOf("\n---\n", 4);
        if (end < 0) {
            // 也允许文件末尾就是 ---
            if (text.endsWith("\n---")) {
                end = text.length() - 4;
            } else {
                throw new IllegalArgumentException("Unterminated frontmatter (closing --- not found)");
            }
        }
        String yamlBlock = text.substring(4, end).trim();
        String body = text.substring(end + 5);
        // body 去掉开头一个换行
        if (body.startsWith("\n")) body = body.substring(1);

        Yaml yaml = new Yaml();
        Map<String, Object> data;
        try {
            Object parsed = yaml.load(yamlBlock);
            if (!(parsed instanceof Map)) {
                throw new IllegalArgumentException("Frontmatter is not a YAML map");
            }
            data = (Map<String, Object>) parsed;
        } catch (YAMLException e) {
            throw new IllegalArgumentException("YAML parse error: " + e.getMessage());
        }

        Object nameObj = data.get("name");
        Object descObj = data.get("description");
        if (nameObj == null || nameObj.toString().isBlank()) {
            throw new IllegalArgumentException("Missing or empty 'name'");
        }
        if (descObj == null || descObj.toString().isBlank()) {
            throw new IllegalArgumentException("Missing or empty 'description'");
        }
        SkillMetadata metadata = new SkillMetadata(nameObj.toString().trim(), descObj.toString().trim());
        log.info("[SKILL-FRONTMATTER] parsed name={}", metadata.getName());
        return new Result(metadata, body);
    }
}
