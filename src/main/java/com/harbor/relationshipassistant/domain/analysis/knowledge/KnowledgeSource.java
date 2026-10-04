package com.harbor.relationshipassistant.domain.analysis.knowledge;

/** 知识来源元数据。 */
public final class KnowledgeSource {

    private final String sourceId;
    private final String name;
    private final String license;
    private final String url;
    private final String note;

    public KnowledgeSource(String sourceId, String name, String license, String url, String note) {
        this.sourceId = sourceId;
        this.name = name;
        this.license = license;
        this.url = url;
        this.note = note;
    }

    public String getSourceId() { return sourceId; }
    public String getName() { return name; }
    public String getLicense() { return license; }
    public String getUrl() { return url; }
    public String getNote() { return note; }
}
