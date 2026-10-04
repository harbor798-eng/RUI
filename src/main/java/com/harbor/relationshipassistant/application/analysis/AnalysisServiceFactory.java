package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.domain.analysis.knowledge.KnowledgeRegistry;
import com.harbor.relationshipassistant.infrastructure.ai.AIProvider;
import com.harbor.relationshipassistant.infrastructure.persistence.ChatMessageRepository;
import com.harbor.relationshipassistant.infrastructure.persistence.DataSourceFactory;

/**
 * 手工组装 AnalysisService（不引入 Spring）。
 * 仅用于 UI 启动时一次性构建。
 */
public final class AnalysisServiceFactory {

    private AnalysisServiceFactory() {}

    public static AnalysisService build(DataSourceFactory ds, AIProvider provider) {
        ChatMessageRepository chatRepo = new ChatMessageRepository(ds);
        KnowledgeRegistry registry = new KnowledgeRegistry();
        return new AnalysisService(
                new ChatProcessor(chatRepo),
                new StatisticsCalculator(),
                new ConversationSessionBuilder(),
                new InteractionStatisticsCalculator(),
                new EvidenceDetector(),
                new EvidenceProcessor(),
                new PatternDetector(),
                new AnalysisContextBuilder(),
                new SkillRouter(),
                new AnalysisNeedDetector(),
                new KnowledgeRouter(),
                new KnowledgeLoader(registry),
                registry,
                new PromptAssembler(),
                new AnalysisResultExecutor(new AnalysisExecutor(provider))
        );
    }
}
