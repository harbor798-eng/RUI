package com.harbor.relationshipassistant.domain.analysis.need;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 一次分析任务中"需要进一步关注/解释"的方向。
 * <p>
 * 运行时中间对象，不是数据库 Entity，不是 AI 结论，不是关系评分。
 * 所有 evidenceIds / patternIds 必须来自当前 AnalysisContext。
 * </p>
 */
public final class AnalysisNeed {

    private final NeedType needType;
    private final NeedPriority priority;
    private final String reason;
    private final List<String> evidenceIds;
    private final List<String> patternIds;

    public AnalysisNeed(NeedType needType,
                        NeedPriority priority,
                        String reason,
                        List<String> evidenceIds,
                        List<String> patternIds) {
        this.needType = Objects.requireNonNull(needType, "needType");
        this.priority = Objects.requireNonNull(priority, "priority");
        this.reason = reason;
        this.evidenceIds = immutable(evidenceIds);
        this.patternIds = immutable(patternIds);
    }

    private static List<String> immutable(List<String> src) {
        if (src == null || src.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(src));
    }

    public NeedType getNeedType() { return needType; }
    public NeedPriority getPriority() { return priority; }
    public String getReason() { return reason; }
    public List<String> getEvidenceIds() { return evidenceIds; }
    public List<String> getPatternIds() { return patternIds; }
}
