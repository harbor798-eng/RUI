package com.harbor.relationshipassistant.stage2;

import com.harbor.relationshipassistant.application.llm.AiProviderLLMClient;
import com.harbor.relationshipassistant.application.llm.LLMClient;
import com.harbor.relationshipassistant.application.knowledge.KnowledgeResult;
import com.harbor.relationshipassistant.application.output.OutputRuntime;
import com.harbor.relationshipassistant.application.output.RawModelOutput;
import com.harbor.relationshipassistant.application.output.ResultItem;
import com.harbor.relationshipassistant.application.output.ResultJsonParser;
import com.harbor.relationshipassistant.application.prompt.LLMRequest;
import com.harbor.relationshipassistant.application.prompt.Prompt;
import com.harbor.relationshipassistant.application.prompt.PromptAssembler;
import com.harbor.relationshipassistant.application.skill.SkillDescriptor;
import com.harbor.relationshipassistant.application.skill.SkillManager;
import com.harbor.relationshipassistant.application.skill.DefaultSkillLoader;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stage 5-2: Result Contract 真实 LLM 集成验证。
 * 跑 5 个产品矩阵组合，验证 JSON 解析后 items[] 的形状。
 */
public final class Stage5Harness {

    private static final Path OUT = Path.of("DEL", "stage5-result-contract-results.md");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private record Case(String id, AnalysisFunction function, String skillName,
                        String expectation) {}

