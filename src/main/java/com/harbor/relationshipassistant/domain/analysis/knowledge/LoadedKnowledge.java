package com.harbor.relationshipassistant.domain.analysis.knowledge;

/**
 * 已经加载到内存的知识正文。运行时对象，不是数据库 Entity。
 * content 为 Markdown 原文，不做摘要/改写/翻译。
 */
public final class LoadedKnowledge {

    private final KnowledgeDefinition definition;
    private final String content;

    public LoadedKnowledge(KnowledgeDefinition definition, String content) {
        this.definition = definition;
        this.content = content == null ? "" : content;
    }

    public KnowledgeDefinition getDefinition() { return definition; }
    public String getKnowledgeId() { return definition.getKnowledgeId(); }
    public String getTitle() { return definition.getTitle(); }
    public String getContent() { return content; }
    public KnowledgeSource getSource() { return definition.getSource(); }
}
