package com.harbor.relationshipassistant.application.distill;

import com.harbor.relationshipassistant.infrastructure.ai.AIProvider;
import com.harbor.relationshipassistant.infrastructure.ai.AIRequest;
import com.harbor.relationshipassistant.infrastructure.ai.AIResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 前任 Skill 蒸馏：忠实按 ex-skill-main 的 4 阶段流程编排 LLM 调用。
 *
 *   S1: memories_analyzer   → 共同记忆分析
 *   S2: persona_analyzer    → 人物性格分析（含标签翻译）
 *   S3: memories_builder    → 生成 memories.md
 *   S4: persona_builder     → 生成 persona.md（Layer 0-5）
 *
 * 然后按原版 SKILL.md Step 5 模板拼装 SKILL.md，原子发布到 ./skills/ex_{slug}/。
 */
public class ExDistillService {
    private static final Logger log = LoggerFactory.getLogger(ExDistillService.class);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final AIProvider ai;
    private final ExMaterialSource materialSource;
    private final ExSkillPublisher publisher;

    public ExDistillService(AIProvider ai, ExMaterialSource materialSource, ExSkillPublisher publisher) {
        this.ai = ai;
        this.materialSource = materialSource;
        this.publisher = publisher;
    }

    public record ExRequest(long relationshipId, String nickname,
                            String relationshipDesc, String personalityTags,
                            List<ExMaterialInput> materials) {
        /** 兼容旧调用：单一微信文件路径。 */
        public static ExRequest withWechatFile(long relId, String nick, String relDesc, String tags, Path wf) {
            if (wf == null) {
                return new ExRequest(relId, nick, relDesc, tags, List.of(ExMaterialInput.db()));
            }
            return new ExRequest(relId, nick, relDesc, tags,
                    List.of(new ExMaterialInput("WECHAT_FILE", wf.toString())));
        }
    }

    public record ExResult(boolean success, String slug, String skillDir, String message,
                           int materialCount, LocalDateTime materialFrom, LocalDateTime materialTo) {}

    public ExResult distill(ExRequest req) {
        try {
            // 1. 收集原材料
            ExMaterialSource.Material material = selectMaterial(req);

            String slug = ExSlugGenerator.generate(req.nickname(), req.relationshipId());
            log.info("[EX-DISTILL] start slug={} nickname={} materialCount={}",
                    slug, req.nickname(), material.messageCount());

            // 2. 4 阶段 LLM 调用
            String memoriesAnalysis = callStage("prompts/memories_analyzer.md",
                    buildAnalyzerUserPrompt(req, material.text()));
            String personaAnalysis = callStage("prompts/persona_analyzer.md",
                    buildPersonaAnalyzerUserPrompt(req, material.text()));
            String memoriesMd = callStage("prompts/memories_builder.md",
                    buildBuilderUserPrompt(req, memoriesAnalysis));
            String personaMd = callStage("prompts/persona_builder.md",
                    buildBuilderUserPrompt(req, personaAnalysis));

            // 3. 拼装 SKILL.md（按原版 Step 5 模板）
            String skillMd = buildExSkillMarkdown(slug, req.nickname(), req.relationshipDesc(),
                    memoriesMd, personaMd);
            String metaJson = buildMetaJson(slug, req);

            // 4. 原子发布
            ExSkillPublisher.Payload payload = new ExSkillPublisher.Payload(
                    skillMd, memoriesMd, personaMd, metaJson);
            Path dir = publisher.publish(slug, payload);

            return new ExResult(true, slug, dir.toString(),
                    "前任 Skill 已生成: " + slug,
                    material.messageCount(), material.from(), material.to());
        } catch (Exception e) {
            log.error("[EX-DISTILL] failed", e);
            return new ExResult(false, null, null, e.getMessage(), 0, null, null);
        }
    }

    // ---------- 材料来源选择（package-private 便于测试） ----------

