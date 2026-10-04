package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.application.analysis.report.DeepObservationPipelineResult;
import com.harbor.relationshipassistant.domain.analysis.AnalysisContext;
import com.harbor.relationshipassistant.domain.analysis.AnalysisMetadata;
import com.harbor.relationshipassistant.domain.analysis.AnalysisRange;
import com.harbor.relationshipassistant.domain.analysis.AnalysisTask;
import com.harbor.relationshipassistant.domain.analysis.AnalysisTaskType;
import com.harbor.relationshipassistant.domain.analysis.TaskCapability;
import com.harbor.relationshipassistant.domain.analysis.EvidenceWindow;
import com.harbor.relationshipassistant.domain.analysis.OutputMode;
import com.harbor.relationshipassistant.domain.analysis.PatternCandidate;
import com.harbor.relationshipassistant.domain.analysis.Skill;
import com.harbor.relationshipassistant.domain.analysis.AnalysisMessage;
import com.harbor.relationshipassistant.domain.analysis.interaction.ConversationSession;
import com.harbor.relationshipassistant.domain.analysis.interaction.InteractionStatistics;
import com.harbor.relationshipassistant.domain.analysis.knowledge.KnowledgeRegistry;
import com.harbor.relationshipassistant.domain.analysis.knowledge.KnowledgeSelection;
import com.harbor.relationshipassistant.domain.analysis.knowledge.LoadedKnowledge;
import com.harbor.relationshipassistant.domain.analysis.need.AnalysisNeed;
import com.harbor.relationshipassistant.domain.analysis.skill.SkillDefinition;
import com.harbor.relationshipassistant.domain.analysis.statistics.MessageStatistics;
import com.harbor.relationshipassistant.domain.analysis.statistics.StatisticsContext;
import com.harbor.relationshipassistant.domain.relationship.RelationshipStage;
import com.harbor.relationshipassistant.infrastructure.ai.AIRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分析用例入口：把 ChatProcessor → Statistics → Evidence → Context → Skill/Need/Knowledge →
 * Prompt → AnalysisResultExecutor 串成一次完整执行。
 * <p>
 * 本类是 Orchestrator，不重新实现任何算法。Profile/Relationship 由调用方以 Map 形式传入，
 * 权限过滤仍由 {@link AnalysisContextBuilder} 完成。
 * </p>
 */
public class AnalysisService {

    private final ChatProcessor chatProcessor;
    private final StatisticsCalculator statisticsCalculator;
    private final ConversationSessionBuilder sessionBuilder;
    private final InteractionStatisticsCalculator interactionStatsCalculator;
    private final EvidenceDetector evidenceDetector;
    private final EvidenceProcessor evidenceProcessor;
    private final PatternDetector patternDetector;
    private final AnalysisContextBuilder contextBuilder;
    private final SkillRouter skillRouter;
    private final AnalysisNeedDetector needDetector;
    private final KnowledgeRouter knowledgeRouter;
    private final KnowledgeLoader knowledgeLoader;
    private final KnowledgeRegistry knowledgeRegistry;
    private final PromptAssembler promptAssembler;
    private final AnalysisResultExecutor resultExecutor;

    public AnalysisService(ChatProcessor chatProcessor,
                           StatisticsCalculator statisticsCalculator,
                           ConversationSessionBuilder sessionBuilder,
                           InteractionStatisticsCalculator interactionStatsCalculator,
                           EvidenceDetector evidenceDetector,
                           EvidenceProcessor evidenceProcessor,
                           PatternDetector patternDetector,
                           AnalysisContextBuilder contextBuilder,
                           SkillRouter skillRouter,
                           AnalysisNeedDetector needDetector,
                           KnowledgeRouter knowledgeRouter,
                           KnowledgeLoader knowledgeLoader,
                           KnowledgeRegistry knowledgeRegistry,
                           PromptAssembler promptAssembler,
                           AnalysisResultExecutor resultExecutor) {
        this.chatProcessor = chatProcessor;
        this.statisticsCalculator = statisticsCalculator;
        this.sessionBuilder = sessionBuilder;
        this.interactionStatsCalculator = interactionStatsCalculator;
        this.evidenceDetector = evidenceDetector;
        this.evidenceProcessor = evidenceProcessor;
        this.patternDetector = patternDetector;
        this.contextBuilder = contextBuilder;
        this.skillRouter = skillRouter;
        this.needDetector = needDetector;
        this.knowledgeRouter = knowledgeRouter;
        this.knowledgeLoader = knowledgeLoader;
        this.knowledgeRegistry = knowledgeRegistry;
        this.promptAssembler = promptAssembler;
        this.resultExecutor = resultExecutor;
    }

