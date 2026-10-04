package com.harbor.relationshipassistant.domain.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 基于当前分析结果给用户的下一步建议。
 * <p>
 * 建议必须尽量能回溯到分析证据，不能凭空给出。
 * </p>
 */
public final class Recommendation {

    private final String content;
    private final List<String> reasonEvidenceIds;
    private final RecommendationPriority priority;

    public Recommendation(String content,
                         List<String> reasonEvidenceIds,
                         RecommendationPriority priority) {
        this.content = Objects.requireNonNull(content, "content");
        this.reasonEvidenceIds = (reasonEvidenceIds == null || reasonEvidenceIds.isEmpty())
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(reasonEvidenceIds));
        this.priority = Objects.requireNonNull(priority, "priority");
    }

    public String getContent() { return content; }
    public List<String> getReasonEvidenceIds() { return reasonEvidenceIds; }
    public RecommendationPriority getPriority() { return priority; }
}
