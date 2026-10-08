package com.harbor.relationshipassistant.application.systemhost;

import com.harbor.relationshipassistant.application.skill.adapter.SkillResolution;
import com.harbor.relationshipassistant.application.skill.context.AnalysisFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * System Host 规划器。
 *
 * <p>输入：Function（WHAT）+ SkillResolution（用户选的 Skill 或 Function 默认）。
 * 输出：不可变的 {@link PlannedExecution}，携带唯一的 ActiveSkill、DeliveryMode 标签
 * 和权威 {@link ResultSpec}。</p>
 *
 * <p>Stage 5-2 产品矩阵（封版）：
 * <ul>
 *   <li>QUICK_REPLY + 未选 Skill → SYSTEM:default-three-strategy，THREE_BY_THREE，
 *       spec = SENDABLE_REPLY×9（NATURAL/PROACTIVE/LIGHT_FLIRT 各 3）。</li>
 *   <li>QUICK_REPLY + User Skill=tong-jincheng-perspective → USER，SINGLE_RESULT，
 *       spec = SENDABLE_REPLY×1 + SHORT_ANALYSIS×1。</li>
 *   <li>QUICK_REPLY + 其他 User Skill → USER，SINGLE_RESULT，spec = SENDABLE_REPLY×1。</li>
 *   <li>DETAILED_ANALYSIS + 未选 Skill（默认三策略）→ 路由到 USER:goutoujunshi，
 *       spec = ANALYSIS_REPORT×1，notice = 提示文案。</li>
 *   <li>DETAILED_ANALYSIS + User Skill=tong-jincheng-perspective → spec = SHORT_ANALYSIS×1。</li>
 *   <li>DETAILED_ANALYSIS + 其他 User Skill → spec = ANALYSIS_REPORT×1。</li>
 * </ul>
 *
 * <p>本阶段不做 Conflict Engine、不做 Skill Composition。</p>
 */
public final class SystemHostPlanner {

    private static final Logger log = LoggerFactory.getLogger(SystemHostPlanner.class);

    public static final String SKILL_TONG_JINCHENG = "tong-jincheng-perspective";
    public static final String SKILL_GOUTOUJUNSHI = "goutoujunshi";

    public PlannedExecution plan(AnalysisFunction function, SkillResolution resolution) {
        if (function == null) throw new IllegalArgumentException("function must not be null");
        if (resolution == null) throw new IllegalArgumentException("resolution must not be null");

        ActiveSkill active;
        DeliveryMode delivery;
        ResultSpec spec;
        String notice = "";

        if (function == AnalysisFunction.QUICK_REPLY) {
            if (resolution.isUserSelected()) {
                String name = resolution.resolvedSkillName();
                active = ActiveSkill.userSkill(name);
                delivery = DeliveryMode.SINGLE_RESULT;
                if (SKILL_TONG_JINCHENG.equals(name)) {
                    spec = ResultSpec.quickReplyTongJincheng();
                } else {
                    spec = ResultSpec.quickReplySingleSendable();
                }
            } else {
                // 未选 Skill → System Skill：默认三策略，3×3。
                active = ActiveSkill.systemDefaultThreeStrategy();
                delivery = DeliveryMode.THREE_BY_THREE;
                spec = ResultSpec.quickReplyDefaultThreeStrategy();
            }
        } else if (function == AnalysisFunction.DETAILED_ANALYSIS) {
            if (resolution.isUserSelected()) {
                String name = resolution.resolvedSkillName();
                active = ActiveSkill.userSkill(name);
                delivery = DeliveryMode.SINGLE_RESULT;
                if (SKILL_TONG_JINCHENG.equals(name)) {
                    spec = ResultSpec.detailShortAnalysis();
                } else {
                    spec = ResultSpec.detailAnalysisReport();
                }
            } else {
                // 默认三策略不提供 Detailed Analysis → 路由到 goutoujunshi。
                active = ActiveSkill.userSkill(SKILL_GOUTOUJUNSHI);
                delivery = DeliveryMode.SINGLE_RESULT;
                spec = ResultSpec.detailAnalysisReport();
                notice = "当前默认三策略不提供详细分析，本次分析将采用狗头军师。";
            }
        } else {
            // DEEP_OBSERVATION：当前未在 Result Contract 内。
            String defaultName = resolution.resolvedSkillName();
            if (defaultName == null || defaultName.isBlank()) {
                active = ActiveSkill.userSkill(SKILL_GOUTOUJUNSHI);
            } else {
                active = ActiveSkill.userSkill(defaultName);
            }
            delivery = DeliveryMode.SINGLE_RESULT;
            spec = ResultSpec.passthroughSingle();
        }

        log.info("[SystemHost] function={} activeSkill={} skillType={} delivery={} expectedResults={}{}",
                function, active.name(), active.kind(), delivery, spec.expectedCount(),
                notice.isBlank() ? "" : (" notice=\"" + notice + "\""));
        return new PlannedExecution(function, active, delivery, spec, notice);
    }
}
