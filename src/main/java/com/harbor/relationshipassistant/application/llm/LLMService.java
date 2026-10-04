package com.harbor.relationshipassistant.application.llm;

import com.harbor.relationshipassistant.application.prompt.LLMRequest;
import com.harbor.relationshipassistant.application.skill.context.AnalysisTask;
import com.harbor.relationshipassistant.application.skill.context.SkillExecutionContext;

import java.time.LocalDateTime;

/**
 * 协调一次 LLM 调用：LLMRequest → LLMClient → LLMResponse → AnalysisResult。
 * 不调用 SkillRouter/KnowledgeRouter，不重新组装 Prompt，不访问 Repository。
 */
public final class LLMService {
    private final LLMClient client;

    public LLMService(LLMClient client) {
        this.client = client;
    }

    public AnalysisResult execute(AnalysisTask task, SkillExecutionContext exec, LLMRequest request) {
        if (task == null) throw new LLMException("AnalysisTask is required");
        if (exec == null || exec.getSkill() == null) throw new LLMException("SkillExecutionContext with skill is required");
        if (request == null) throw new LLMException("LLMRequest is required");

        LLMResponse resp = client.complete(request);
        String skillName = exec.getSkill().getMetadata() == null ? null : exec.getSkill().getMetadata().getName();
        return new AnalysisResult(task.getFunction(), skillName, resp.content(), resp.model(), LocalDateTime.now());
    }
}
