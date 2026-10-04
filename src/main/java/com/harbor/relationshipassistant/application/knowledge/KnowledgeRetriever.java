package com.harbor.relationshipassistant.application.knowledge;

import java.util.List;

/**
 * 知识检索器抽象。本阶段不绑定向量数据库。
 */
public interface KnowledgeRetriever {
    List<KnowledgeItem> retrieve(KnowledgeRequest request);
}
