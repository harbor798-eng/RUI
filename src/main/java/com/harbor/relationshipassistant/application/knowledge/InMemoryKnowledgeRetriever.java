package com.harbor.relationshipassistant.application.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * V1 测试用内存 Retriever：简单关键词匹配 title/content/category。
 * 不是最终方案；未来可替换为 VectorRetriever 而不改变 Router API。
 */
public final class InMemoryKnowledgeRetriever implements KnowledgeRetriever {
    private final List<KnowledgeItem> store;

    public InMemoryKnowledgeRetriever(List<KnowledgeItem> seed) {
        this.store = List.copyOf(seed);
    }

    @Override
    public List<KnowledgeItem> retrieve(KnowledgeRequest request) {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            return List.of();
        }
        String q = request.getQuery().toLowerCase(Locale.ROOT);
        String cat = request.getCategory() == null ? null : request.getCategory().toLowerCase(Locale.ROOT);
        List<KnowledgeItem> out = new ArrayList<>();
        for (KnowledgeItem item : store) {
            if (cat != null && item.getCategory() != null
                    && !item.getCategory().toLowerCase(Locale.ROOT).contains(cat)) {
                continue;
            }
            String hay = ((item.getTitle() == null ? "" : item.getTitle()) + " "
                    + (item.getContent() == null ? "" : item.getContent()) + " "
                    + (item.getCategory() == null ? "" : item.getCategory())).toLowerCase(Locale.ROOT);
            if (containsAnyToken(hay, q)) {
                out.add(item);
                if (out.size() >= request.getMaxResults()) break;
            }
        }
        return out;
    }

    private static boolean containsAnyToken(String hay, String query) {
        for (String token : query.split("\\s+")) {
            if (!token.isBlank() && hay.contains(token)) return true;
        }
        return false;
    }
}
