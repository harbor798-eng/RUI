package com.harbor.relationshipassistant.application.skill;

import java.nio.file.Path;

/**
 * 加载并校验一个 Skill 目录，返回完整的 {@link SkillDescriptor}。
 * 实现负责读取 SKILL.md / 元数据 / 规则；本阶段提供默认 stub。
 */
public interface SkillLoader {

    /**
     * 加载并校验指定目录。
     * @throws Exception 加载或校验失败时抛出，异常会被 Manager 捕获并标记 INVALID
     */
    SkillDescriptor load(Path skillDirectory) throws Exception;
}
