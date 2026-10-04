package com.harbor.relationshipassistant.application.knowledge;

import java.util.List;

/**
 * 一次知识检索结果。items 不可修改。
 * enabled=false 表示任务不允许使用知识；enabled=true+empty 表示允许但未命中。
 */
public final class KnowledgeResult {
    private final boolean enabled;
    private final String query;
    private final List<KnowledgeItem> items;

    private KnowledgeResult(boolean enabled, String query, List<KnowledgeItem> items) {
        this.enabled = enabled;
        this.query = query;
        this.items = items == null ? List.of() : List.copyOf(items);
    }

    public static KnowledgeResult disabled() {
        return new KnowledgeResult(false, null, List.of());
    }

    public static KnowledgeResult of(String query, List<KnowledgeItem> items) {
        return new KnowledgeResult(true, query, items);
    }

    public boolean isEnabled() { return enabled; }
    public String getQuery() { return query; }
    public List<KnowledgeItem> getItems() { return items; }
}
