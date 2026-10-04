package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.application.analysis.validator.AnalysisResultValidator;
import com.harbor.relationshipassistant.domain.analysis.AnalysisContext;
import com.harbor.relationshipassistant.domain.analysis.AnalysisOutput;
import com.harbor.relationshipassistant.domain.analysis.AnalysisTaskType;
import com.harbor.relationshipassistant.domain.analysis.TaskCapability;
import com.harbor.relationshipassistant.domain.analysis.deep.DeepAnalysisResult;
import com.harbor.relationshipassistant.domain.analysis.knowledge.KnowledgeSelection;
import com.harbor.relationshipassistant.domain.analysis.quickreply.QuickReplyResult;
import com.harbor.relationshipassistant.domain.analysis.validation.ValidationIssue;
import com.harbor.relationshipassistant.domain.analysis.validation.ValidationResult;
import com.harbor.relationshipassistant.infrastructure.ai.AIRequest;
import com.harbor.relationshipassistant.infrastructure.ai.AIResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * 按 TaskCapability 选择 Parser，执行 LLM → Parse → Validate → Retry。
 */
public class AnalysisResultExecutor {

    private final AnalysisExecutor llm;
    private final AnalysisResultParser baseParser = new AnalysisResultParser();
    private final QuickReplyParser quickReplyParser = new QuickReplyParser();
    private final DeepAnalysisResultParser deepParser = new DeepAnalysisResultParser();
    private final AnalysisResultValidator validator = new AnalysisResultValidator();
    private final AnalysisRetryPolicy policy;

    public AnalysisResultExecutor(AnalysisExecutor llm) {
        this(llm, new AnalysisRetryPolicy());
    }

    public AnalysisResultExecutor(AnalysisExecutor llm, AnalysisRetryPolicy policy) {
        this.llm = llm;
        this.policy = policy;
    }

    public AnalysisExecutionResult execute(AIRequest request,
                                           AnalysisContext context,
                                           List<KnowledgeSelection> selectedKnowledge,
                                           TaskCapability capability) {
        System.out.println("[AnalysisResultExecutor] start. taskType="
                + context.getTask().getTaskType() + ", resultType=" + capability.getResultType().getSimpleName());

        AnalysisOutput lastOutput = null;
        AIResponse lastResponse = null;
        ValidationResult lastVr = null;
        Throwable lastParseError = null;

        for (int attempt = 1; attempt <= policy.getMaxAttempts(); attempt++) {
            System.out.println("[AnalysisResultExecutor] attempt=" + attempt);
            AIRequest req = attempt == 1 ? request
                    : buildRepairRequest(request, lastParseError, lastVr, context.getTask().getTaskType());

            lastResponse = llm.execute(req);
            lastOutput = null;
            lastParseError = null;
            try {
                lastOutput = parseWith(capability, lastResponse);
            } catch (AnalysisResultParseException e) {
                lastParseError = e;
                System.out.println("[AnalysisResultExecutor] attempt=" + attempt + " parse failed: " + e.getMessage());
            }

            if (lastOutput != null) {
                lastVr = validate(capability, lastOutput, context, selectedKnowledge);
                if (lastVr.isValid()) {
                    System.out.println("[AnalysisResultExecutor] completed. attemptsUsed=" + attempt);
                    return AnalysisExecutionResult.ok(attempt, lastResponse, lastOutput, lastVr);
                }
                long errs = lastVr.getIssues().stream().filter(i -> i.getSeverity() == ValidationIssue.Severity.ERROR).count();
                System.out.println("[AnalysisResultExecutor] attempt=" + attempt + " validation failed errors=" + errs);
            }

            if (!policy.shouldRetry(attempt, lastParseError, lastVr)) break;
        }

        String failureType = lastParseError != null ? "PARSE_ERROR" : "VALIDATION_ERROR";
        return AnalysisExecutionResult.fail(policy.getMaxAttempts(), failureType, lastResponse, lastOutput, lastVr);
    }

    private AnalysisOutput parseWith(TaskCapability cap, AIResponse r) {
        Class<?> rt = cap.getResultType();
        if (rt == QuickReplyResult.class) return quickReplyParser.parse(r);
        if (rt == DeepAnalysisResult.class) return deepParser.parse(r);
        return baseParser.parse(r);
    }

    private ValidationResult validate(TaskCapability cap, AnalysisOutput out,
                                      AnalysisContext ctx, List<KnowledgeSelection> kb) {
        // QuickReplyResult：仅做结构性检查（Parser 已保证 3 个策略）。
        if (out instanceof QuickReplyResult qr) {
            List<ValidationIssue> issues = new ArrayList<>();
            if (qr.getCandidates().size() != 3) {
                issues.add(ValidationIssue.error("INVALID_QUICK_REPLY", "must have exactly 3 candidates", "quickReply"));
            }
            return new ValidationResult(issues);
        }
        if (out instanceof DeepAnalysisResult d) {
            return new com.harbor.relationshipassistant.application.analysis.validator.DeepAnalysisValidator()
                    .validate(d, ctx, kb);
        }
        return validator.validate((com.harbor.relationshipassistant.domain.analysis.AnalysisResult) out, ctx, kb);
    }

    private AIRequest buildRepairRequest(AIRequest original, Throwable parseError,
                                          ValidationResult vr, AnalysisTaskType taskType) {
        List<AIRequest.Turn> turns = new ArrayList<>();
        if (original.getTurns() != null) turns.addAll(original.getTurns());
        StringBuilder sb = new StringBuilder();
        sb.append("你的上一轮输出未通过验证。当前任务类型：").append(taskType).append("。请仅修复下列错误，重新输出完整 JSON。\n");
        if (parseError != null) sb.append("- [ERROR] code=INVALID_JSON message=输出不是合法 JSON\n");
        if (vr != null) {
            for (ValidationIssue i : vr.getIssues()) {
                if (i.getSeverity() != ValidationIssue.Severity.ERROR) continue;
                sb.append("- [ERROR] code=").append(i.getCode())
                        .append(" path=").append(i.getPath())
                        .append(" message=").append(i.getMessage()).append("\n");
            }
        }
        turns.add(new AIRequest.Turn("user", sb.toString()));
        AIRequest r = AIRequest.of(original.getSystemPrompt(), turns);
        r.setModel(original.getModel());
        r.setTemperature(original.getTemperature());
        r.setMaxTokens(original.getMaxTokens());
        return r;
    }
}
