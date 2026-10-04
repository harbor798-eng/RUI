package com.harbor.relationshipassistant.domain.observation;

import java.time.LocalDateTime;

/** 观察依据快照：AI 当时实际看到的证据文本。 */
public class ObservationEvidence {
    private Long id;
    private Long observationId;
    private String evidenceType; // CHAT_STAT / CHAT_SNIPPET / PROFILE / BEHAVIOR
    private String evidenceSnapshot;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getObservationId() { return observationId; }
    public void setObservationId(Long v) { this.observationId = v; }
    public String getEvidenceType() { return evidenceType; }
    public void setEvidenceType(String v) { this.evidenceType = v; }
    public String getEvidenceSnapshot() { return evidenceSnapshot; }
    public void setEvidenceSnapshot(String v) { this.evidenceSnapshot = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
