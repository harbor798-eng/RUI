package com.harbor.relationshipassistant.stage2;

import com.harbor.relationshipassistant.application.llm.AiProviderLLMClient;
import com.harbor.relationshipassistant.application.llm.LLMClient;
import com.harbor.relationshipassistant.application.output.OutputRuntime;
import com.harbor.relationshipassistant.application.output.RawModelOutput;
import com.harbor.relationshipassistant.application.output.ResultItem;
import com.harbor.relationshipassistant.application.output.ResultJsonParser;
import com.harbor.relationshipassistant.application.prompt.LLMRequest;
import com.harbor.relationshipassistant.application.prompt.Prompt;
import com.harbor.relationshipassistant.application.prompt.PromptAssembler;
import com.harbor.relationshipassistant.application.skill.DefaultSkillLoader;
import com.harbor.relationshipassistant.application.skill.SkillDescriptor;
import com.harbor.relationshipassistant.application.skill.SkillManager;
import com.harbor.relationshipassistant.application.skill.SkillRouter;
import com.harbor.relationshipassistant.application.skill.adapter.SkillResolution;
import com.harbor.relationshipassistant.application.skill.context.AnalysisContext;
import com.harbor.relationshipassistant.application.skill.context.AnalysisFunction;
import com.harbor.relationshipassistant.application.skill.context.AnalysisTask;
import com.harbor.relationshipassistant.application.skill.context.ChatMessageContext;
import com.harbor.relationshipassistant.application.skill.context.MessageSender;
import com.harbor.relationshipassistant.application.skill.context.SkillExecutionContext;
import com.harbor.relationshipassistant.application.skill.context.UserConfirmedFact;
import com.harbor.relationshipassistant.application.systemhost.PlannedExecution;
import com.harbor.relationshipassistant.application.systemhost.SystemHostPlanner;
import com.harbor.relationshipassistant.common.config.AppConfig;
import com.harbor.relationshipassistant.infrastructure.ai.AIProvider;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Stage7Harness {
    public static void main(String[] args) throws Exception {
        AppConfig cfg = new AppConfig();
        var ds = new com.harbor.relationshipassistant.infrastructure.persistence.DataSourceFactory(cfg);
        var crypto = new com.harbor.relationshipassistant.infrastructure.security.AesCryptoService(cfg.aesKey());
        var aiCfg = new com.harbor.relationshipassistant.application.ai.AiConfigService(ds, crypto);
        AIProvider provider = aiCfg.buildProvider();
        System.out.println("[Stage7] provider=" + provider.getProviderName());

        LLMClient client = new AiProviderLLMClient(provider);
        OutputRuntime outputRuntime = new OutputRuntime(client);
        PromptAssembler assembler = new PromptAssembler();
        SystemHostPlanner planner = new SystemHostPlanner();
        ResultJsonParser parser = new ResultJsonParser();

        SkillManager skillManager = new SkillManager(Path.of("skills"), new DefaultSkillLoader());
        skillManager.initialize();
        SkillRouter skillRouter = new SkillRouter(skillManager);

        List<ChatMessageContext> msgs = new ArrayList<>();
        msgs.add(new ChatMessageContext(null, MessageSender.OTHER,
                "周末有空吗？最近发现一家咖啡店。", LocalDateTime.now().minusMinutes(5)));
        msgs.add(new ChatMessageContext(null, MessageSender.ME,
                "我周末要加班，可能去不了。", LocalDateTime.now().minusMinutes(3)));
        msgs.add(new ChatMessageContext(null, MessageSender.OTHER,
                "哦，那下次吧。", LocalDateTime.now().minusMinutes(2)));

        AnalysisContext analysisCtx = new AnalysisContext(
                null, null, msgs, msgs.get(msgs.size() - 1),
                List.of(new UserConfirmedFact("user intent: 礼貌拒绝",
                        LocalDateTime.now().minusMinutes(10), "user")),
                null);

        Optional<SkillDescriptor> sdOpt = skillRouter.route("goutoujunshi");
        if (sdOpt.isEmpty()) throw new IllegalStateException("goutoujunshi not found");
        SkillResolution resolution = SkillResolution.userSelected(sdOpt.get());
        PlannedExecution plan = planner.plan(AnalysisFunction.DETAILED_ANALYSIS, resolution);
        System.out.println("[Stage7] activeSkill=" + plan.activeSkill().name()
                + " kind=" + plan.activeSkill().kind()
                + " expectedCount=" + plan.resultSpec().expectedCount());

        AnalysisTask task = new AnalysisTask(AnalysisFunction.DETAILED_ANALYSIS, "goutoujunshi", analysisCtx, false, plan);
        SkillExecutionContext exec = new SkillExecutionContext(task, analysisCtx, sdOpt.get().getDefinition(), plan);
        Prompt prompt = assembler.assemble(exec, com.harbor.relationshipassistant.application.knowledge.KnowledgeResult.disabled());
        LLMRequest llmReq = LLMRequest.from(prompt);
        RawModelOutput raw = outputRuntime.generate(exec, llmReq);

        ResultJsonParser.ParseOutcome parsed = parser.parse(raw.getContent(), plan.resultSpec());        System.out.println("[Stage7] items=" + parsed.items().size() + " degraded=" + parsed.degraded());
        for (ResultItem it : parsed.items()) {
            System.out.println("[Stage7] type=" + it.type() + " hasPayload=" + (it.payload() != null));
            JsonNode p = it.payload();
            if (p != null) {
                System.out.println("[Stage7] summary=" + p.path("summary").asText(""));
                System.out.println("[Stage7] facts count=" + p.path("facts").size());
                System.out.println("[Stage7] inferences count=" + p.path("inferences").size());
                System.out.println("[Stage7] possibilities count=" + p.path("possibilities").size());
                System.out.println("[Stage7] rec.action=" + p.path("recommendation").path("action").asText(""));
                System.out.println("[Stage7] emotions.me=" + p.path("emotions").path("me").size()
                        + " emotions.other=" + p.path("emotions").path("other").size());
                // Verify adapter produces structured result, not raw JSON
                com.harbor.relationshipassistant.application.llm.AnalysisResult legacy =
                        new com.harbor.relationshipassistant.application.llm.AnalysisResult(
                                com.harbor.relationshipassistant.application.skill.context.AnalysisFunction.DETAILED_ANALYSIS,
                                "goutoujunshi", p.toString(), "deepseek-chat", null);
                var adapted = new com.harbor.relationshipassistant.application.analysis.DetailedAnalysisAdapter().adapt(legacy);
                System.out.println("[Stage7] adapted.summaryLen=" + adapted.summary().length());
                System.out.println("[Stage7] adapted.reportMarkdownLen=" + adapted.reportMarkdown().length());
                System.out.println("[Stage7] adapted.facts=" + adapted.facts().size());
                System.out.println("[Stage7] adapted.meEmotions=" + adapted.meEmotions().size());
            }
        }
    }
}
