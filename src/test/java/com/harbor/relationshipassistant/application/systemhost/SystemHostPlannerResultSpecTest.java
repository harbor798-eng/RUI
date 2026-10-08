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
 * Stage 5-2: Planner 必须按产品矩阵产出正确 ResultSpec。
 */
class SystemHostPlannerResultSpecTest {

    private final SystemHostPlanner planner = new SystemHostPlanner();

    private SkillResolution userSelected(String name) {
        SkillMetadata meta = new SkillMetadata(name, "");
        SkillDefinition def = new SkillDefinition(meta, "");
        SkillResources res = new SkillResources(Path.of("skills", name), null, null, null);
        return SkillResolution.userSelected(new SkillDescriptor(def, res));
    }

    @Test
    void defaultThreeStrategyQuickReply_has9SendableReplySlots() {
        PlannedExecution p = planner.plan(AnalysisFunction.QUICK_REPLY, SkillResolution.none());
        assertEquals(9, p.resultSpec().expectedCount());
        long sendable = p.resultSpec().slots().stream()
                .filter(s -> s.type() == ResultType.SENDABLE_REPLY && s.audience() == Audience.OTHER)
                .mapToInt(s -> s.count()).sum();
        assertEquals(9, sendable);
    }

    @Test
    void tongJinchengQuickReply_hasReplyPlusShortAnalysis() {
        PlannedExecution p = planner.plan(AnalysisFunction.QUICK_REPLY,
                userSelected("tong-jincheng-perspective"));
        assertEquals(2, p.resultSpec().expectedCount());
        assertTrue(p.resultSpec().slots().stream()
                .anyMatch(s -> s.type() == ResultType.SENDABLE_REPLY));
        assertTrue(p.resultSpec().slots().stream()
                .anyMatch(s -> s.type() == ResultType.SHORT_ANALYSIS));
    }

    @Test
    void goutoujunshiQuickReply_hasSingleSendable() {
        PlannedExecution p = planner.plan(AnalysisFunction.QUICK_REPLY, userSelected("goutoujunshi"));
        assertEquals(1, p.resultSpec().expectedCount());
        assertEquals(ResultType.SENDABLE_REPLY, p.resultSpec().slots().get(0).type());
    }

    @Test
    void goutoujunshiDetailed_hasAnalysisReport() {
        PlannedExecution p = planner.plan(AnalysisFunction.DETAILED_ANALYSIS, userSelected("goutoujunshi"));
        assertEquals(ResultType.ANALYSIS_REPORT, p.resultSpec().slots().get(0).type());
    }

    @Test
    void tongJinchengDetailed_hasShortAnalysis() {
        PlannedExecution p = planner.plan(AnalysisFunction.DETAILED_ANALYSIS,
                userSelected("tong-jincheng-perspective"));
        assertEquals(ResultType.SHORT_ANALYSIS, p.resultSpec().slots().get(0).type());
    }

    @Test
    void defaultThreeStrategyDetailed_routesToGoutoujunshi_withNotice() {
        PlannedExecution p = planner.plan(AnalysisFunction.DETAILED_ANALYSIS, SkillResolution.none());
        assertEquals("goutoujunshi", p.activeSkill().name());
        assertEquals(SkillKind.USER, p.activeSkill().kind());
        assertTrue(p.hasNotice());
        assertTrue(p.notice().contains("狗头军师"));
    }
}
