package com.harbor.relationshipassistant.domain.analysis.knowledge;

import com.harbor.relationshipassistant.domain.analysis.EvidenceType;
import com.harbor.relationshipassistant.domain.analysis.Skill;
import com.harbor.relationshipassistant.domain.analysis.need.NeedType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 一份知识的元数据定义。<b>不是</b>知识正文，不是 Prompt，不是数据库 Entity。
 * 正文由后续 KnowledgeLoader 按需加载；本类只描述"这份知识是什么、适用于什么"。
 */
public final class KnowledgeDefinition {

    private final String knowledgeId;
    private final String file;
    private final String title;
    private final String description;
    private final List<String> topics;
    private final Set<NeedType> needTypes;
    private final Set<EvidenceType> evidenceTypes;
    private final Set<Skill> supportedSkills;
    private final Set<String> riskTags;
    private final KnowledgeSource source;

    public KnowledgeDefinition(String knowledgeId,
                               String file,
                               String title,
                               String description,
                               List<String> topics,
                               Set<NeedType> needTypes,
                               Set<EvidenceType> evidenceTypes,
                               Set<Skill> supportedSkills,
                               Set<String> riskTags,
                               KnowledgeSource source) {
        this.knowledgeId = Objects.requireNonNull(knowledgeId, "knowledgeId");
        this.file = Objects.requireNonNull(file, "file");
        this.title = Objects.requireNonNull(title, "title");
        this.description = description;
        this.topics = immutable(topics);
        this.needTypes = immutable(needTypes);
        this.evidenceTypes = immutable(evidenceTypes);
        // supportedSkills 空 = GLOBAL，所有 Skill 可用
        this.supportedSkills = immutable(supportedSkills);
        this.riskTags = immutable(riskTags);
        this.source = source;
    }

    private static <T> Set<T> immutable(Set<T> src) {
        if (src == null || src.isEmpty()) return Collections.emptySet();
        return Collections.unmodifiableSet(src);
    }

    private static <T> List<T> immutable(List<T> src) {
        if (src == null || src.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(src));
    }

    public String getKnowledgeId() { return knowledgeId; }
    public String getFile() { return file; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public List<String> getTopics() { return topics; }
    public Set<NeedType> getNeedTypes() { return needTypes; }
    public Set<EvidenceType> getEvidenceTypes() { return evidenceTypes; }
    public Set<Skill> getSupportedSkills() { return supportedSkills; }
    public Set<String> getRiskTags() { return riskTags; }
    public KnowledgeSource getSource() { return source; }
}
