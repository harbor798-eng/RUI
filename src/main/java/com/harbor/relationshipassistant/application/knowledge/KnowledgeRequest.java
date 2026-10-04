package com.harbor.relationshipassistant.application.knowledge;

/**
 * 一次知识检索请求。不保存聊天记录、Entity。
 */
public final class KnowledgeRequest {
    public static final int DEFAULT_MAX = 3;
    public static final int HARD_MAX = 10;

    private final String query;
    private final int maxResults;
    private final String category;

    public KnowledgeRequest(String query, Integer maxResults, String category) {
        this.query = query;
        int m = maxResults == null ? DEFAULT_MAX : maxResults;
        if (m <= 0) m = DEFAULT_MAX;
        if (m > HARD_MAX) m = HARD_MAX;
        this.maxResults = m;
        this.category = category;
    }

    public String getQuery() { return query; }
    public int getMaxResults() { return maxResults; }
    public String getCategory() { return category; }
}
