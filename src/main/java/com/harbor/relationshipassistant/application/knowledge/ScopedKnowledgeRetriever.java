package com.harbor.relationshipassistant.application.knowledge;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Scoped knowledge retriever: only searches within selected document IDs.
 * Deterministic keyword/bigram scoring. Does NOT inject into Prompt.
 */
public final class ScopedKnowledgeRetriever {

    public record RetrievedDocument(String documentId, String title, double score,
                                    List<String> matchedTerms, String content) {}

    public record RetrievalResult(List<RetrievedDocument> results, int candidateCount,
                                    String query, Set<String> scopeDocumentIds) {}

    private final List<KnowledgeItem> catalog;

    public ScopedKnowledgeRetriever(List<KnowledgeItem> catalog) {
        this.catalog = List.copyOf(catalog);
    }

    public RetrievalResult retrieve(String query, Set<String> scopeDocumentIds, int maxResults, double minScore) {
        if (query == null || query.isBlank() || scopeDocumentIds == null || scopeDocumentIds.isEmpty()) {
            return new RetrievalResult(List.of(), scopeDocumentIds == null ? 0 : scopeDocumentIds.size(), query, scopeDocumentIds);
        }
        String q = query.toLowerCase(Locale.ROOT);
        List<String> queryTerms = tokenize(q);
        List<RetrievedDocument> scored = new java.util.ArrayList<>();
        int candidateCount = 0;
        for (KnowledgeItem item : catalog) {
            if (!scopeDocumentIds.contains(item.getId())) continue;
            candidateCount++;
            String hay = ((item.getTitle() == null ? "" : item.getTitle()) + " "
                    + (item.getContent() == null ? "" : item.getContent())).toLowerCase(Locale.ROOT);
            double score = 0;
            java.util.List<String> matched = new java.util.ArrayList<>();
            for (String term : queryTerms) {
                if (term.isBlank()) continue;
                int hits = countOccurrences(hay, term);
                if (hits > 0) {
                    double weight = hay.startsWith(term) ? 2.0 : 1.0;
                    score += hits * weight;
                    matched.add(term);
                }
                // bigram for Chinese
                if (term.length() >= 2) {
                    for (int i = 0; i < term.length() - 1; i++) {
                        String bg = term.substring(i, i + 2);
                        if (bg.chars().anyMatch(c -> c >= 128)) {
                            int bh = countOccurrences(hay, bg);
                            if (bh > 0 && !matched.contains(bg)) {
                                score += bh * 0.5;
                                matched.add(bg);
                            }
                        }
                    }
                }
            }
            if (score >= minScore) {
                scored.add(new RetrievedDocument(item.getId(), item.getTitle(), score, matched, item.getContent()));
            }
        }
        scored.sort((a, b) -> {
            int c = Double.compare(b.score(), a.score());
            return c != 0 ? c : a.documentId().compareTo(b.documentId());
        });
        if (scored.size() > maxResults) scored = new java.util.ArrayList<>(scored.subList(0, maxResults));
        return new RetrievalResult(scored, candidateCount, query, scopeDocumentIds);
    }

    private static List<String> tokenize(String q) {
        return List.of(q.split("\\s+"));
    }

    private static int countOccurrences(String hay, String needle) {
        if (needle.isEmpty()) return 0;
        int count = 0, idx = 0;
        while ((idx = hay.indexOf(needle, idx)) != -1) { count++; idx += needle.length(); }
        return count;
    }
}
