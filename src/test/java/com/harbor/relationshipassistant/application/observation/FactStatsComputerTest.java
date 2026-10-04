package com.harbor.relationshipassistant.application.observation;

import com.harbor.relationshipassistant.domain.observation.FactStats;
import com.harbor.relationshipassistant.domain.observation.ObservationBatchChatSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FactStatsComputerTest {

    private final FactStatsComputer c = new FactStatsComputer(null);

    private ObservationBatchChatSnapshot row(String sender, String type, LocalDateTime t) {
        ObservationBatchChatSnapshot r = new ObservationBatchChatSnapshot();
        r.setSenderType(sender);
        r.setMessageType(type);
        r.setMessageTime(t);
        r.setContent("x");
        return r;
    }

    @Test
    void emptySnapshot() {
        FactStats s = c.computeFrom(new ArrayList<>());
        assertEquals(0, s.totalMessages());
        assertEquals(0, s.activeDays());
        assertNull(s.myReplyLatencyHoursMedian());
    }

    @Test
    void onlyMe() {
        List<ObservationBatchChatSnapshot> rows = List.of(
                row("ME", "TEXT", LocalDateTime.of(2026, 9, 1, 10, 0)),
                row("ME", "TEXT", LocalDateTime.of(2026, 9, 1, 11, 0)));
        FactStats s = c.computeFrom(rows);
        assertEquals(2, s.meCount());
        assertEquals(0, s.otherCount());
        assertEquals(1.0, s.meRatio(), 1e-9);
        assertEquals(0, s.otherReplySampleCount());
    }

    @Test
    void bothSidesRatiosAndTypes() {
        List<ObservationBatchChatSnapshot> rows = List.of(
                row("ME", "TEXT", LocalDateTime.of(2026, 9, 1, 10, 0)),
                row("OTHER", "EMOJI", LocalDateTime.of(2026, 9, 1, 10, 30)),
                row("OTHER", "TEXT", LocalDateTime.of(2026, 9, 2, 9, 0)));
        FactStats s = c.computeFrom(rows);
        assertEquals(3, s.totalMessages());
        assertEquals(1, s.meCount());
        assertEquals(2, s.otherCount());
        assertEquals(2, s.activeDays());
        assertEquals(2, s.messageTypeCounts().get("TEXT"));
        assertEquals(1, s.messageTypeCounts().get("EMOJI"));
        // 排序后：ME(9/1 10:00) → OTHER(9/1 10:30) 间隔0.5h（对方回复我）
        //        → OTHER(9/2 09:00) 同发送者，不计
        assertEquals(0.5, s.otherReplyLatencyHoursMedian(), 1e-6);
        assertEquals(1, s.otherReplySampleCount());
        assertEquals(0, s.myReplySampleCount());
    }

    @Test
    void sessionInitiationGap() {
        List<ObservationBatchChatSnapshot> rows = List.of(
                row("OTHER", "TEXT", LocalDateTime.of(2026, 9, 1, 10, 0)),
                // 5 小时间隔 → 新会话，发起者是 ME
                row("ME", "TEXT", LocalDateTime.of(2026, 9, 1, 15, 0)));
        FactStats s = c.computeFrom(rows);
        assertEquals(1, s.meStartedSessions());
        assertEquals(0, s.otherStartedSessions());
    }

    @Test
    void withinGapIsNotNewSession() {
        List<ObservationBatchChatSnapshot> rows = List.of(
                row("OTHER", "TEXT", LocalDateTime.of(2026, 9, 1, 10, 0)),
                row("ME", "TEXT", LocalDateTime.of(2026, 9, 1, 12, 0))); // 2h < 4h
        FactStats s = c.computeFrom(rows);
        assertEquals(0, s.meStartedSessions());
        assertEquals(0, s.otherStartedSessions());
    }
}
