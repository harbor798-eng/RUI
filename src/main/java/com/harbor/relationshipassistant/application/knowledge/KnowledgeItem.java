package com.harbor.relationshipassistant.application.knowledge;

/**
 * 知识库中的一个知识单元。是 DTO，不是 Entity，不是分析结果。
 */
public final class KnowledgeItem {
    private final String id;
    private final String title;
    private final String content;
    private final String category;
    private final String source;
    private final String location;

    public KnowledgeItem(String id, String title, String content, String category, String source, String location) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.category = category;
        this.source = source;
        this.location = location;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getCategory() { return category; }
    public String getSource() { return source; }
    public String getLocation() { return location; }
}
