package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.domain.analysis.AnalysisContext;
import com.harbor.relationshipassistant.domain.analysis.OutputMode;
import com.harbor.relationshipassistant.domain.analysis.Skill;
import com.harbor.relationshipassistant.domain.analysis.skill.SkillDefinition;

import java.util.List;
import java.util.Map;

/**
 * 根据 {@link AnalysisContext} 中已经确定的 {@link Skill}，路由到对应的 {@link SkillDefinition}。
 * <p>
 * SkillRouter 是路由器，不是决策 AI：
 * <ul>
 *   <li>不根据聊天内容/Evidence/Pattern 重新猜 Skill；</li>
 *   <li>不修改 AnalysisContext；</li>
 *   <li>不调用 LLM；</li>
 *   <li>不选 Knowledge、不拼 Prompt。</li>
 * </ul>
 * </p>
 */
public class SkillRouter {

    private static final Map<Skill, SkillDefinition> REGISTRY = buildRegistry();

    public SkillDefinition route(AnalysisContext context) {
        System.out.println("[SkillRouter] Start routing Skill");
        if (context == null || context.getTask() == null) {
            throw new IllegalArgumentException("[SkillRouter][ERROR] context/task must not be null");
        }
        Skill skill = context.getTask().getSkill();
        System.out.println("[SkillRouter] taskId=" + context.getTask().getId());
        System.out.println("[SkillRouter] relationshipId=" + context.getTask().getRelationshipId());
        System.out.println("[SkillRouter] taskType=" + context.getTask().getTaskType());
        System.out.println("[SkillRouter] skill=" + skill);
        System.out.println("[SkillRouter] outputMode=" + context.getTask().getOutputMode());

        if (skill == null) {
            throw new IllegalArgumentException("[SkillRouter][ERROR] task.skill must not be null");
        }
        SkillDefinition def = REGISTRY.get(skill);
        if (def == null) {
            throw new IllegalArgumentException("[SkillRouter][ERROR] No SkillDefinition for skill=" + skill);
        }
        System.out.println("[SkillRouter] selected skill=" + def.getId());
        System.out.println("[SkillRouter] ruleIds=" + def.getRuleIds().size());
        System.out.println("[SkillRouter] Skill routing completed");
        return def;
    }

    private static Map<Skill, SkillDefinition> buildRegistry() {
        SkillDefinition jeveNative = new SkillDefinition(
                Skill.NONE,
                "jeve-native",
                "JEVE Native",
                "JEVE 原生分析规则；不加载外部 Skill。",
                List.of(),
                List.of(OutputMode.NORMAL, OutputMode.WAKE_UP));

        SkillDefinition goutou = new SkillDefinition(
                Skill.GOUTOUJUNSHI,
                "goutoujunshi",
                "狗头军师",
                "狗头军师 Skill：情绪落地、事实拆分、行为化利益判断、给首选建议+理由+小动作+观察窗口+停止条件。",
                List.of(
                        "EMOTION_GROUNDING",
                        "FACT_SPLIT",
                        "BEHAVIORAL_INTEREST",
                        "PRIMARY_RECOMMENDATION",
                        "REASONS_2_TO_4",
                        "SMALL_ACTION",
                        "OBSERVATION_WINDOW",
                        "STOP_CONDITIONS",
                        "LOCKED_SPEAKER",
                        "NO_FABRICATED_SPEAKER",
                        "NO_SPECULATION_AS_FACT",
                        "NO_PERSONALITY_DIAGNOSIS",
                        "NO_FUTURE_PREDICTION_AS_FACT",
                        "PERSISTENT_BEHAVIOR_OVER_SINGLE",
                        "COLD_READ_REFRAMED",
                        "EVIDENCE_FIRST"
                ),
                List.of(OutputMode.NORMAL, OutputMode.WAKE_UP));

        return Map.of(
                Skill.NONE, jeveNative,
                Skill.GOUTOUJUNSHI, goutou);
    }
}
