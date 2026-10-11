package com.harbor.relationshipassistant.application.distill;

import com.harbor.relationshipassistant.domain.chat.ChatMessage;
import com.harbor.relationshipassistant.domain.chat.SenderType;
import com.harbor.relationshipassistant.infrastructure.ai.AIProvider;
import com.harbor.relationshipassistant.infrastructure.ai.AIRequest;
import com.harbor.relationshipassistant.infrastructure.ai.AIResponse;
import com.harbor.relationshipassistant.infrastructure.persistence.ChatMessageRepository;
import com.harbor.relationshipassistant.infrastructure.persistence.RelationshipRepository;
import com.harbor.relationshipassistant.domain.relationship.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 人物蒸馏服务：从聊天记录提取样本 → LLM 分析 → 生成 SKILL.md → 安全写入 ./skills/。
 */
public class DistillationService {
    private static final Logger log = LoggerFactory.getLogger(DistillationService.class);
    private static final int MAX_SAMPLES = 800;
    private static final int MIN_SAMPLES = 20;
    private static final int MAX_TEXT_LEN = 8000;

    private final ChatMessageRepository chatRepo;
    private final RelationshipRepository relRepo;
    private final AIProvider aiProvider;
    private final Path skillsRoot;

    public DistillationService(ChatMessageRepository chatRepo,
                               RelationshipRepository relRepo,
                               AIProvider aiProvider,
                               Path skillsRoot) {
        this.chatRepo = chatRepo;
        this.relRepo = relRepo;
        this.aiProvider = aiProvider;
        this.skillsRoot = skillsRoot.toAbsolutePath().normalize();
    }

    // ---------- 请求/响应 DTO ----------

    /** 单个特征调节：enabled 是否启用，value 1-10（5=不调整） */
    public record TraitAdj(boolean enabled, int value) {}

    public record DistillRequest(
            String mode,
            long relationshipId,
            String targetName,
            String action,
            String relationshipRole,          // 身份：朋友/恋人/前任/.../自定义
            Map<String, TraitAdj> traits,     // 特征调节（key 为中文名）
            String customNote                 // 补充说明
    ) {
        // 兼容旧调用：无配置时的构造器
        public DistillRequest(String mode, long relationshipId, String targetName, String action) {
            this(mode, relationshipId, targetName, action, null, Map.of(), "");
        }
    }

    public record DistillResult(
            boolean success,
            String skillName,
            String skillDir,
            String message,
            int sampleCount,
            int usedSampleCount
    ) {}

    public DistillResult distill(DistillRequest req) {
        Relationship rel;
        try {
            rel = relRepo.findById(req.relationshipId);
        } catch (Exception e) {
            return new DistillResult(false, null, null, "关系不存在: " + e.getMessage(), 0, 0);
        }
        if (rel == null) {
            return new DistillResult(false, null, null, "关系不存在 id=" + req.relationshipId, 0, 0);
        }

        SenderType targetSender = "SELF".equals(req.mode()) ? SenderType.ME : SenderType.OTHER;
        List<ChatMessage> all = chatRepo.findLatest(req.relationshipId(), MAX_SAMPLES);
        List<ChatMessage> samples = all.stream()
                .filter(m -> m.getSenderType() == targetSender)
                .filter(m -> m.getContent() != null && !m.getContent().isBlank())
                .filter(m -> m.getMessageType() == null || m.getMessageType().name().equals("TEXT")
                        || m.getMessageType().name().equals("EMOJI"))
                .collect(Collectors.toList());
        Collections.reverse(samples);

        if (samples.size() < MIN_SAMPLES) {
            return new DistillResult(false, null, null,
                    "样本不足：只找到 " + samples.size() + " 条" + targetSender + "消息，至少需要 " + MIN_SAMPLES + " 条",
                    samples.size(), 0);
        }

        String sampleText = buildSampleText(samples);
        if (sampleText.length() > MAX_TEXT_LEN) {
            sampleText = sampleText.substring(0, MAX_TEXT_LEN) + "\n...(截断)";
        }

        String baseName = "SELF".equals(req.mode())
                ? "self-persona"
                : slugify(rel.getName());
        String skillName = baseName;
        Path skillDir = skillsRoot.resolve(skillName);

        boolean existing = Files.exists(skillDir.resolve("SKILL.md"));
        if (existing && !"UPDATE_EXISTING".equals(req.action())) {
            skillName = baseName + "-" + System.currentTimeMillis();
            skillDir = skillsRoot.resolve(skillName);
        }

        log.info("[DISTILL] mode={} rel={} samples={} skillName={} role={} traits={}",
                req.mode(), rel.getName(), samples.size(), skillName,
                req.relationshipRole(), activeTraitSummary(req.traits()));

        String systemPrompt = buildSystemPrompt(req, rel.getName());
        List<AIRequest.Turn> turns = List.of(new AIRequest.Turn("user", sampleText));
        AIRequest aiReq = AIRequest.of(systemPrompt, turns);
        aiReq.setTemperature(0.4);
        aiReq.setMaxTokens(2000);

        AIResponse resp;
        try {
            resp = aiProvider.generate(aiReq);
        } catch (Exception e) {
            log.error("[DISTILL] LLM call failed", e);
            return new DistillResult(false, skillName, skillDir.toString(),
                    "LLM 调用失败: " + e.getMessage(), samples.size(), 0);
        }

        String skillMd = buildSkillMarkdown(skillName, req.targetName(), req.mode(),
                req.relationshipRole(), req.traits(), resp.text());

        try {
            writeSkillSafely(skillDir, skillMd);
        } catch (Exception e) {
            log.error("[DISTILL] write failed", e);
            return new DistillResult(false, skillName, skillDir.toString(),
                    "写入失败: " + e.getMessage(), samples.size(), samples.size());
        }

        log.info("[DISTILL] success skillName={} dir={}", skillName, skillDir);
        return new DistillResult(true, skillName, skillDir.toString(),
                "蒸馏完成，已生成 Skill: " + skillName, samples.size(), samples.size());
    }

