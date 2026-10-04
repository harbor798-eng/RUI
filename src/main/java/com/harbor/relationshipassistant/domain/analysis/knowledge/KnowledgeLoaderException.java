package com.harbor.relationshipassistant.domain.analysis.knowledge;

/** 知识加载阶段的明确业务异常。 */
public class KnowledgeLoaderException extends RuntimeException {

    public KnowledgeLoaderException(String message) { super(message); }

    public KnowledgeLoaderException(String message, Throwable cause) { super(message, cause); }
}
