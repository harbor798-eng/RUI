package com.harbor.relationshipassistant.application.systemhost;

import com.harbor.relationshipassistant.application.skill.SkillDescriptor;
import com.harbor.relationshipassistant.application.skill.SkillDefinition;
import com.harbor.relationshipassistant.application.skill.SkillMetadata;
import com.harbor.relationshipassistant.application.skill.SkillResources;
import com.harbor.relationshipassistant.application.skill.adapter.SkillResolution;
import com.harbor.relationshipassistant.application.skill.context.AnalysisFunction;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SystemHostPlanner 最小验证：4 个 Active Skill × Function 组合的 Delivery 判定。
 */
class SystemHostPlannerTest {

    private final SystemHostPlanner planner = new SystemHostPlanner();

    private SkillResolution userSelected(String name) {
        SkillMetadata meta = new SkillMetadata(name, "");
        SkillDefinition def = new SkillDefinition(meta, "");
        SkillResources res = new SkillResources(Path.of("skills", name), null, null, null);
        return SkillResolution.userSelected(new SkillDescriptor(def, res));
    }

    @Test
    void defaultThreeStrategyPlusQuickReply_isThreeByThree() {
        PlannedExecution p = planner.plan(AnalysisFunction.QUICK_REPLY, SkillResolution.none());
        assertEquals(AnalysisFunction.QUICK_REPLY, p.function());
        assertEquals(ActiveSkill.DEFAULT_THREE_STRATEGY_NAME, p.activeSkill().name());
        assertEquals(SkillKind.SYSTEM, p.activeSkill().kind());
        assertEquals(DeliveryMode.THREE_BY_THREE, p.deliveryMode());
    }

    @Test
    void goutoujunshiPlusQuickReply_isSingleResult() {
        PlannedExecution p = planner.plan(AnalysisFunction.QUICK_REPLY, userSelected("goutoujunshi"));
        assertEquals("goutoujunshi", p.activeSkill().name());
        assertEquals(SkillKind.USER, p.activeSkill().kind());
        assertEquals(DeliveryMode.SINGLE_RESULT, p.deliveryMode());
    }

    @Test
    void quickReplySkillPlusQuickReply_isSingleResult() {
        PlannedExecution p = planner.plan(AnalysisFunction.QUICK_REPLY, userSelected("quick-reply"));
        assertEquals("quick-reply", p.activeSkill().name());
        assertEquals(SkillKind.USER, p.activeSkill().kind());
        assertEquals(DeliveryMode.SINGLE_RESULT, p.deliveryMode());
    }

    @Test
    void tongJinchengPlusQuickReply_isSingleResult() {
        PlannedExecution p = planner.plan(AnalysisFunction.QUICK_REPLY, userSelected("tong-jincheng-perspective"));
        assertEquals("tong-jincheng-perspective", p.activeSkill().name());
        assertEquals(SkillKind.USER, p.activeSkill().kind());
        assertEquals(DeliveryMode.SINGLE_RESULT, p.deliveryMode());
    }

    @Test
    void defaultThreeStrategyPlusDetailedAnalysis_notThreeByThree() {
        PlannedExecution p = planner.plan(AnalysisFunction.DETAILED_ANALYSIS, SkillResolution.none());
        assertEquals(DeliveryMode.SINGLE_RESULT, p.deliveryMode());
    }

    @Test
    void userSkillPlusDetailedAnalysis_notThreeByThree() {
        PlannedExecution p = planner.plan(AnalysisFunction.DETAILED_ANALYSIS, userSelected("goutoujunshi"));
        assertEquals("goutoujunshi", p.activeSkill().name());
        assertEquals(SkillKind.USER, p.activeSkill().kind());
        assertEquals(DeliveryMode.SINGLE_RESULT, p.deliveryMode());
    }
}
