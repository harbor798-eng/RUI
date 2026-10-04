package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.domain.analysis.AnalysisContext;
import com.harbor.relationshipassistant.domain.analysis.AnalysisMetadata;
import com.harbor.relationshipassistant.domain.analysis.AnalysisTask;
import com.harbor.relationshipassistant.domain.analysis.EvidenceWindow;
import com.harbor.relationshipassistant.domain.analysis.PatternCandidate;
import com.harbor.relationshipassistant.domain.analysis.ProfileContext;
import com.harbor.relationshipassistant.domain.analysis.RelationshipContext;
import com.harbor.relationshipassistant.domain.analysis.TimelineEvent;
import com.harbor.relationshipassistant.domain.analysis.interaction.InteractionStatistics;
import com.harbor.relationshipassistant.domain.analysis.statistics.MessageStatistics;
import com.harbor.relationshipassistant.domain.analysis.statistics.StatisticsContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把一次分析任务已经准备好的全部运行时材料，组装成 {@link AnalysisContext}。
 * <p>
 * 本 Builder <b>不</b>：
 * <ul>
 *   <li>判断关系好坏 / 心理 / 情绪；</li>
 *   <li>调用 LLM / SkillRouter / KnowledgeRouter / PromptAssembler；</li>
 *   <li>重新打分 / 合并 / 抽样 EvidenceWindow；</li>
 *   <li>重新检测 Pattern；</li>
 *   <li>把 NONE 权限字段带给 AI；</li>
 *   <li>伪造数据完整性。</li>
 * </ul>
 * 它只做：校验关系隔离 + AI 权限过滤 + 完整性校验 + 不可变组装。
 * </p>
 */
public class AnalysisContextBuilder {

