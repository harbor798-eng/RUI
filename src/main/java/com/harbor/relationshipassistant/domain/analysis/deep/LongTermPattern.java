package com.harbor.relationshipassistant.domain.analysis.deep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 长期行为模式（基于 PatternCandidate 的归纳，不是人格标签）。 */
public final class LongTermPattern {

    private final String patternType;
    private final String description;
    private final List<String> evidenceIds;
    private final List<String> patternIds;
    private final double confidence;

    public LongTermPattern(String patternType, String description,
                           List<String> evidenceIds, List<String> patternIds, double confidence) {
        this.patternType = Objects.requireNonNull(patternType);
        this.description = Objects.requireNonNull(description);
        this.evidenceIds = immutable(evidenceIds);
        this.patternIds = immutable(patternIds);
        this.confidence = confidence;
    }

    public String getPatternType() { return patternType; }
    public String getDescription() { return description; }
    public List<String> getEvidenceIds() { return evidenceIds; }
    public List<String> getPatternIds() { return patternIds; }
    public double getConfidence() { return confidence; }

    private static List<String> immutable(List<String> src) {
        if (src == null || src.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(src));
    }
}
