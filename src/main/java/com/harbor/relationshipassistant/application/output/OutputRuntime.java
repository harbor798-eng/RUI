package com.harbor.relationshipassistant.application.output;

import com.harbor.relationshipassistant.application.llm.AnalysisResult;
import com.harbor.relationshipassistant.application.llm.LLMClient;
import com.harbor.relationshipassistant.application.llm.LLMResponse;
import com.harbor.relationshipassistant.application.prompt.LLMRequest;
import com.harbor.relationshipassistant.application.skill.context.SkillExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 通用 Output Runtime。
 *
 * <p>职责边界：接收 LLM 原始文本 → 包装成 {@link RawModelOutput} → 交付给上层。
 * 不解析业务 JSON、不假设 Skill 输出格式、不包含 Quick Reply 三策略、
 * 不包含 Detail Analysis JSON schema、不包含情绪词表。</p>
 *
 * <p>业务适配器（QuickReplyAdapter / Detail Analysis Adapter / UI）负责决定
 * 如何把 {@link RawModelOutput#getContent()} 解释成业务模型。</p>
 */
public final class OutputRuntime {
    private static final Logger log = LoggerFactory.getLogger(OutputRuntime.class);

    private final LLMClient client;

    public OutputRuntime(LLMClient client) {
        this.client = client;
    }

    /**
     * 发送一次 LLM 请求，返回原始输出。
     * 不抛业务异常；网络/协议错误由调用方处理。
     */
    public RawModelOutput generate(SkillExecutionContext exec, LLMRequest request) {
        LLMResponse resp = client.complete(request);
        String skillName = exec.getSkill() != null && exec.getSkill().getMetadata() != null
                ? exec.getSkill().getMetadata().getName()
                : null;
        RawModelOutput out = new RawModelOutput(
                resp.content(),
                resp.model(),
                exec.getTask().getFunction(),
                skillName,
                java.time.LocalDateTime.now()
        );
        log.info("[OUTPUT-RUNTIME] function={} skill={} contentLen={} model={}",
                out.getFunction(), out.getSkillName(), out.getContent().length(), out.getModel());
        return out;
    }

    /** 兼容桥接：把 RawModelOutput 包装成旧的 AnalysisResult，不改变业务接口。 */
    public AnalysisResult toAnalysisResult(RawModelOutput raw) {
        return new AnalysisResult(raw.getFunction(), raw.getSkillName(),
                raw.getContent(), raw.getModel(), raw.getCreatedAt());
    }
}
