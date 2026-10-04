package com.harbor.relationshipassistant.domain.analysis;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 运行时分析时间线事件。
 * <p>
 * 这是 AI / 程序识别出的关系发展节点。
 * <b>不是</b>数据库表 {@code timeline_event} 的 Entity。
 * </p>
 * <p>
 * ID 语义：
 * <ul>
 *   <li>{@code eventId}：本 TimelineEvent 自己的 ID。</li>
 *   <li>{@code evidenceIds}：{@link EvidenceWindow#getEvidenceId()}（String，EV-xxx），
 *       不是 ChatMessage 的 messageId。MessageId 通过 EvidenceWindow.messageIds 间接追溯。</li>
 * </ul>
 * </p>
 */
public final class TimelineEvent {

    private final String eventId;
    private final LocalDateTime time;
    private final String type;
    private final String description;
    private final List<String> evidenceIds;

    public TimelineEvent(String eventId,
                         LocalDateTime time,
                         String type,
                         String description,
                         List<String> evidenceIds) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.time = time;
        this.type = Objects.requireNonNull(type, "type");
        this.description = description;
        this.evidenceIds = (evidenceIds == null || evidenceIds.isEmpty())
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(evidenceIds));
    }

    public String getEventId() { return eventId; }
    public LocalDateTime getTime() { return time; }
    public String getType() { return type; }
    public String getDescription() { return description; }
    /** @return EvidenceWindow IDs (EV-xxx), not message IDs. */
    public List<String> getEvidenceIds() { return evidenceIds; }
}
