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
            if (token.isBlank()) continue;
            if (hay.contains(token)) return true;
            // 中文不分词兜底：把连续非 ASCII 串切成 2-char bigram，提高命中率。
            for (String bigram : toBigrams(token)) {
                if (hay.contains(bigram)) return true;
            }
        }
        return false;
    }

    private static java.util.List<String> toBigrams(String s) {
        // 只对长度≥2 的非 ASCII 串切 bigram；英文/数字 token 保持原样。
        if (s.length() < 2) return java.util.List.of();
        StringBuilder asciiRun = new StringBuilder();
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 128) {
                asciiRun.append(c);
            } else {
                if (asciiRun.length() > 0) { asciiRun.setLength(0); }
            }
        }
        // 直接对整个串做 2-gram（包含跨英文/中文边界），简单粗暴。
        for (int i = 0; i < s.length() - 1; i++) {
            String bg = s.substring(i, i + 2);
            // 跳过纯 ASCII bigram（已经 contains(token) 过了）
            boolean hasNonAscii = false;
            for (int j = 0; j < bg.length(); j++) if (bg.charAt(j) >= 128) hasNonAscii = true;
            if (hasNonAscii) out.add(bg);
        }
        return out;
    }
}