    public AnalysisExecutionResult execute(long relationshipId,
                                           AnalysisTaskType taskType,
                                           AnalysisRange range,
                                           Skill skill,
                                           OutputMode outputMode,
                                           Map<String, String> meRaw,
                                           Map<String, String> meWeights,
                                           Map<String, String> otherRaw,
                                           Map<String, String> otherWeights,
                                           RelationshipStage relationshipStage,
                                           LocalDateTime relationshipStart,
                                           LocalDateTime analysisStart,
                                           LocalDateTime analysisEnd) {
        return doExecute(relationshipId, taskType, range, skill, outputMode,
                meRaw, meWeights, otherRaw, otherWeights,
                relationshipStage, relationshipStart, analysisStart, analysisEnd).execResult();
    }

    /** Deep Observation 专用：返回 AnalysisExecutionResult + Context + KnowledgeSelection，供报告层复用同一次执行。 */
    public DeepObservationPipelineResult executeDeepObservationPipeline(long relationshipId,
                                                                        AnalysisTaskType taskType,
                                                                        AnalysisRange range,
                                                                        Skill skill,
                                                                        OutputMode outputMode,
                                                                        Map<String, String> meRaw,
                                                                        Map<String, String> meWeights,
                                                                        Map<String, String> otherRaw,
                                                                        Map<String, String> otherWeights,
                                                                        RelationshipStage relationshipStage,
                                                                        LocalDateTime relationshipStart,
                                                                        LocalDateTime analysisStart,
                                                                        LocalDateTime analysisEnd) {
        PipelineData d = doExecute(relationshipId, taskType, range, skill, outputMode,
                meRaw, meWeights, otherRaw, otherWeights,
                relationshipStage, relationshipStart, analysisStart, analysisEnd);
        return new DeepObservationPipelineResult(d.execResult(), d.context(), d.selected());
    }

    /** 详细分析语义入口：固定最近 7 天范围，返回 Base AnalysisResult。 */
    public AnalysisExecutionResult executeDetailAnalysis(long relationshipId,
                                                        Skill skill,
                                                        OutputMode outputMode,
                                                        Map<String, String> meRaw,
                                                        Map<String, String> meWeights,
                                                        Map<String, String> otherRaw,
                                                        Map<String, String> otherWeights,
                                                        RelationshipStage relationshipStage,
                                                        LocalDateTime relationshipStart) {
        System.out.println("[DetailAnalysis] start. relationshipId=" + relationshipId + ", range=RECENT_7_DAYS");
        AnalysisExecutionResult r = doExecute(relationshipId, AnalysisTaskType.DETAIL_ANALYSIS,
                AnalysisRange.of(AnalysisRange.Kind.RECENT_7_DAYS),
                skill, outputMode, meRaw, meWeights, otherRaw, otherWeights,
                relationshipStage, relationshipStart, null, null).execResult();
        System.out.println("[DetailAnalysis] completed. success=" + r.success()
                + ", attemptsUsed=" + r.attemptsUsed());
        return r;
    }

