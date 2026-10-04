package com.harbor.relationshipassistant.domain.ai;

import com.harbor.relationshipassistant.domain.relationship.RelationshipStage;

import java.util.List;

/**
 * 快速回复策略（技术设计 §24）：由关系阶段决定 3 种策略，每种生成 3 条，共 9 条候选。
 */
public enum ReplyStrategy {
    NATURAL("自然"),
    FUN("有趣"),
    PROACTIVE("主动"),
    LIGHT_FLIRT("轻微暧昧"),
    WARM("自然温暖"),
    TEASING("幽默调侃"),
    INTIMATE_FLIRT("亲密暧昧");

    private final String label;

    ReplyStrategy(String label) { this.label = label; }
    public String getLabel() { return label; }

    public static List<ReplyStrategy> strategiesFor(RelationshipStage stage) {
        return switch (stage == null ? RelationshipStage.INITIAL_CONTACT : stage) {
            case INITIAL_CONTACT -> List.of(NATURAL, FUN, PROACTIVE);
            case AMBIGUOUS -> List.of(NATURAL, PROACTIVE, LIGHT_FLIRT);
            case DATING -> List.of(WARM, TEASING, INTIMATE_FLIRT);
            // 分手 / 重新联系 暂退回初识策略集，后续阶段细化
            case BROKEN_UP, RECONNECTED -> List.of(NATURAL, PROACTIVE, FUN);
        };
    }
}
