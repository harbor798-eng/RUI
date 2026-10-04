package com.harbor.relationshipassistant.domain.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 一次 AI 分析任务最终产生的结构化分析结果。
 * <p>
 * 这是运行时 Domain Model：由 LLM JSON 解析后填充。
 * <b>不是</b>数据库表 {@code analysis_result}（KV 存储）的 Entity。
 * </p>
 * <p>
 * 当前批次只包含基础分析结果；Deep Observation 专属字段
 * （timeline、relationshipChanges、longTermPatterns、theoryExplanations）
 * 留到下一阶段扩展，本批不提前创建。
 * </p>
 */
public final class AnalysisResult implements AnalysisOutput {

    private final AnalysisResultMetadata metadata;
    private final List<Fact> facts;
    private final List<AnalysisObservation> observations;
    private final List<Possibility> possibilities;
    private final List<Unknown> unknowns;
    private final List<EmotionObservation> emotions;
    private final List<UserIssue> userIssues;
    private final List<Recommendation> recommendations;

    public AnalysisResult(AnalysisResultMetadata metadata,
                         List<Fact> facts,
                         List<AnalysisObservation> observations,
                         List<Possibility> possibilities,
                         List<Unknown> unknowns,
                         List<EmotionObservation> emotions,
                         List<UserIssue> userIssues,
                         List<Recommendation> recommendations) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.facts = immutable(facts);
        this.observations = immutable(observations);
        this.possibilities = immutable(possibilities);
        this.unknowns = immutable(unknowns);
        this.emotions = immutable(emotions);
        this.userIssues = immutable(userIssues);
        this.recommendations = immutable(recommendations);
    }

    private static <T> List<T> immutable(List<T> src) {
        if (src == null || src.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(src));
    }

    public AnalysisResultMetadata getMetadata() { return metadata; }
    public List<Fact> getFacts() { return facts; }
    public List<AnalysisObservation> getObservations() { return observations; }
    public List<Possibility> getPossibilities() { return possibilities; }
    public List<Unknown> getUnknowns() { return unknowns; }
    public List<EmotionObservation> getEmotions() { return emotions; }
    public List<UserIssue> getUserIssues() { return userIssues; }
    public List<Recommendation> getRecommendations() { return recommendations; }
}