    private PipelineData doExecute(long relationshipId,
                                   AnalysisTaskType taskType,
                                   AnalysisRange range,
                                   Skill skill,
                                   OutputMode outputMode,
                                   Map<String, String> meRaw,
                                   Map<String, String> meWeights,
                                   Map<String, String> otherRaw,
                                   Map<String, String> otherWeights,
                                   RelationshipStage relationshipStage,
                                   LocalDateTime relationshipStart,
                                   LocalDateTime analysisStart,
                                   LocalDateTime analysisEnd) {
        System.out.println("[AnalysisService] start. relationshipId=" + relationshipId
                + ", taskType=" + taskType + ", range=" + range + ", skill=" + skill);

        AnalysisTask task = new AnalysisTask(null, relationshipId, taskType, range,
                skill == null ? Skill.NONE : skill,
                outputMode == null ? OutputMode.NORMAL : outputMode,
                LocalDateTime.now());

        TaskCapability capability = TaskCapability.of(taskType);
        if (!capability.isSkillSatisfied(task.getSkill())) {
            throw new IllegalStateException("TaskType " + taskType + " requires skill="
                    + capability.getRequiredSkill() + ", got " + task.getSkill());
        }

        // 1. Chat
        stage("CHAT_PROCESSING");
        List<AnalysisMessage> messages = chatProcessor.process(relationshipId, range);
        if (messages == null) messages = new ArrayList<>();
        System.out.println("[AnalysisService] messages=" + messages.size());

        // 2. Statistics
        stage("STATISTICS");
        StatisticsContext stats = statisticsCalculator.calculate(messages);
        MessageStatistics ms = stats.getMessageStatistics();

        // 3. Sessions
        stage("SESSIONS");
        List<ConversationSession> sessions = sessionBuilder.build(messages);

        // 4. Interaction statistics
        stage("INTERACTION_STATISTICS");
        InteractionStatistics is = interactionStatsCalculator.calculate(relationshipId, sessions, messages);

        // 5. Evidence
        stage("EVIDENCE_DETECTION");
        List<EvidenceWindow> candidates = evidenceDetector.detect(messages, sessions, ms, is);
        stage("EVIDENCE_PROCESSING");
        List<EvidenceWindow> evidence = evidenceProcessor.process(candidates, messages, sessions, ms, is);

        // 6. Patterns
        stage("PATTERN_DETECTION");
        List<PatternCandidate> patterns = patternDetector.detect(messages, sessions, ms, is, evidence);

        // 7. Context
        stage("CONTEXT_BUILDING");
        LocalDateTime actualStart = messages.isEmpty() ? null
                : messages.get(0).getMessageTime();
        LocalDateTime actualEnd = messages.isEmpty() ? null
                : messages.get(messages.size() - 1).getMessageTime();
        AnalysisMetadata meta = new AnalysisMetadata(LocalDateTime.now(), actualStart, actualEnd,
                ms == null ? 0 : ms.getTotalMessageCount(),
                messages.size(), false);
        com.harbor.relationshipassistant.domain.analysis.RelationshipContext relationship =
                new com.harbor.relationshipassistant.domain.analysis.RelationshipContext(
                        relationshipId, relationshipStage, relationshipStart, analysisStart, analysisEnd);
        AnalysisContext context = contextBuilder.build(task, relationship,
                nullSafe(meRaw), nullSafe(meWeights), nullSafe(otherRaw), nullSafe(otherWeights),
                ms, is, evidence, new DeepTimelineBuilder().build(evidence), patterns, meta);

        // 8. Skill / Need
        stage("SKILL_ROUTING");
        SkillDefinition skillDef = skillRouter.route(context);
        stage("NEED_DETECTION");
        List<AnalysisNeed> needs = needDetector.detect(context, skillDef);

        // 9. Knowledge
        stage("KNOWLEDGE_ROUTING");
        List<KnowledgeSelection> selected = knowledgeRouter.route(context, needs, task.getSkill(), knowledgeRegistry);
        stage("KNOWLEDGE_LOADING");
        List<LoadedKnowledge> loaded = selected.isEmpty()
                ? Collections.emptyList() : knowledgeLoader.loadAll(selected);

        // 10. Prompt
        stage("PROMPT_ASSEMBLY");
        AIRequest request = promptAssembler.assemble(context, skillDef, needs, loaded);

        // 11. AI
        stage("AI_EXECUTION");
        AnalysisExecutionResult result = resultExecutor.execute(request, context, selected, capability);

        System.out.println("[AnalysisService] completed. taskType=" + taskType
                + ", messages=" + messages.size()
                + ", evidence=" + evidence.size()
                + ", patterns=" + patterns.size()
                + ", knowledge=" + loaded.size()
                + ", attemptsUsed=" + result.attemptsUsed()
                + ", success=" + result.success());
        return new PipelineData(result, context, selected);
    }

    private record PipelineData(AnalysisExecutionResult execResult,
                                AnalysisContext context,
                                List<KnowledgeSelection> selected) {}

    private static void stage(String s) {
        System.out.println("[AnalysisService] stage=" + s);
    }

    private static Map<String, String> nullSafe(Map<String, String> m) {
        return m == null ? new HashMap<>() : m;
    }
}