    /**
     * @param task             本次分析任务（不能为 null）
     * @param relationship     关系上下文（不能为 null）
     * @param meRaw            ME 档案原始字段（key=字段名，value=字段值）
     * @param meWeights        ME 每个字段的 usage_weight（NONE/LOW/NORMAL/HIGH）
     * @param otherRaw         OTHER 档案原始字段
     * @param otherWeights     OTHER 每个字段的 usage_weight
     * @param messageStats     MessageStatistics（可空）
     * @param interactionStats InteractionStatistics（可空）
     * @param evidenceWindows  EvidenceProcessor 已经处理好的最终窗口（可空=空列表）
     * @param timeline         运行时时间线（可空=空列表；当前阶段允许为空）
     * @param patterns         PatternDetector 输出（可空=空列表）
     * @param metadata         描述实际数据范围的元数据（不能为 null）
     */
    public AnalysisContext build(AnalysisTask task,
                                 RelationshipContext relationship,
                                 Map<String, String> meRaw,
                                 Map<String, String> meWeights,
                                 Map<String, String> otherRaw,
                                 Map<String, String> otherWeights,
                                 MessageStatistics messageStats,
                                 InteractionStatistics interactionStats,
                                 List<EvidenceWindow> evidenceWindows,
                                 List<TimelineEvent> timeline,
                                 List<PatternCandidate> patterns,
                                 AnalysisMetadata metadata) {

        System.out.println("[AnalysisContextBuilder] Start building AnalysisContext");
        if (task == null) throw new IllegalArgumentException("task must not be null");
        if (relationship == null) throw new IllegalArgumentException("relationship must not be null");
        if (metadata == null) throw new IllegalArgumentException("metadata must not be null");
        if (task.getRelationshipId() <= 0) {
            throw new IllegalArgumentException("task.relationshipId must be > 0");
        }
        if (relationship.getRelationshipId() != task.getRelationshipId()) {
            throw new IllegalArgumentException("relationship.relationshipId does not match task.relationshipId");
        }

        long relId = task.getRelationshipId();
        System.out.println("[AnalysisContextBuilder] taskId=" + task.getId());
        System.out.println("[AnalysisContextBuilder] relationshipId=" + relId);
        System.out.println("[AnalysisContextBuilder] taskType=" + task.getTaskType());
        System.out.println("[AnalysisContextBuilder] range=" + task.getRange());
        System.out.println("[AnalysisContextBuilder] skill=" + task.getSkill());
        System.out.println("[AnalysisContextBuilder] outputMode=" + task.getOutputMode());

        // 1) Profile 权限过滤
        ProfileContext profiles = filterProfiles(meRaw, meWeights, otherRaw, otherWeights);
        System.out.println("[AnalysisContextBuilder] profile ME fields=" + profiles.getMe().size());
        System.out.println("[AnalysisContextBuilder] profile OTHER fields=" + profiles.getOther().size());

        // 2) 组装 StatisticsContext（不重新计算，只合并两个已产出的结果）
        StatisticsContext statistics = null;
        if (messageStats != null) {
            statistics = new StatisticsContext(messageStats, interactionStats);
        }
        long msgCount = (messageStats != null) ? messageStats.getTotalMessageCount() : 0;
        System.out.println("[AnalysisContextBuilder] statistics messages=" + msgCount);

        // 3) EvidenceWindow 关系隔离
        List<EvidenceWindow> windows = evidenceWindows == null ? Collections.emptyList() : evidenceWindows;
        Set<String> evidenceIds = new HashSet<>();
        for (EvidenceWindow w : windows) {
            if (w == null) continue;
            if (w.getRelationshipId() != relId) {
                throw new IllegalArgumentException("[AnalysisContextBuilder][ERROR] EvidenceWindow.relationshipId="
                        + w.getRelationshipId() + " != task.relationshipId=" + relId);
            }
            evidenceIds.add(w.getEvidenceId());
        }
        System.out.println("[AnalysisContextBuilder] evidenceWindows=" + windows.size());

        // 4) PatternCandidate 完整性校验
        List<PatternCandidate> pats = patterns == null ? Collections.emptyList() : patterns;
        for (PatternCandidate p : pats) {
            if (p == null) continue;
            for (String eid : p.getEvidenceIds()) {
                if (!evidenceIds.contains(eid)) {
                    throw new IllegalArgumentException("[AnalysisContextBuilder][ERROR] PatternCandidate references"
                            + " unknown EvidenceWindow: patternId=" + p.getPatternId() + ", evidenceId=" + eid);
                }
            }
        }
        System.out.println("[AnalysisContextBuilder] patterns=" + pats.size());

        // 5) Timeline（当前阶段允许空；不伪造）
        List<TimelineEvent> tl = timeline == null ? Collections.emptyList() : timeline;
        System.out.println("[AnalysisContextBuilder] timelineEvents=" + tl.size());

        // 6) Metadata
        System.out.println("[AnalysisContextBuilder] actualDataStart=" + metadata.getActualDataStart());
        System.out.println("[AnalysisContextBuilder] actualDataEnd=" + metadata.getActualDataEnd());
        System.out.println("[AnalysisContextBuilder] dataComplete=" + metadata.isDataComplete());

        // 7) 组装
        AnalysisContext ctx = new AnalysisContext(
                task,
                relationship,
                profiles,
                statistics,
                metadata,
                windows,
                tl,
                pats
        );
        System.out.println("[AnalysisContextBuilder] AnalysisContext built. Completed");
        return ctx;
    }

    /**
     * 按 usage_weight 过滤档案字段。
     * <p>
     * NONE：完全剔除（字段不出现，不是 null）。<br>
     * LOW / NORMAL / HIGH：V1 全部保留。<br>
     * ME 与 OTHER 独立过滤，互不影响。
     * </p>
     */
    private ProfileContext filterProfiles(Map<String, String> meRaw, Map<String, String> meWeights,
                                          Map<String, String> otherRaw, Map<String, String> otherWeights) {
        Map<String, String> me = filterOne(meRaw, meWeights, "ME");
        Map<String, String> other = filterOne(otherRaw, otherWeights, "OTHER");
        return new ProfileContext(me, other);
    }

    private Map<String, String> filterOne(Map<String, String> raw, Map<String, String> weights, String owner) {
        Map<String, String> out = new HashMap<>();
        if (raw == null || raw.isEmpty()) return out;
        for (Map.Entry<String, String> e : raw.entrySet()) {
            String field = e.getKey();
            String value = e.getValue();
            if (value == null || value.isEmpty()) continue;
            String w = weights == null ? null : weights.get(field);
            if (w != null && "NONE".equalsIgnoreCase(w)) {
                System.out.println("[AnalysisContextBuilder] profile field excluded by AI permission: owner="
                        + owner + ", field=" + field);
                continue;
            }
            out.put(field, value);
        }
        return out;
    }
}
