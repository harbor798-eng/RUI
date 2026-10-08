package com.harbor.relationshipassistant.stage2;

import com.harbor.relationshipassistant.application.ai.AiConfigService;
import com.harbor.relationshipassistant.application.knowledge.KnowledgeResult;
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
import com.harbor.relationshipassistant.application.systemhost.BuiltInSkills;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 6-2A: Default Three Strategy 行为基线测试。
 * 12 个场景 × 1 次 LLM 调用 = 12 次，每次 9 条 SENDABLE_REPLY。
 */
public final class Stage6Harness {

    private static final Path OUT = Path.of("DEL", "stage6-2A-default-three-strategy-baseline.md");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private record Scenario(String id, String other, String me, String note) {}

    public static void main(String[] args) throws Exception {
        System.out.println("[Stage6] starting baseline test");

        AppConfig cfg = new AppConfig();
        var ds = new com.harbor.relationshipassistant.infrastructure.persistence.DataSourceFactory(cfg);
        var crypto = new com.harbor.relationshipassistant.infrastructure.security.AesCryptoService(cfg.aesKey());
        var aiCfg = new AiConfigService(ds, crypto);
        AIProvider provider = aiCfg.buildProvider();
        System.out.println("[Stage6] provider=" + provider.getProviderName());

        LLMClient client = new AiProviderLLMClient(provider);
        OutputRuntime outputRuntime = new OutputRuntime(client);
        PromptAssembler assembler = new PromptAssembler();
        SystemHostPlanner planner = new SystemHostPlanner();
        ResultJsonParser parser = new ResultJsonParser();

        SkillManager skillManager = new SkillManager(Path.of("skills"), new DefaultSkillLoader());
        skillManager.initialize();

        List<Scenario> scenarios = List.of(
                new Scenario("01", "今天过得怎么样？", "正常回复，聊下去就行。", "普通问候"),
                new Scenario("02", "你在干嘛呀？", "正常回复，不想太刻意。", "日常状态"),
                new Scenario("03", "我周末发现一家挺不错的店，要不要一起去？", "我愿意去，希望回复稍微积极一点。", "主动邀约"),
                new Scenario("04", "嗯，行吧。", "正常接住，不要过度热情。", "对方回复比较冷"),
                new Scenario("05", "你怎么每次都这么会说话呀。", "可以稍微暧昧一点，但不要油腻。", "明显暧昧"),
                new Scenario("06", "你是不是偷偷练过怎么哄人？", "轻松接这个玩笑。", "对方开玩笑"),
                new Scenario("07", "感觉跟你聊天还挺舒服的。", "自然回应，不要突然表白。", "对方主动夸奖"),
                new Scenario("08", "哈哈哈我也不知道怎么说。", "帮我把聊天继续下去。", "不知道怎么接"),
                new Scenario("09", "周末有空吗？最近发现一家咖啡店。", "我只想礼貌回复一句，不想主动推进关系。", "用户明确要求礼貌"),
                new Scenario("10", "你周末有空吗？", "想稍微暧昧一点，但不要太过。", "用户明确要求暧昧"),
                new Scenario("11", "哦。", "帮我自然回复。", "上下文信息非常少"),
                new Scenario("12", "最近真的有点累，什么都不太想做。", "先关心一下对方，不要急着调情。", "对方明显表达情绪")
        );

        Files.createDirectories(OUT.getParent());
        StringBuilder md = new StringBuilder();
        md.append("# Stage 6-2A Default Three Strategy Behavior Baseline\n\n");
        md.append("- Timestamp: ").append(LocalDateTime.now().format(TS)).append("\n");
        md.append("- Model: deepseek-chat (from DB ai_provider_config)\n");
        md.append("- Function: QUICK_REPLY\n");
        md.append("- ActiveSkill: default-three-strategy (SYSTEM, Built-in)\n");
        md.append("- ResultSpec: 9 × SENDABLE_REPLY (NATURAL/PROACTIVE/LIGHT_FLIRT ×3)\n\n");

        int total = 0, ok = 0, failed = 0;
        int totalItems = 0;
        int degradedCount = 0;

        for (Scenario sc : scenarios) {
            total++;
            md.append("## Scenario ").append(sc.id()).append(" — ").append(sc.note()).append("\n\n");
            md.append("- OTHER: ").append(sc.other()).append("\n");
            md.append("- ME: ").append(sc.me()).append("\n\n");
            try {
                List<ChatMessageContext> msgs = new ArrayList<>();
                msgs.add(new ChatMessageContext(null, MessageSender.OTHER, sc.other(), LocalDateTime.now().minusMinutes(2)));
                msgs.add(new ChatMessageContext(null, MessageSender.ME, sc.me(), LocalDateTime.now().minusMinutes(1)));
                AnalysisContext analysisCtx = new AnalysisContext(
                        null, null, msgs, msgs.get(msgs.size() - 1),
                        List.of(new UserConfirmedFact("user intent: " + sc.me(),
                                LocalDateTime.now().minusMinutes(5), "user")),
                        null);

                SkillResolution resolution = SkillResolution.none();
                PlannedExecution plan = planner.plan(AnalysisFunction.QUICK_REPLY, resolution);
                AnalysisTask task = new AnalysisTask(AnalysisFunction.QUICK_REPLY, "default-three-strategy", analysisCtx, false, plan);
                SkillExecutionContext exec = new SkillExecutionContext(task, analysisCtx,
                        BuiltInSkills.defaultThreeStrategy(), plan);

                Prompt prompt = assembler.assemble(exec, KnowledgeResult.disabled());
                LLMRequest llmReq = LLMRequest.from(prompt);
                RawModelOutput raw = outputRuntime.generate(exec, llmReq);
                ResultJsonParser.ParseOutcome parsed = parser.parse(raw.getContent(), plan.resultSpec());

                md.append("- activeSkill: ").append(plan.activeSkill().name()).append(" (").append(plan.activeSkill().kind()).append(")\n");
                md.append("- parsedItems: ").append(parsed.items().size()).append("\n");
                md.append("- parseDegraded: ").append(parsed.degraded()).append("\n\n");

                // Group by strategyKey
                Map<String, List<ResultItem>> byStrategy = new LinkedHashMap<>();
                byStrategy.put("NATURAL", new ArrayList<>());
                byStrategy.put("PROACTIVE", new ArrayList<>());
                byStrategy.put("LIGHT_FLIRT", new ArrayList<>());
                for (ResultItem it : parsed.items()) {
                    String key = it.strategyKey() == null ? "?" : it.strategyKey();
                    byStrategy.computeIfAbsent(key, k -> new ArrayList<>()).add(it);
                }
                for (Map.Entry<String, List<ResultItem>> e : byStrategy.entrySet()) {
                    md.append("### ").append(e.getKey()).append("\n\n");
                    int i = 1;
                    for (ResultItem it : e.getValue()) {
                        md.append(i++).append(". ").append(it.text()).append("\n\n");
                    }
                }
                md.append("<details><summary>Raw LLM output</summary>\n\n```\n")
                  .append(raw.getContent()).append("\n```\n\n</details>\n\n---\n\n");

                ok++;
                totalItems += parsed.items().size();
                if (parsed.degraded()) degradedCount++;
                System.out.println("[Stage6] OK " + sc.id() + " items=" + parsed.items().size() + " degraded=" + parsed.degraded());
            } catch (Exception e) {
                md.append("**ERROR:** ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append("\n\n---\n\n");
                failed++;
                System.err.println("[Stage6] FAIL " + sc.id() + " : " + e.getMessage());
            }
        }

        md.append("## Summary\n\n");
        md.append("- Total scenarios: ").append(total).append("\n");
        md.append("- Success: ").append(ok).append("\n");
        md.append("- Failed: ").append(failed).append("\n");
        md.append("- Total SENDABLE_REPLY items: ").append(totalItems).append("\n");
        md.append("- Scenarios with parseDegraded=true: ").append(degradedCount).append("\n");

        Files.writeString(OUT, md.toString(), StandardCharsets.UTF_8);
        System.out.println("[Stage6] DONE total=" + total + " ok=" + ok + " failed=" + failed + " items=" + totalItems);
    }
}
