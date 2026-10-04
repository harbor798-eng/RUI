package com.harbor.relationshipassistant.application.ai;

import com.harbor.relationshipassistant.application.ai.dto.CandidateSet;
import com.harbor.relationshipassistant.common.exception.AIException;
import com.harbor.relationshipassistant.infrastructure.ai.AIProvider;
import com.harbor.relationshipassistant.infrastructure.ai.AIRequest;
import com.harbor.relationshipassistant.infrastructure.ai.AIResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 快速回复生成（规范 §18/§21/§48）：
 * ContextBuilder 构建上下文 → 一次 DeepSeek 请求 → 严格 JSON → 校验 → 内存返回 9 候选。
 * 校验/解析失败自动重试一次；仍失败抛错。候选本阶段不落库（Generation Record 属后续 Phase）。
 */
public class QuickReplyService {

    private static final Logger log = LoggerFactory.getLogger(QuickReplyService.class);

    private final ContextBuilder contextBuilder;
    private final AIProvider provider;

    /** 最近一次请求 ID；新请求到来即覆盖，旧结果不得再回调 UI。 */
    private volatile String latestRequestId;

    public QuickReplyService(ContextBuilder contextBuilder, AIProvider provider) {
        this.contextBuilder = contextBuilder;
        this.provider = provider;
    }

    public record GenerationResult(String requestId, CandidateSet candidates, String model,
                                   int promptTokens, int completionTokens,
                                   ContextBuilder.BuiltContext context) {}

    public GenerationResult generateCandidates(Long relationshipId, String tempUserRequest) {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        latestRequestId = requestId;
        long t0 = System.currentTimeMillis();

        java.util.List<com.harbor.relationshipassistant.domain.ai.ReplyStrategy> forced =
                java.util.List.of(
                        com.harbor.relationshipassistant.domain.ai.ReplyStrategy.NATURAL,
                        com.harbor.relationshipassistant.domain.ai.ReplyStrategy.PROACTIVE,
                        com.harbor.relationshipassistant.domain.ai.ReplyStrategy.LIGHT_FLIRT);
        log.info("[QuickReply][STRATEGY_TARGET] requested strategies=NATURAL,PROACTIVE,LIGHT_FLIRT");
        ContextBuilder.BuiltContext ctx = contextBuilder.build(relationshipId, tempUserRequest, forced);

        // 最新发送者只是 Context 模式信息，不再作为生成准入条件：
        // 无论最后一条是 ME 还是 OTHER，用户主动点击生成都要继续调用 AI。
        log.info("[REPLY_TARGET][ALLOW] relationshipId={} task=GENERATE_NEXT_MESSAGE speaker=ME",
                relationshipId);
        log.info("[REPLY_TARGET][CONTEXT] relationshipId={} mode={}", relationshipId,
                ctx.latestSender() == com.harbor.relationshipassistant.domain.chat.SenderType.OTHER
                        ? "RESPOND_OR_CONTINUE" : "CONTINUE_OR_NEW_TOPIC");

        AIRequest req = ctx.request();
        req.setTemperature(0.8);
        req.setMaxTokens(1200);

        Exception lastError = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            if (!requestId.equals(latestRequestId)) {
                log.info("[AI][ERROR] requestId={} 已过期（新请求到来），丢弃", requestId);
                throw new AIException("请求已过期", "generateCandidates");
            }
            try {
                log.info("[AI][REQUEST] requestId={} provider={} relationshipId={} attempt={}",
                        requestId, provider.getProviderName(), relationshipId, attempt);
                AIResponse resp = provider.generate(req);
                long elapsed = System.currentTimeMillis() - t0;
                log.info("[AI][SUCCESS] requestId={} elapsedMs={} promptTokens={}",
                        requestId, elapsed, resp.promptTokens());
                CandidateSet set = CandidateSet.parseAndValidate(resp.text(), ctx.strategies());
                log.info("[CANDIDATE][PARSED] requestId={} strategyCount={} candidateCount={}",
                        requestId, set.strategies.size(), set.flatten().size());
                return new GenerationResult(requestId, set, resp.model(),
                        resp.promptTokens(), resp.completionTokens(), ctx);
            } catch (AIException e) {
                lastError = e;
                log.warn("[CANDIDATE][VALIDATION] requestId={} attempt={} errorType={}",
                        requestId, attempt, e.getMessage());
                if (attempt == 1) log.info("[AI][RETRY] requestId={}", requestId);
            }
        }
        log.error("[AI][ERROR] requestId={} 最终失败: {}", requestId, lastError == null ? "?" : lastError.getMessage());
        throw new AIException("AI 回复校验失败，请重试", "generateCandidates", lastError);
    }

    public record SingleResult(String requestId, String strategyLabel, String text, String reason) {}

    public SingleResult generateSingleCandidate(Long relationshipId, com.harbor.relationshipassistant.domain.ai.ReplyStrategy only) {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        latestRequestId = requestId;
        long t0 = System.currentTimeMillis();
        log.info("[JEVE][QuickReply] single refresh requested strategy={}", only);
        ContextBuilder.BuiltContext ctx = contextBuilder.build(relationshipId, "", java.util.List.of(only));
        AIRequest req = ctx.request();
        req.setTemperature(0.8);
        req.setMaxTokens(400);
        Exception lastError = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                AIResponse resp = provider.generate(req);
                long elapsed = System.currentTimeMillis() - t0;
                var parsed = CandidateSet.parseSingle(resp.text(), only.getLabel());
                log.info("[JEVE][QuickReply] single refresh success strategy={} elapsedMs={}", only, elapsed);
                return new SingleResult(requestId, parsed.strategyLabel(), parsed.text(), parsed.reason());
            } catch (AIException e) {
                lastError = e;
                log.warn("[JEVE][QuickReply] single refresh attempt={} error={}", attempt, e.getMessage());
            }
        }
        log.error("[JEVE][QuickReply] single refresh failed strategy={} err={}", only, lastError==null?"?":lastError.getMessage());
        throw new AIException("单策略生成失败", "generateSingleCandidate", lastError);
    }
    public String getLatestRequestId() {
        return latestRequestId;
    }
}
