package com.harbor.relationshipassistant.application.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * 根据显式 Skill 名称选择当前 READY 的 Skill。
 * V1 不做 AI 自动选择，不调用 LLM，不加载文件，不写 Registry。
 */
public final class SkillRouter {
    private static final Logger log = LoggerFactory.getLogger(SkillRouter.class);

    private final SkillManager skillManager;

    public SkillRouter(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    /**
     * 按名称路由到一个 READY Skill。
     * name 为空或不存在时返回 Optional.empty()，不抛异常。
     */
    public Optional<SkillDescriptor> route(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            log.info("[SKILL-ROUTER] route empty name");
            return Optional.empty();
        }
        log.info("[SKILL-ROUTER] route skillName={}", skillName);
        Optional<SkillDescriptor> result = skillManager.getSkill(skillName);
        if (result.isPresent()) {
            log.info("[SKILL-ROUTER] matched skillName={}", skillName);
        } else {
            log.info("[SKILL-ROUTER] skill not found name={}", skillName);
        }
        return result;
    }
}
