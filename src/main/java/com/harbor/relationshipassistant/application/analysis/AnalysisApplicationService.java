package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.application.knowledge.KnowledgeRequest;
import com.harbor.relationshipassistant.application.knowledge.KnowledgeResult;
import com.harbor.relationshipassistant.application.knowledge.KnowledgeRouter;
import com.harbor.relationshipassistant.application.llm.AnalysisResult;
import com.harbor.relationshipassistant.application.output.OutputRuntime;
import com.harbor.relationshipassistant.application.output.PlannedResult;
import com.harbor.relationshipassistant.application.output.RawModelOutput;
import com.harbor.relationshipassistant.application.output.ResultJsonParser;
import com.harbor.relationshipassistant.application.prompt.LLMRequest;
import com.harbor.relationshipassistant.application.prompt.Prompt;
import com.harbor.relationshipassistant.application.prompt.PromptAssembler;
import com.harbor.relationshipassistant.application.skill.SkillDescriptor;
import com.harbor.relationshipassistant.application.skill.SkillDefinition;
import com.harbor.relationshipassistant.application.skill.SkillMetadata;
import com.harbor.relationshipassistant.application.skill.SkillNotFoundException;
import com.harbor.relationshipassistant.application.skill.SkillRouter;
import com.harbor.relationshipassistant.application.skill.context.*;
import com.harbor.relationshipassistant.application.systemhost.PlannedExecution;
import com.harbor.relationshipassistant.application.systemhost.ActiveSkill;
import com.harbor.relationshipassistant.application.systemhost.BuiltInSkills;
import com.harbor.relationshipassistant.application.systemhost.ResultSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * AI 分析应用层编排器：把一次 AnalysisTask 串成完整调用链。
 * 不访问 DB、不读聊天记录、不组 Prompt、不直接调 HTTP。
 *
 * <p>Stage 5-2：新增 {@link #executePlanned(AnalysisTask)} 返回权威 {@link PlannedResult}
 * （LLM JSON 已按 {@link ResultSpec} 解析成 {@code ResultItem[]}）。
 * 旧 {@link #execute(AnalysisTask)} 保留为兼容桥接，输出折叠成单字符串 {@link AnalysisResult}。</p>
 */
public final class AnalysisApplicationService {
    private static final Logger log = LoggerFactory.getLogger(AnalysisApplicationService.class);

    private final SkillRouter skillRouter;
    private final KnowledgeRouter knowledgeRouter;
    private final PromptAssembler promptAssembler;
    private final OutputRuntime outputRuntime;
    private final AnalysisResultValidator resultValidator;
    private final ResultJsonParser resultParser = new ResultJsonParser();

    public AnalysisApplicationService(SkillRouter skillRouter,
                                      KnowledgeRouter knowledgeRouter,
                                      PromptAssembler promptAssembler,
                                      OutputRuntime outputRuntime) {
        this(skillRouter, knowledgeRouter, promptAssembler, outputRuntime, new AnalysisResultValidator());
    }

    public AnalysisApplicationService(SkillRouter skillRouter,
                                      KnowledgeRouter knowledgeRouter,
                                      PromptAssembler promptAssembler,
                                      OutputRuntime outputRuntime,
                                      AnalysisResultValidator resultValidator) {
        this.skillRouter = skillRouter;
        this.knowledgeRouter = knowledgeRouter;
        this.promptAssembler = promptAssembler;
        this.outputRuntime = outputRuntime;
        this.resultValidator = resultValidator;
    }

    /** 兼容旧链路：执行 + 折叠成单字符串 AnalysisResult。新代码请用 {@link #executePlanned}。 */
    public AnalysisResult execute(AnalysisTask task) {
        PlannedResult planned = executePlanned(task);
        // 折叠：把 items 按 order 拼成一段纯文本，供旧 Adapter / JavaFX 消费。
        StringBuilder folded = new StringBuilder();
        for (var it : planned.items()) {
            if (!folded.isEmpty()) folded.append("\n\n");
            folded.append(it.text());
        }
        if (folded.isEmpty()) folded.append(planned.rawContent() == null ? "" : planned.rawContent());
        AnalysisResult result = new AnalysisResult(planned.function(), planned.skillName(),
                folded.toString(), planned.model(), planned.createdAt());
        return resultValidator.validate(result);
    }

    /**
     * Stage 5-2 权威执行路径：LLM 输出按 ResultSpec 解析成 ResultItem[]。
     */
    public PlannedResult executePlanned(AnalysisTask task) {
        validate(task);
        PlannedExecution plan = task.getPlannedExecution();
        log.info("[ANALYSIS] execute start function={} skill={} useKnowledge={} systemHostActive={}",
                task.getFunction(), task.getRequestedSkillName(), task.isUseKnowledge(),
                plan == null ? "(none)" : plan.activeSkill().name() + "/" + plan.activeSkill().kind());

        SkillDefinition skill;
        if (plan != null && plan.activeSkill().isSystem()) {
            // Built-in system skill: use RUI-provided SkillDefinition with real instructions.
            if (BuiltInSkills.DEFAULT_THREE_STRATEGY_NAME.equals(plan.activeSkill().name())) {
                skill = BuiltInSkills.defaultThreeStrategy();
                log.info("[ANALYSIS] built-in system skill: default-three-strategy (instructions loaded)");
            } else {
                ActiveSkill sys = plan.activeSkill();
                skill = new SkillDefinition(new SkillMetadata(sys.name(), "RUI built-in system skill"), "");
                log.info("[ANALYSIS] unknown system skill placeholder: {}", sys.name());
            }
        } else {
            Optional<SkillDescriptor> skillOpt = skillRouter.route(task.getRequestedSkillName());
            if (skillOpt.isEmpty()) {
                throw new SkillNotFoundException(task.getRequestedSkillName());
            }
            skill = skillOpt.get().getDefinition();
        }

        SkillExecutionContext exec = new SkillExecutionContext(task, task.getContext(), skill, plan);

        KnowledgeResult knowledge;
        if (task.isUseKnowledge()) {
            KnowledgeRequest kr = buildKnowledgeRequest(task);
            knowledge = knowledgeRouter.retrieve(task, kr);
            // Stage 8-4B: filter to user-selected document scope
            var scope = task.getKnowledgeScope();
            if (scope != null && !scope.isEmpty()) {
                var filtered = knowledge.getItems().stream()
                        .filter(it -> scope.contains(it.getId()))
                        .toList();
                knowledge = KnowledgeResult.of(kr.getQuery(), filtered);
                log.info("[ANALYSIS] knowledge scope filter: {} -> {}", knowledge.getItems().size(), filtered.size());
            }
        } else {
            knowledge = KnowledgeResult.disabled();
        }
        log.info("[ANALYSIS] knowledgeItems={}", knowledge.getItems().size());

        String legacyBlock = "";
        Prompt prompt = promptAssembler.assemble(exec, knowledge, legacyBlock);
        log.info("[ANALYSIS] promptLength system={} user={}",
                prompt.getSystemPrompt().length(), prompt.getUserPrompt().length());

        LLMRequest llmReq = LLMRequest.from(prompt);
        RawModelOutput raw = outputRuntime.generate(exec, llmReq);

        ResultSpec spec = plan != null ? plan.resultSpec() : ResultSpec.passthroughSingle();
        ResultJsonParser.ParseOutcome parsed = resultParser.parse(raw.getContent(), spec);
        log.info("[ANALYSIS] parsed items={}/{} degraded={}",
                parsed.items().size(), spec.expectedCount(), parsed.degraded());

        return new PlannedResult(
                task.getFunction(),
                raw.getSkillName(),
                spec,
                parsed.items(),
                parsed.degraded(),
                parsed.rawContent(),
                raw.getModel(),
                raw.getCreatedAt()
        );
    }

    private static void validate(AnalysisTask task) {
        if (task == null) throw new IllegalArgumentException("AnalysisTask is required");
        if (task.getFunction() == null) throw new IllegalArgumentException("AnalysisTask.function is required");
        if (task.getContext() == null) throw new IllegalArgumentException("AnalysisTask.context is required");
        if (task.getRequestedSkillName() == null || task.getRequestedSkillName().isBlank()) {
            throw new IllegalArgumentException("AnalysisTask.requestedSkillName is required");
        }
    }

    private KnowledgeRequest buildKnowledgeRequest(AnalysisTask task) {
        AnalysisContext ctx = task.getContext();
        StringBuilder sb = new StringBuilder();
        if (ctx.getRelationship() != null && ctx.getRelationship().getStage() != null) {
            sb.append(ctx.getRelationship().getStage()).append(' ');
            sb.append(stageToChinese(ctx.getRelationship().getStage())).append(' ');
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

    private static String stageToChinese(String stage) {
        return switch (stage) {
            case "INITIAL_CONTACT" -> "初识 暧昧 关系阶段";
            case "DATING" -> "约会 追求";
            case "DATING_EXCLUSIVE" -> "确定关系 恋爱";
            case "BROKEN_UP" -> "分手 修复";
            case "RECONTACT" -> "重新联系 复合";
            default -> "";
        };
    }
}
