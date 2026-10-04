package com.harbor.relationshipassistant.application.ai;

import com.harbor.relationshipassistant.infrastructure.persistence.DataSourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 4：只读行为统计层。
 * 事实层 = ai_generation_record + chat_message；本 Service 只做实时聚合，不落任何统计结果。
 * 只统计 relationship_id 维度，不跨关系、不做趋势、不做 AI 解读。
 */
public class BehaviorStatsService {

    private static final Logger log = LoggerFactory.getLogger(BehaviorStatsService.class);

    private final DataSourceFactory ds;

    public BehaviorStatsService(DataSourceFactory ds) {
        this.ds = ds;
    }

    /** 统计摘要（全部为客观计数，不含任何 AI 判断）。 */
    public record Summary(
            long generationCount,
            long selectedCount,
            Map<String, Long> strategyBreakdown,
            long modifiedCount,
            long unmodifiedCount,
            long aiAssistedSent,
            long directInputSent,
            long usedAndSent,
            long usedButNotSent) {

        public double modificationRate() {
            long base = modifiedCount + unmodifiedCount;
            return base == 0 ? 0.0 : (double) modifiedCount / base;
        }

        public double usedSendRate() {
            long base = usedAndSent + usedButNotSent;
            return base == 0 ? 0.0 : (double) usedAndSent / base;
        }
    }

    public Summary getSummary(long relationshipId) {
        long gen = count("SELECT COUNT(*) FROM ai_generation_record WHERE relationship_id=?", relationshipId);
        long selected = count("SELECT COUNT(*) FROM ai_generation_record WHERE relationship_id=? AND selected_strategy IS NOT NULL", relationshipId);
        long modified = count("SELECT COUNT(*) FROM ai_generation_record WHERE relationship_id=? AND selected_strategy IS NOT NULL AND modified=1", relationshipId);
        long usedSent = count("SELECT COUNT(*) FROM ai_generation_record WHERE relationship_id=? AND selected_strategy IS NOT NULL AND sent_message_id IS NOT NULL", relationshipId);
        long usedNotSent = count("SELECT COUNT(*) FROM ai_generation_record WHERE relationship_id=? AND selected_strategy IS NOT NULL AND sent_message_id IS NULL", relationshipId);
        long aiAssisted = chatCount(relationshipId, "AI_ASSISTED");
        long directInput = chatCount(relationshipId, "APP");
        Map<String, Long> breakdown = strategyBreakdown(relationshipId);

        Summary s = new Summary(gen, selected, breakdown, modified, selected - modified,
                aiAssisted, directInput, usedSent, usedNotSent);
        log.info("[STATS][SUMMARY] relationshipId={} generations={} selected={} modified={} aiSent={} directSent={} usedSent={} usedNotSent={}",
                relationshipId, gen, selected, modified, aiAssisted, directInput, usedSent, usedNotSent);
        return s;
    }

    private long count(String sql, long relationshipId) {
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, relationshipId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (Exception e) {
            throw new IllegalStateException("行为统计查询失败: " + e.getMessage(), e);
        }
    }

    private long chatCount(long relationshipId, String sourceType) {
        String sql = "SELECT COUNT(*) FROM chat_message WHERE relationship_id=? AND source_type=?";
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, relationshipId);
            ps.setString(2, sourceType);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (Exception e) {
            throw new IllegalStateException("发送统计查询失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Long> strategyBreakdown(long relationshipId) {
        Map<String, Long> m = new LinkedHashMap<>();
        String sql = "SELECT selected_strategy, COUNT(*) FROM ai_generation_record " +
                "WHERE relationship_id=? AND selected_strategy IS NOT NULL GROUP BY selected_strategy";
        try (Connection c = ds.newConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, relationshipId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) m.put(rs.getString(1), rs.getLong(2));
            }
        } catch (Exception e) {
            throw new IllegalStateException("策略分布查询失败: " + e.getMessage(), e);
        }
        log.info("[STATS][STRATEGY] relationshipId={} breakdown={}", relationshipId, m);
        return m;
    }
}
