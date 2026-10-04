package com.harbor.relationshipassistant.application.observation;

import com.harbor.relationshipassistant.domain.observation.FactStats;
import com.harbor.relationshipassistant.domain.observation.ObservationBatchChatSnapshot;
import com.harbor.relationshipassistant.infrastructure.persistence.ObservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 2：把 Batch 快照转为客观事实统计。
 * 数据来源严格限定为 observation_batch_chat_snapshot，不回查 chat_message。
 */
public class FactStatsComputer {

    private static final Logger log = LoggerFactory.getLogger(FactStatsComputer.class);
    private final ObservationRepository repo;

    public FactStatsComputer(ObservationRepository repo) { this.repo = repo; }

    public FactStats compute(long batchId) {
        log.info("[OBS_FACT_STATS_START] batchId={}", batchId);
        List<ObservationBatchChatSnapshot> rows = repo.listSnapshots(batchId);
        FactStats s = computeFrom(rows);
        log.info("[OBS_FACT_STATS_COMPLETE] batchId={} total={} me={} other={} activeDays={}",
                batchId, s.totalMessages(), s.meCount(), s.otherCount(), s.activeDays());
        return s;
    }

    /** 纯计算入口，便于单测。 */
    public FactStats computeFrom(List<ObservationBatchChatSnapshot> rows) {
        long me = 0, other = 0, system = 0;
        Map<String, Long> typeCounts = new HashMap<>();
        Set<LocalDate> activeDays = new HashSet<>();
        List<Double> myReplyGaps = new ArrayList<>();
        List<Double> otherReplyGaps = new ArrayList<>();
        long meStarted = 0, otherStarted = 0;

        List<ObservationBatchChatSnapshot> sorted = new ArrayList<>(rows);
        sorted.sort((a, b) -> {
            LocalDateTime ta = a.getMessageTime(), tb = b.getMessageTime();
            if (ta == null && tb == null) return Long.compare(safeId(a), safeId(b));
            if (ta == null) return -1;
            if (tb == null) return 1;
            int c = ta.compareTo(tb);
            return c != 0 ? c : Long.compare(safeId(a), safeId(b));
        });

        ObservationBatchChatSnapshot prev = null;
        for (ObservationBatchChatSnapshot r : sorted) {
            String sender = r.getSenderType() == null ? "UNKNOWN" : r.getSenderType();
            switch (sender) {
                case "ME" -> me++;
                case "OTHER" -> other++;
                default -> system++;
            }
            typeCounts.merge(r.getMessageType() == null ? "UNKNOWN" : r.getMessageType(), 1L, Long::sum);
            if (r.getMessageTime() != null) activeDays.add(r.getMessageTime().toLocalDate());

            if (prev != null && prev.getMessageTime() != null && r.getMessageTime() != null) {
                double gapHours = Duration.between(prev.getMessageTime(), r.getMessageTime()).toMillis() / 3600000.0;
                String prevSender = prev.getSenderType();
                if (!sender.equals(prevSender)) {
                    if (sender.equals("ME")) myReplyGaps.add(gapHours);
                    else if (sender.equals("OTHER")) otherReplyGaps.add(gapHours);
                }
                if (gapHours > FactStats.INITIATION_GAP_HOURS) {
                    if (sender.equals("ME")) meStarted++;
                    else if (sender.equals("OTHER")) otherStarted++;
                }
            }
            prev = r;
        }

        long total = rows.size();
        log.info("[OBS_FACT_STATS_CHAT_COUNT] total={} me={} other={} system={}", total, me, other, system);
        log.info("[OBS_FACT_STATS_MESSAGE_TYPE] {}", typeCounts);
        log.info("[OBS_FACT_STATS_ACTIVE_DAYS] {}", activeDays.size());
        log.info("[OBS_FACT_STATS_REPLY_INTERVAL] mySamples={} otherSamples={}", myReplyGaps.size(), otherReplyGaps.size());

        return new FactStats(
                total, me, other, system,
                total == 0 ? 0 : (double) me / total,
                total == 0 ? 0 : (double) other / total,
                activeDays.size(),
                typeCounts,
                median(myReplyGaps), median(otherReplyGaps),
                myReplyGaps.size(), otherReplyGaps.size(),
                meStarted, otherStarted,
                FactStats.INITIATION_GAP_HOURS);
    }

    private static long safeId(ObservationBatchChatSnapshot s) {
        return s.getId() == null ? 0 : s.getId();
    }

    private static Double median(List<Double> v) {
        if (v.isEmpty()) return null;
        List<Double> c = new ArrayList<>(v);
        c.sort(Double::compare);
        int n = c.size();
        return n % 2 == 1 ? c.get(n / 2) : (c.get(n / 2 - 1) + c.get(n / 2)) / 2.0;
    }
}
