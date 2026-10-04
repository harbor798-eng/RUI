package com.harbor.relationshipassistant.domain.analysis.statistics;

import com.harbor.relationshipassistant.domain.analysis.interaction.InteractionStatistics;

import java.util.Objects;

/**
 * 一次分析任务产生的程序统计结果集合。
 * <p>
 * 同时承载：
 * <ul>
 *   <li>{@link MessageStatistics} —— 基础消息统计（Batch 4，由 StatisticsCalculator 产出）；</li>
 *   <li>{@link InteractionStatistics} —— Session/发起/回复统计（Batch 6，由 InteractionStatisticsCalculator 产出）。</li>
 * </ul>
 * 这是运行时事实容器，不是数据库 Entity。
 * </p>
 * <p>
 * 兼容设计：
 * <ul>
 *   <li>1 参构造器 {@link #StatisticsContext(MessageStatistics)} 仅供 Batch 4
 *       {@code StatisticsCalculator} 使用，它不感知 InteractionStatistics；</li>
 *   <li>2 参构造器 {@link #StatisticsContext(MessageStatistics, InteractionStatistics)}
 *       供上层组装（Batch 10）使用。</li>
 * </ul>
 * </p>
 */
public final class StatisticsContext {

    private final MessageStatistics messageStatistics;
    private final InteractionStatistics interactionStatistics;

    /** Batch 4 兼容构造器：只含 MessageStatistics。 */
    public StatisticsContext(MessageStatistics messageStatistics) {
        this(messageStatistics, null);
    }

    /** Batch 10 组装构造器：同时承载两类统计事实。 */
    public StatisticsContext(MessageStatistics messageStatistics,
                             InteractionStatistics interactionStatistics) {
        this.messageStatistics = Objects.requireNonNull(messageStatistics, "messageStatistics");
        this.interactionStatistics = interactionStatistics;
    }

    public MessageStatistics getMessageStatistics() {
        return messageStatistics;
    }

    public InteractionStatistics getInteractionStatistics() {
        return interactionStatistics;
    }
}
