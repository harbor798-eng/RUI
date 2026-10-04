package com.harbor.relationshipassistant.application.skill.context;

/**
 * 关系上下文 DTO。不做关系推断，只保存已经确定的事实。
 */
public final class RelationshipContext {
    private final Integer relationshipId;
    private final String stage;
    private final String label;

    public RelationshipContext(Integer relationshipId, String stage, String label) {
        this.relationshipId = relationshipId;
        this.stage = stage;
        this.label = label;
    }

    public Integer getRelationshipId() { return relationshipId; }
    public String getStage() { return stage; }
    public String getLabel() { return label; }
}
