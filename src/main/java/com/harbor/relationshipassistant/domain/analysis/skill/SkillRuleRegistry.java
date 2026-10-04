package com.harbor.relationshipassistant.domain.analysis.skill;

import java.util.Map;
import java.util.Set;

/**
 * ruleId → 规则正文的内置注册表。
 * <p>
 * V1 集中管理，不读 MD；后续如需外置再做 SkillRuleLoader。
 * </p>
 */
public final class SkillRuleRegistry {

    private static final Map<String, String> RULES = Map.ofEntries(
            Map.entry("EMOTION_GROUNDING",
                    "把情绪落地到具体可观察的对话片段；不要把情绪当成无来源的标签。"),
            Map.entry("FACT_SPLIT",
                    "严格区分【事实】(有明确聊天/统计/已确认档案来源)、【推测】(AI 的解释)、【未知】(证据不足)；三者不得混淆。"),
            Map.entry("BEHAVIORAL_INTEREST",
                    "从可观察行为判断互动意义：承诺、回应性、尊重、修复、公平；不要从一句话脑补心理动机。"),
            Map.entry("PRIMARY_RECOMMENDATION",
                    "给出一句首选建议，而不是罗列多个互相矛盾的方向。"),
            Map.entry("REASONS_2_TO_4",
                    "首选建议必须给出 2~4 个理由，每个理由都要能回溯到证据。"),
            Map.entry("SMALL_ACTION",
                    "给一个本周可执行的小动作，具体、低风险、不需要对方配合也能开始。"),
            Map.entry("OBSERVATION_WINDOW",
                    "告诉用户接下来观察什么、观察多久、什么信号算支持/反对当前解释。"),
            Map.entry("STOP_CONDITIONS",
                    "明确写出什么情况下应该停止继续推进或停止当前解释。"),
            Map.entry("LOCKED_SPEAKER",
                    "严格锁定说话人 ME / OTHER / SYSTEM；不得交换说话人，不得把对方的话安到用户头上。"),
            Map.entry("NO_FABRICATED_SPEAKER",
                    "不得虚构任何一方说过的话、时间或行为。"),
            Map.entry("NO_SPECULATION_AS_FACT",
                    "推测只能写为【可能性】，不得写成【事实】。"),
            Map.entry("NO_PERSONALITY_DIAGNOSIS",
                    "不得根据聊天直接判定人格障碍、依恋类型、心理疾病、MBTI 结论；如讨论只能作为开放解释框架。"),
            Map.entry("NO_FUTURE_PREDICTION_AS_FACT",
                    "不得把'他一定会回来''你们肯定会复合'等未来预测写成事实。"),
            Map.entry("PERSISTENT_BEHAVIOR_OVER_SINGLE",
                    "单次行为只是线索；重复/持续/多证据才能支持行为模式结论。"),
            Map.entry("COLD_READ_REFRAMED",
                    "所谓'读心'必须改写为：观察到的事实 + 暂定假设 + 邀请用户纠正；禁止冷读式断言。"),
            Map.entry("EVIDENCE_FIRST",
                    "所有结论优先引用当前 Context 中真实存在的 evidenceId；禁止创造 EV-xxx。")
    );

    public String getRule(String ruleId) {
        return RULES.get(ruleId);
    }

    public boolean contains(String ruleId) {
        return RULES.containsKey(ruleId);
    }

    public Set<String> allRuleIds() {
        return RULES.keySet();
    }
}