    public static void main(String[] args) throws Exception {
        System.out.println("[Stage5] starting");

        AppConfig cfg = new AppConfig();
        var ds = new com.harbor.relationshipassistant.infrastructure.persistence.DataSourceFactory(cfg);
        var crypto = new com.harbor.relationshipassistant.infrastructure.security.AesCryptoService(cfg.aesKey());
        var aiCfg = new com.harbor.relationshipassistant.application.ai.AiConfigService(ds, crypto);
        AIProvider provider = aiCfg.buildProvider();
        System.out.println("[Stage5] provider=" + provider.getProviderName());

        LLMClient client = new AiProviderLLMClient(provider);
        OutputRuntime outputRuntime = new OutputRuntime(client);
        PromptAssembler assembler = new PromptAssembler();
        SystemHostPlanner planner = new SystemHostPlanner();
        ResultJsonParser parser = new ResultJsonParser();

        SkillManager skillManager = new SkillManager(Path.of("skills"), new DefaultSkillLoader());
        skillManager.initialize();
        SkillRouter skillRouter = new SkillRouter(skillManager);

        List<Case> cases = List.of(
                new Case("QR+default-three-strategy", AnalysisFunction.QUICK_REPLY, null,
                        "expect 9 SENDABLE_REPLY (NATURAL/PROACTIVE/LIGHT_FLIRT ×3)"),
                new Case("QR+goutoujunshi", AnalysisFunction.QUICK_REPLY, "goutoujunshi",
                        "expect 1 SENDABLE_REPLY"),
                new Case("QR+tong-jincheng", AnalysisFunction.QUICK_REPLY, "tong-jincheng-perspective",
                        "expect 1 SENDABLE_REPLY + 1 SHORT_ANALYSIS"),
                new Case("DA+goutoujunshi", AnalysisFunction.DETAILED_ANALYSIS, "goutoujunshi",
                        "expect 1 ANALYSIS_REPORT"),
                new Case("DA+tong-jincheng", AnalysisFunction.DETAILED_ANALYSIS, "tong-jincheng-perspective",
                        "expect 1 SHORT_ANALYSIS")
        );

        Files.createDirectories(OUT.getParent());
        StringBuilder md = new StringBuilder();
        md.append("# Stage 5-2 Result Contract Real LLM Validation\n\n");
        md.append("- Timestamp: ").append(LocalDateTime.now().format(TS)).append("\n");
        md.append("- Model: deepseek-chat (from DB ai_provider_config)\n");
        md.append("- Fixed context: OTHER=\"她问你周末有空吗，说最近新发现一家咖啡店。\" + ME=user wants a polite reply\n\n");

        int total = 0, ok = 0, failed = 0;

        for (Case c : cases) {
            total++;
            md.append("## ").append(c.id()).append("\n\n");
            md.append("- Expected: ").append(c.expectation()).append("\n\n");
            try {
                List<ChatMessageContext> msgs = new ArrayList<>();
                msgs.add(new ChatMessageContext(null, MessageSender.OTHER,
                        "她问你周末有空吗，说最近新发现一家咖啡店。", LocalDateTime.now().minusMinutes(2)));
                msgs.add(new ChatMessageContext(null, MessageSender.ME,
                        "我想礼貌回复一句。", LocalDateTime.now().minusMinutes(1)));
                AnalysisContext analysisCtx = new AnalysisContext(
                        null, null, msgs, msgs.get(msgs.size() - 1),
                        List.of(new UserConfirmedFact("user intent: polite reply",
                                LocalDateTime.now().minusMinutes(5), "user")),
                        null);

                SkillResolution resolution;
                String requestedSkill;
                if (c.skillName() == null) {
                    resolution = SkillResolution.none();
                    requestedSkill = "default-three-strategy";
                } else {
                    Optional<SkillDescriptor> sdOpt = skillRouter.route(c.skillName());
                    if (sdOpt.isEmpty()) throw new IllegalStateException("skill not found: " + c.skillName());
                    resolution = SkillResolution.userSelected(sdOpt.get());
                    requestedSkill = c.skillName();
                }
                PlannedExecution plan = planner.plan(c.function(), resolution);
                AnalysisTask task = new AnalysisTask(c.function(), requestedSkill, analysisCtx, false, plan);

                SkillDescriptor sd = (c.skillName() == null)
                        ? null
                        : skillRouter.route(c.skillName()).orElseThrow();
                var skillDef = sd == null
                        ? new com.harbor.relationshipassistant.application.skill.SkillDefinition(
                                new com.harbor.relationshipassistant.application.skill.SkillMetadata("default-three-strategy",""), "")
                        : sd.getDefinition();
                SkillExecutionContext exec = new SkillExecutionContext(task, analysisCtx, skillDef, plan);

                Prompt prompt = assembler.assemble(exec, KnowledgeResult.disabled());
                LLMRequest llmReq = LLMRequest.from(prompt);
                RawModelOutput raw = outputRuntime.generate(exec, llmReq);

                ResultJsonParser.ParseOutcome parsed = parser.parse(raw.getContent(), plan.resultSpec());

                md.append("- activeSkill: ").append(plan.activeSkill().name()).append("\n");
                md.append("- skillKind: ").append(plan.activeSkill().kind()).append("\n");
                md.append("- deliveryMode: ").append(plan.deliveryMode()).append("\n");
                md.append("- expectedCount: ").append(plan.resultSpec().expectedCount()).append("\n");
                md.append("- parsedItems: ").append(parsed.items().size()).append("\n");
                md.append("- parseDegraded: ").append(parsed.degraded()).append("\n");
                if (plan.hasNotice()) md.append("- notice: ").append(plan.notice()).append("\n");
                md.append("\n");

                md.append("### Parsed items\n\n");
                md.append("| # | type | audience | strategyKey | text |\n|---|---|---|---|---|\n");
                for (ResultItem it : parsed.items()) {
                    String t = it.text().replace("|","\\|").replace("\n"," ");
                    if (t.length() > 120) t = t.substring(0, 120) + "…";
                    md.append("| ").append(it.order())
                      .append(" | ").append(it.type())
                      .append(" | ").append(it.audience())
                      .append(" | ").append(it.strategyKey() == null ? "-" : it.strategyKey())
                      .append(" | ").append(t).append(" |\n");
                }
                md.append("\n<details><summary>Raw LLM output</summary>\n\n```\n")
                  .append(raw.getContent()).append("\n```\n\n</details>\n\n");
                ok++;
                System.out.println("[Stage5] OK " + c.id() + " items=" + parsed.items().size() + " degraded=" + parsed.degraded());
            } catch (Exception e) {
                md.append("**ERROR:** ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append("\n\n");
                failed++;
                System.err.println("[Stage5] FAIL " + c.id() + " : " + e.getMessage());
            }
        }

        md.append("---\n\nSummary: total=").append(total).append(" ok=").append(ok).append(" failed=").append(failed).append("\n");
        Files.writeString(OUT, md.toString(), StandardCharsets.UTF_8);
        System.out.println("[Stage5] DONE total=" + total + " ok=" + ok + " failed=" + failed);
    }
}
