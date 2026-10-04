package com.harbor.relationshipassistant.application.skill.context;

public final class ParticipantContext {
    private final String userName;
    private final String otherName;
    private final String userProfileSummary;
    private final String otherProfileSummary;

    public ParticipantContext(String userName, String otherName, String userProfileSummary, String otherProfileSummary) {
        this.userName = userName;
        this.otherName = otherName;
        this.userProfileSummary = userProfileSummary;
        this.otherProfileSummary = otherProfileSummary;
    }

    public String getUserName() { return userName; }
    public String getOtherName() { return otherName; }
    public String getUserProfileSummary() { return userProfileSummary; }
    public String getOtherProfileSummary() { return otherProfileSummary; }
}
