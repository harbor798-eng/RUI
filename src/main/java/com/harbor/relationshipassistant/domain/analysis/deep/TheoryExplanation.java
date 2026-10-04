package com.harbor.relationshipassistant.domain.analysis.deep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 理论解释（可选解释框架，不是事实、不是诊断）。
 * knowledgeIds 必须来自本次 KnowledgeRouter 选中的集合。
 */
public final class TheoryExplanation {

    private final String title;
    private final String explanation;
    private final List<String> knowledgeIds;
    private final List<String> evidenceIds;

    public TheoryExplanation(String title, String explanation,
                             List<String> knowledgeIds, List<String> evidenceIds) {
        this.title = Objects.requireNonNull(title);
        this.explanation = Objects.requireNonNull(explanation);
        this.knowledgeIds = immutable(knowledgeIds);
        this.evidenceIds = immutable(evidenceIds);
    }

    public String getTitle() { return title; }
    public String getExplanation() { return explanation; }
    public List<String> getKnowledgeIds() { return knowledgeIds; }
    public List<String> getEvidenceIds() { return evidenceIds; }

    private static List<String> immutable(List<String> src) {
        if (src == null || src.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(src));
    }
}
