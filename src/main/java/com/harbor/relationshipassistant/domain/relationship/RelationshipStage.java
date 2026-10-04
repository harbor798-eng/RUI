package com.harbor.relationshipassistant.domain.relationship;

/** 关系阶段（PRD §4.2），用户手动设置，AI 不可自动改。 */
public enum RelationshipStage {
    INITIAL_CONTACT("初识"),
    AMBIGUOUS("暧昧"),
    DATING("恋爱中"),
    BROKEN_UP("分手"),
    RECONNECTED("重新联系");

    private final String label;

    RelationshipStage(String label) { this.label = label; }
    public String getLabel() { return label; }
}
