package com.harbor.relationshipassistant.domain.relationship;

import java.time.LocalDateTime;

/** Relationship 根实体（技术设计 §11）。 */
public class Relationship {
    private Long id;
    private String name;
    private String myName;
    private RelationshipStage currentStage;
    private String avatarPath;
    private RelationshipStatus status;
    private String goalNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static Relationship createNew(String name, String myName, RelationshipStage stage) {
        Relationship r = new Relationship();
        r.name = name;
        r.myName = myName;
        r.currentStage = stage == null ? RelationshipStage.INITIAL_CONTACT : stage;
        r.status = RelationshipStatus.ACTIVE;
        return r;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMyName() { return myName; }
    public void setMyName(String myName) { this.myName = myName; }
    public RelationshipStage getCurrentStage() { return currentStage; }
    public void setCurrentStage(RelationshipStage currentStage) { this.currentStage = currentStage; }
    public String getAvatarPath() { return avatarPath; }
    public void setAvatarPath(String avatarPath) { this.avatarPath = avatarPath; }
    public RelationshipStatus getStatus() { return status; }
    public void setStatus(RelationshipStatus status) { this.status = status; }
    public String getGoalNote() { return goalNote; }
    public void setGoalNote(String goalNote) { this.goalNote = goalNote; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
