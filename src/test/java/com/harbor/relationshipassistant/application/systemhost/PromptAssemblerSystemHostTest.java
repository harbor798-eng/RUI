package com.harbor.relationshipassistant.application.systemhost;

import com.harbor.relationshipassistant.application.knowledge.KnowledgeResult;
import com.harbor.relationshipassistant.application.prompt.Prompt;
import com.harbor.relationshipassistant.application.prompt.PromptAssembler;
import com.harbor.relationshipassistant.application.skill.SkillDefinition;
import com.harbor.relationshipassistant.application.skill.SkillMetadata;
import com.harbor.relationshipassistant.application.skill.context.AnalysisContext;
import com.harbor.relationshipassistant.application.skill.context.AnalysisFunction;
import com.harbor.relationshipassistant.application.skill.context.AnalysisTask;
import com.harbor.relationshipassistant.application.skill.context.SkillExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 PlannedExecution 真正流入 PromptAssembler：
 * 组装出的 system prompt 必须包含 [SYSTEM_HOST] 块，
 * 且字段值来自 PlannedExecution 而不是旧 task.requestedSkillName。
 */
class PromptAssemblerSystemHostTest {

    private SkillExecutionContext ctx(AnalysisFunction fn, PlannedExecution plan, String fallbackSkillName) {
        AnalysisContext analysisCtx = new AnalysisContext(null, null, Collections.emptyList(), null, Collections.emptyList(), null);
        AnalysisTask task = new AnalysisTask(fn, fallbackSkillName, analysisCtx, false, plan);
        SkillDefinition skill = new SkillDefinition(new SkillMetadata(plan.activeSkill().name(), ""), "");
        return new SkillExecutionContext(task, analysisCtx, skill, plan);
    }

    @Test
    void systemHostBlockEmittedForDefaultThreeStrategy() {
        PlannedExecution plan = new PlannedExecution(
                AnalysisFunction.QUICK_REPLY,
                ActiveSkill.systemDefaultThreeStrategy(),
                DeliveryMode.THREE_BY_THREE);
        PromptAssembler asm = new PromptAssembler();
        Prompt p = asm.assemble(ctx(AnalysisFunction.QUICK_REPLY, plan, "quick-reply"),
                KnowledgeResult.disabled());
        String sys = p.getSystemPrompt();
        assertTrue(sys.contains("[SYSTEM_HOST]"), "must contain [SYSTEM_HOST] block");
        assertTrue(sys.contains("function: QUICK_REPLY"));
        assertTrue(sys.contains("activeSkill: default-three-strategy"));
        assertTrue(sys.contains("skillKind: SYSTEM"));
        assertTrue(sys.contains("deliveryMode: THREE_BY_THREE"));
    }

    @Test
    void systemHostBlockEmittedForUserSkill() {
        PlannedExecution plan = new PlannedExecution(
                AnalysisFunction.QUICK_REPLY,
                ActiveSkill.userSkill("goutoujunshi"),
                DeliveryMode.SINGLE_RESULT);
        PromptAssembler asm = new PromptAssembler();
        Prompt p = asm.assemble(ctx(AnalysisFunction.QUICK_REPLY, plan, "goutoujunshi"),
                KnowledgeResult.disabled());
        String sys = p.getSystemPrompt();
        assertTrue(sys.contains("[SYSTEM_HOST]"));
        assertTrue(sys.contains("activeSkill: goutoujunshi"));
        assertTrue(sys.contains("skillKind: USER"));
        assertTrue(sys.contains("deliveryMode: SINGLE_RESULT"));
    }
}
