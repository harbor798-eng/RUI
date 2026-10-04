package com.harbor.relationshipassistant.domain.analysis.deep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 一段关系变化（AI observation，不是 Fact）。 */
public final class RelationshipChange {

    private final String type;
    private final String description;
    private final List<String> evidenceIds;
    private final double confidence;

    public RelationshipChange(String type, String description, List<String> evidenceIds, double confidence) {
        this.type = Objects.requireNonNull(type);
        this.description = Objects.requireNonNull(description);
        this.evidenceIds = immutable(evidenceIds);
        this.confidence = confidence;
    }

    public String getType() { return type; }
    public String getDescription() { return description; }
    public List<String> getEvidenceIds() { return evidenceIds; }
    public double getConfidence() { return confidence; }

    private static List<String> immutable(List<String> src) {
        if (src == null || src.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(src));
    }
}
