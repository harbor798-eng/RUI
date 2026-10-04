package com.harbor.relationshipassistant.domain.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 从证据中发现的、与用户自身行为相关的问题。
 * <p>
 * 只有在有证据支持时才出现，不是强制给用户找错。
 * 例："在对方连续两次抛出话题后，你连续用很短的回复结束了话题。"
 * </p>
 */
public final class UserIssue {

    private final String content;
    private final IssueSeverity severity;
    private final List<String> evidenceIds;
    private final double confidence;

    public UserIssue(String content,
                     IssueSeverity severity,
                     List<String> evidenceIds,
                     double confidence) {
        this.content = Objects.requireNonNull(content, "content");
        this.severity = Objects.requireNonNull(severity, "severity");
        this.evidenceIds = (evidenceIds == null || evidenceIds.isEmpty())
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(evidenceIds));
        this.confidence = confidence;
    }

    public String getContent() { return content; }
    public IssueSeverity getSeverity() { return severity; }
    public List<String> getEvidenceIds() { return evidenceIds; }
    public double getConfidence() { return confidence; }
}