    ExMaterialSource.Material selectMaterial(ExRequest req) throws Exception {
        log.info("[EX-DISTILL] materials={}", req.materials());
        return materialSource.collect(req.relationshipId(), req.nickname(), req.materials());
    }

    // ---------- Prompt 加载与调用 ----------

    private String loadPrompt(String resourcePath) {
        try (InputStream in = getClass().getResourceAsStream("/exskill/" + resourcePath)) {
            if (in == null) throw new IllegalStateException("缺少资源: " + resourcePath);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取 prompt 失败: " + resourcePath, e);
        }
    }

    private String callStage(String promptResource, String userContent) {
        String system = loadPrompt(promptResource);
        AIRequest req = AIRequest.of(system, List.of(new AIRequest.Turn("user", userContent)));
        req.setTemperature(0.5);
        req.setMaxTokens(2500);
        log.info("[EX-DISTILL] calling stage={} userChars={}", promptResource, userContent.length());
        AIResponse resp = ai.generate(req);
        return resp.text();
    }

    private String buildAnalyzerUserPrompt(ExRequest req, String material) {
        return "人物昵称：" + req.nickname() + "\n\n"
                + "原材料（聊天记录）：\n" + material;
    }

    private String buildPersonaAnalyzerUserPrompt(ExRequest req, String material) {
        return "人物昵称：" + req.nickname() + "\n"
                + "关系描述（用户手动填写）：" + (req.relationshipDesc() == null ? "（未填）" : req.relationshipDesc()) + "\n"
                + "性格标签（用户手动填写）：" + (req.personalityTags() == null ? "（未填）" : req.personalityTags()) + "\n\n"
                + "原材料（聊天记录）：\n" + material;
    }

    private String buildBuilderUserPrompt(ExRequest req, String analysisResult) {
        return "人物昵称：" + req.nickname() + "\n\n"
                + "以下是分析结果，请按模板生成最终文件：\n\n" + analysisResult;
    }

    // ---------- 原版 SKILL.md Step 5 模板 ----------

    String buildExSkillMarkdown(String slug, String name, String relationshipDesc,
                                String memoriesMd, String personaMd) {
        String identity = relationshipDesc == null || relationshipDesc.isBlank()
                ? name + " 的人物画像"
                : name + "，" + relationshipDesc;
        return """
                ---
                name: ex_%s
                description: %s
                user-invocable: true
                ---

                # %s

                %s

                ---

                ## PART A：共同记忆

                %s

                ---

                ## PART B：人物性格

                %s

                ---

                ## 运行规则

                接收到任何消息时：

                1. **先由 PART B 判断**：她会不会回这条消息？用什么心情和态度回？
                2. **再由 PART A 提供记忆**：相关的共同记忆、日常细节、重要时刻
                3. **输出时保持 PART B 的表达风格**：她说话的方式、用词习惯、emoji 偏好

                **PART B 的 Layer 0 规则永远优先，任何情况下不得违背。**
                """.formatted(slug, identity, name, identity, memoriesMd.trim(), personaMd.trim());
    }

    private String buildMetaJson(String slug, ExRequest req) {
        String now = LocalDateTime.now().format(ISO);
        return """
                {
                  "name": "%s",
                  "slug": "%s",
                  "created_at": "%s",
                  "updated_at": "%s",
                  "version": "v1",
                  "profile": {
                    "relationship_desc": "%s",
                    "gender": "女"
                  },
                  "tags": {
                    "personality": [],
                    "attachment": ""
                  },
                  "impression": "%s",
                  "knowledge_sources": [],
                  "corrections_count": 0
                }
                """.formatted(
                jsonEscape(req.nickname()),
                slug,
                now, now,
                jsonEscape(req.relationshipDesc() == null ? "" : req.relationshipDesc()),
                jsonEscape(req.personalityTags() == null ? "" : req.personalityTags()));
    }

    private String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
