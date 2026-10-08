package com.harbor.relationshipassistant.application.skill.adapter;

import com.harbor.relationshipassistant.application.skill.SkillDescriptor;
import com.harbor.relationshipassistant.application.skill.SkillRouter;
import com.harbor.relationshipassistant.application.skill.context.AnalysisFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * 通用 Skill 解析入口：
 *   userSelectedSkillName + function 默认 Skill
 *     → SkillResolution
 *
 * 优先级：USER_SELECTED > FUNCTION_DEFAULT > NONE。
 * 不决定 singleReply / useKnowledge，不构造 AnalysisTask，不读写 Prompt/Knowledge/LLM。
 * 仅复用现有 SkillRouter 完成 "name → READY SkillDescriptor" 解析。
 */
public final class SkillAdapter {
    private static final Logger log = LoggerFactory.getLogger(SkillAdapter.class);

    private final SkillRouter skillRouter;
    private final Map<AnalysisFunction, String> defaultSkillByFunction;

    public SkillAdapter(SkillRouter skillRouter, Map<AnalysisFunction, String> defaultSkillByFunction) {
        this.skillRouter = skillRouter;
        this.defaultSkillByFunction = Map.copyOf(defaultSkillByFunction);
    }

    /**
     * @param function             当前 Function
     * @param userSelectedSkillName 用户从 UI 选择的 Skill 名；空串或 null 表示未选择
     * @return 解析结果
     * @throws SkillNotAvailableException 用户显式选择了一个不存在或非 READY 的 Skill
     */
    public SkillResolution resolve(AnalysisFunction function, String userSelectedSkillName) {
        String userSkill = userSelectedSkillName == null ? "" : userSelectedSkillName.trim();

        if (!userSkill.isEmpty()) {
            Optional<SkillDescriptor> matched = skillRouter.route(userSkill);
            if (matched.isEmpty()) {
                log.warn("[SKILL-ADAPTER] user-selected skill not available function={} skill={}", function, userSkill);
                throw new SkillNotAvailableException(userSkill,
                        "Skill '" + userSkill + "' is not available (not found or not READY).");
            }
            log.info("[SKILL-ADAPTER] resolve function={} source=USER_SELECTED skill={}", function, userSkill);
            return SkillResolution.userSelected(matched.get());
        }

        String defaultName = defaultSkillByFunction.get(function);
        if (defaultName == null || defaultName.isBlank()) {
            log.info("[SKILL-ADAPTER] resolve function={} source=NONE", function);
            return SkillResolution.none();
        }
        Optional<SkillDescriptor> matched = skillRouter.route(defaultName);
        if (matched.isEmpty()) {
            // Function 默认 Skill 应该 READY；若缺失，返回 NONE 而不是抛错，避免阻断默认流程。
            log.warn("[SKILL-ADAPTER] function default skill not READY function={} default={}", function, defaultName);
            return SkillResolution.none();
        }
        log.info("[SKILL-ADAPTER] resolve function={} source=FUNCTION_DEFAULT skill={}", function, defaultName);
        return SkillResolution.functionDefault(matched.get());
    }
}
