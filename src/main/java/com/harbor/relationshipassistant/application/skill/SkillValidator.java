package com.harbor.relationshipassistant.application.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 校验已构造的 SkillDescriptor。V1 只做 name/description 非空检查。
 * instructions 允许为空（标准未强制要求正文非空）。
 */
public final class SkillValidator {
    private static final Logger log = LoggerFactory.getLogger(SkillValidator.class);

    public ValidationResult validate(SkillDescriptor descriptor) {
        if (descriptor == null || descriptor.getDefinition() == null || descriptor.getDefinition().getMetadata() == null) {
            return ValidationResult.error("Missing metadata");
        }
        SkillMetadata m = descriptor.getDefinition().getMetadata();
        if (m.getName() == null || m.getName().isBlank()) {
            return ValidationResult.error("Missing or empty name");
        }
        if (m.getDescription() == null || m.getDescription().isBlank()) {
            return ValidationResult.error("Missing or empty description");
        }
        log.info("[SKILL-VALIDATE] success name={}", m.getName());
        return ValidationResult.ok();
    }
}