    // ---------- Prompt 构建 ----------

    private String activeTraitSummary(Map<String, TraitAdj> traits) {
        if (traits == null || traits.isEmpty()) return "无";
        return traits.entrySet().stream()
                .filter(e -> e.getValue().enabled() && e.getValue().value() != 5)
                .map(e -> e.getKey() + "=" + e.getValue().value())
                .collect(Collectors.joining(","));
    }

    private String buildTraitInstruction(Map<String, TraitAdj> traits) {
        if (traits == null || traits.isEmpty()) return "";
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, TraitAdj> e : traits.entrySet()) {
            TraitAdj adj = e.getValue();
            if (!adj.enabled()) continue;
            int v = adj.value();
            if (v == 5) continue;
            String dir = v > 5 ? "增强" : "减弱";
            int strength = Math.abs(v - 5);
            lines.add("  - " + e.getKey() + ": " + dir + "（强度 " + strength + "/5）");
        }
        if (lines.isEmpty()) return "";
        return "\n【用户指定的性格调节（在原始分析基础上调整）】\n" +
                String.join("\n", lines) + "\n" +
                "要求：保留该人物真实的口头禅、句式和语言辨识度；上述调节是在原始风格基础上的微调，" +
                "不得把用户调节目标伪装成聊天记录中观察到的事实。未列出的特征保持原始分析不变。\n";
    }

    private String buildSystemPrompt(DistillRequest req, String relName) {
        String role = req.relationshipRole();
        String roleContext;
        if ("SELF".equals(req.mode())) {
            roleContext = "";
        } else if (role != null && !role.isBlank() && !"其他".equals(role)) {
            roleContext = "该人物与用户的关系身份为：" + role + "。请在此关系语境下分析其沟通习惯。\n";
        } else if (role != null && !role.isBlank()) {
            roleContext = "该人物与用户的关系身份为：" + role + "。\n";
        } else {
            roleContext = "";
        }

        String traitInstr = buildTraitInstruction(req.traits());

        if ("SELF".equals(req.mode())) {
            return """
                    你是一个人物表达分析师。以下是用户在与他人的微信聊天中【自己发送】的消息记录。
                    请分析用户本人的表达风格，输出一份可执行的"说话风格指南"，用于 AI 在帮用户起草回复时模仿用户的语气。

                    %s
                    %s
                    要求：
                    1. 只基于消息中的证据，不要编造。明确区分【观察到的规律】和【推测】。
                    2. 输出 Markdown 格式，包含以下部分：
                       ## 表达特征
                       - 常用词汇、语气词、句式特点
                       - 标点和表情使用习惯
                       - 情绪表达模式（直接/委婉/幽默/冷淡等）
                       ## 互动习惯
                       - 主动联系 vs 等待对方先开口
                       - 回复节奏偏好
                       - 冲突时的沟通方式
                       ## 说话规则
                       给出 5-8 条具体的写作规则，让 AI 能按用户风格生成回复。
                       每条规则要可执行，例如"多用短句，少用长从句"。
                    3. 不要输出"分析报告"式的空话，直接给出可操作的风格指南。
                    4. 中文输出。
                    """.formatted(roleContext, traitInstr);
        } else {
            String targetName = req.targetName();
            return """
                    你是一个人物表达分析师。以下是用户在与【%s】的微信聊天中，【对方发送】的消息记录。
                    请分析 %s 的表达风格和思维模式，输出一份"人物画像指南"，用于 AI 在帮用户起草回复时更好地理解对方。

                    %s
                    %s
                    要求：
                    1. 只基于消息中的证据，不要编造。明确区分【观察到的规律】和【推测】。
                    2. 输出 Markdown 格式，包含以下部分：
                       ## 表达特征
                       - 常用词汇、语气词、句式特点
                       - 标点和表情使用习惯
                       - 情绪表达模式
                       ## 互动模式
                       - 主动 vs 被动
                       - 如何表达关心/不满/兴趣
                       - 常见的回应方式
                       ## 应对规则
                       给出 5-8 条具体规则，告诉 AI 在帮用户回复 %s 时应该注意什么。
                       例如"对方喜欢用反问，回复时要给明确答案"。
                    3. 不要输出空话，直接给出可操作的画像。
                    4. 中文输出。
                    """.formatted(targetName, targetName, roleContext, traitInstr, targetName);
        }
    }

    private String buildSampleText(List<ChatMessage> samples) {
        StringBuilder sb = new StringBuilder("以下是聊天记录（按时间正序）：\n\n");
        for (ChatMessage m : samples) {
            String time = m.getMessageTime() != null ? m.getMessageTime().toLocalDate().toString() : "?";
            sb.append("[").append(time).append("] ").append(m.getContent().trim()).append("\n");
        }
        return sb.toString();
    }

    private String buildSkillMarkdown(String skillName, String targetName, String mode,
                                      String role, Map<String, TraitAdj> traits, String analysis) {
        String description = "SELF".equals(mode)
                ? "用户本人的说话风格 Skill。在帮用户起草回复时，模仿用户自己的表达习惯。"
                : targetName + " 的人物画像与沟通风格。在帮用户回复 " + targetName + " 时使用。";

        // 生成调节记录头
        StringBuilder mods = new StringBuilder();
        if (role != null && !role.isBlank()) {
            mods.append("- 关系身份：").append(role).append("\n");
        }
        if (traits != null) {
            traits.forEach((k, v) -> {
                if (v.enabled() && v.value() != 5) {
                    mods.append("- ").append(k).append(": ")
                        .append(v.value() > 5 ? "增强" : "减弱")
                        .append("（").append(v.value()).append("/10）\n");
                }
            });
        }
        String modSection = mods.length() > 0
                ? "\n## 生成参数\n" + mods
                : "";

        return """
                ---
                name: %s
                description: %s
                ---

                # %s

                ## 使用说明
                当用户选择此 Skill 时，按以下风格指南生成回复。不要编造没有证据的特征。
                本 Skill 由聊天记录蒸馏生成；标注为【调节】的内容是用户指定的风格微调，不等于聊天记录中的客观事实。
                %s
                %s
                """.formatted(skillName, description, targetName, modSection, analysis.trim());
    }

    // ---------- 文件写入 ----------

    private void writeSkillSafely(Path skillDir, String skillMd) throws IOException {
        Path targetFile = skillDir.resolve("SKILL.md");

        if (Files.exists(targetFile)) {
            Path backup = skillDir.resolve("SKILL.md.bak");
            Files.copy(targetFile, backup, StandardCopyOption.REPLACE_EXISTING);
            log.info("[DISTILL] backed up old SKILL.md to {}", backup);
        }

        Path tmpDir = Files.createTempDirectory("rui-distill-");
        Path tmpFile = tmpDir.resolve("SKILL.md");
        Files.writeString(tmpFile, skillMd);

        try {
            Files.createDirectories(skillDir);
            Files.move(tmpFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmpFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            try { Files.deleteIfExists(tmpFile); Files.deleteIfExists(tmpDir); } catch (IOException ignored) {}
        }
        log.info("[DISTILL] wrote {}", targetFile);
    }

    private String slugify(String s) {
        if (s == null || s.isBlank()) return "persona";
        return s.toLowerCase()
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
