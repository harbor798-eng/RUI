package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.application.knowledge.KnowledgeRequest;
import com.harbor.relationshipassistant.application.knowledge.KnowledgeResult;
import com.harbor.relationshipassistant.application.knowledge.KnowledgeRouter;
import com.harbor.relationshipassistant.application.llm.AnalysisResult;
import com.harbor.relationshipassistant.application.llm.LLMService;
import com.harbor.relationshipassistant.application.prompt.LLMRequest;
import com.harbor.relationshipassistant.application.prompt.Prompt;
import com.harbor.relationshipassistant.application.prompt.PromptAssembler;
import com.harbor.relationshipassistant.application.skill.SkillDescriptor;
import com.harbor.relationshipassistant.application.skill.SkillNotFoundException;
import com.harbor.relationshipassistant.application.skill.SkillRouter;
import com.harbor.relationshipassistant.application.skill.context.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * AI 分析应用层编排器：把一次 AnalysisTask 串成完整调用链。
 * 不访问 DB、不读聊天记录、不组 Prompt、不直接调 HTTP。
 */
public final class AnalysisApplicationService {
    private static final Logger log = LoggerFactory.getLogger(AnalysisApplicationService.class);

    private final SkillRouter skillRouter;
    private final KnowledgeRouter knowledgeRouter;
    private final PromptAssembler promptAssembler;
    private final LLMService llmService;
    private final AnalysisResultValidator resultValidator;

    public AnalysisApplicationService(SkillRouter skillRouter,
                                      KnowledgeRouter knowledgeRouter,
                                      PromptAssembler promptAssembler,
                                      LLMService llmService) {
        this(skillRouter, knowledgeRouter, promptAssembler, llmService, new AnalysisResultValidator());
    }

    public AnalysisApplicationService(SkillRouter skillRouter,
                                      KnowledgeRouter knowledgeRouter,
                                      PromptAssembler promptAssembler,
                                      LLMService llmService,
                                      AnalysisResultValidator resultValidator) {
        this.skillRouter = skillRouter;
        this.knowledgeRouter = knowledgeRouter;
        this.promptAssembler = promptAssembler;
        this.llmService = llmService;
        this.resultValidator = resultValidator;
    }

    public AnalysisResult execute(AnalysisTask task) {
        validate(task);
        log.info("[ANALYSIS] execute start function={} skill={} useKnowledge={}",
                task.getFunction(), task.getRequestedSkillName(), task.isUseKnowledge());

        // 1. Skill
        Optional<SkillDescriptor> skillOpt = skillRouter.route(task.getRequestedSkillName());
        if (skillOpt.isEmpty()) {
            throw new SkillNotFoundException(task.getRequestedSkillName());
        }
        SkillDescriptor skill = skillOpt.get();

        // 2. Execution context
        SkillExecutionContext exec = new SkillExecutionContext(task, task.getContext(), skill.getDefinition());

        // 3. Knowledge
        KnowledgeResult knowledge;
        if (task.isUseKnowledge()) {
            KnowledgeRequest kr = buildKnowledgeRequest(task);
            log.info("[ANALYSIS] knowledge query length={}", kr.getQuery() == null ? 0 : kr.getQuery().length());
            knowledge = knowledgeRouter.retrieve(task, kr);
        } else {
            knowledge = KnowledgeResult.disabled();
        }
        log.info("[ANALYSIS] knowledgeItems={}", knowledge.getItems().size());

        // 4. Prompt
        Prompt prompt = promptAssembler.assemble(exec, knowledge);
        log.info("[ANALYSIS] promptLength system={} user={}",
                prompt.getSystemPrompt().length(), prompt.getUserPrompt().length());

        // 5. LLM
        LLMRequest llmReq = LLMRequest.from(prompt);
        AnalysisResult result = llmService.execute(task, exec, llmReq);
        result = resultValidator.validate(result);
        log.info("[ANALYSIS] execute completed model={}", result.getModel());
        return result;
    }

    private static void validate(AnalysisTask task) {
        if (task == null) throw new IllegalArgumentException("AnalysisTask is required");
        if (task.getFunction() == null) throw new IllegalArgumentException("AnalysisTask.function is required");
        if (task.getContext() == null) throw new IllegalArgumentException("AnalysisTask.context is required");
        if (task.getRequestedSkillName() == null || task.getRequestedSkillName().isBlank()) {
            throw new IllegalArgumentException("AnalysisTask.requestedSkillName is required");
        }
    }

    /**
     * 最小可解释的 Knowledge query：关系阶段 + 当前消息 + 用户确认事实的简短拼接。
     * 不塞整个聊天记录，不序列化 Context，不使用 Skill instructions。
     */
    private KnowledgeRequest buildKnowledgeRequest(AnalysisTask task) {
        AnalysisContext ctx = task.getContext();
        StringBuilder sb = new StringBuilder();
        if (ctx.getRelationship() != null && ctx.getRelationship().getStage() != null) {
            sb.append(ctx.getRelationship().getStage()).append(' ');
        }
        if (ctx.getCurrentMessage() != null && ctx.getCurrentMessage().getContent() != null) {
            sb.append(ctx.getCurrentMessage().getContent()).append(' ');
        }
        if (ctx.getUserConfirmedFacts() != null && !ctx.getUserConfirmedFacts().isEmpty()) {
            UserConfirmedFact f = ctx.getUserConfirmedFacts().get(0);
            if (f.getContent() != null) sb.append(f.getContent());
        }
        return new KnowledgeRequest(sb.toString().trim(), 3, null);
    }
}
