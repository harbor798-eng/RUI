package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.domain.analysis.EvidenceLevel;
import com.harbor.relationshipassistant.domain.analysis.EvidenceType;
import com.harbor.relationshipassistant.domain.analysis.EvidenceWindow;
import com.harbor.relationshipassistant.domain.analysis.TimelineEvent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 程序侧 Timeline 候选生成：从 EvidenceWindow 提取值得进入时间线的节点。
 * <p>
 * 纯程序逻辑，不做关系判断、不做心理推断；S/A 全部入选，B 仅在覆盖不足时补入关键类型，C 不入选。
 * 同一 EvidenceWindow 多 Type 不重复生成事件。
 * </p>
 */
public class DeepTimelineBuilder {

    private static final Set<EvidenceType> CORE_TYPES = Set.of(
            EvidenceType.RELATIONSHIP_STATUS_CHANGE,
            EvidenceType.RELATIONSHIP_EXPRESSION,
            EvidenceType.CONFLICT,
            EvidenceType.REPAIR,
            EvidenceType.IMPORTANT_ACTION,
            EvidenceType.BOUNDARY_OR_REJECTION,
            EvidenceType.INTERACTION_PATTERN_CHANGE,
            EvidenceType.PERSISTENT_BEHAVIOR
    );

    public List<TimelineEvent> build(List<EvidenceWindow> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            System.out.println("[DeepTimelineBuilder] candidateCount=0 selectedCount=0");
            return new ArrayList<>();
        }
        List<EvidenceWindow> sorted = new ArrayList<>(evidence);
        sorted.sort(Comparator.comparing(EvidenceWindow::getStartTime,
                Comparator.nullsLast(Comparator.naturalOrder())));

        List<TimelineEvent> out = new ArrayList<>();
        Set<String> usedEvidenceIds = new HashSet<>();

        // S/A 全部入选
        for (EvidenceWindow w : sorted) {
            if (w.getLevel() == EvidenceLevel.S || w.getLevel() == EvidenceLevel.A) {
                addEvent(out, usedEvidenceIds, w);
            }
        }
        int sACount = out.size();

        // B：覆盖不足时补入核心类型
        if (out.size() < 4) {
            for (EvidenceWindow w : sorted) {
                if (w.getLevel() != EvidenceLevel.B) continue;
                boolean core = w.getTypes().stream().anyMatch(CORE_TYPES::contains);
                if (core) addEvent(out, usedEvidenceIds, w);
            }
        }
        // C 不入选

        out.sort(Comparator.comparing(TimelineEvent::getTime,
                Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TimelineEvent::getEventId));
        System.out.println("[DeepTimelineBuilder] candidateCount=" + sorted.size()
                + " s/aSelected=" + sACount
                + " totalSelected=" + out.size());
        return out;
    }

    private static void addEvent(List<TimelineEvent> out, Set<String> used, EvidenceWindow w) {
        if (!used.add(w.getEvidenceId())) return;
        String type = w.getTypes().stream()
                .filter(CORE_TYPES::contains)
                .findFirst()
                .map(Enum::name)
                .orElse(w.getTypes().isEmpty() ? "EVENT" : w.getTypes().iterator().next().name());
        LocalDateTime time = w.getStartTime();
        out.add(new TimelineEvent(w.getEvidenceId(), time, type,
                w.getEvidenceId() + " [" + type + "]", List.of(w.getEvidenceId())));
    }
}
