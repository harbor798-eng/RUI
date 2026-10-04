package com.harbor.relationshipassistant.application.knowledge;

import com.harbor.relationshipassistant.application.skill.context.AnalysisTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 知识路由：根据 AnalysisTask.useKnowledge 决定是否检索。
 * 不分析聊天、不调 LLM、不缓存、不修改 AnalysisContext。
 */
public final class KnowledgeRouter {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeRouter.class);
    public static final int MAX_ITEMS = 3;

    private final KnowledgeRetriever retriever;

    public KnowledgeRouter(KnowledgeRetriever retriever) {
        this.retriever = retriever;
    }

    public KnowledgeResult retrieve(AnalysisTask task, KnowledgeRequest request) {
        if (task == null || !task.isUseKnowledge()) {
            log.info("[KNOWLEDGE-ROUTER] disabled by task");
            return KnowledgeResult.disabled();
        }
        if (request == null) {
            log.info("[KNOWLEDGE-ROUTER] null request, return enabled empty");
            return KnowledgeResult.of(null, List.of());
        }
        log.info("[KNOWLEDGE-ROUTER] query={} maxResults={}", request.getQuery(), request.getMaxResults());
        List<KnowledgeItem> items;
        try {
            items = retriever.retrieve(request);
        } catch (Throwable t) {
            log.warn("[KNOWLEDGE-ROUTER] retrieve failed: {}", t.toString());
            return KnowledgeResult.of(request.getQuery(), List.of());
        }
        if (items == null) items = List.of();
        int n = Math.min(items.size(), MAX_ITEMS);
        List<KnowledgeItem> limited = items.subList(0, n);
        log.info("[KNOWLEDGE-ROUTER] matched={}", limited.size());
        return KnowledgeResult.of(request.getQuery(), limited);
    }
}
