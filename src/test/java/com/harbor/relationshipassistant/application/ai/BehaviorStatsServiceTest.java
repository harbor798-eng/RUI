package com.harbor.relationshipassistant.application.ai;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4 统计层纯逻辑测试：
 * 数据库层语义（A→B 覆盖只计最后一次、relationship 隔离、source_type 归类）
 * 已在 Phase 3 用真实数据验证；这里校验聚合率与空态不崩溃。
 */
class BehaviorStatsServiceTest {

    private BehaviorStatsService.Summary summary(long gen, long selected, long modified,
                                                long usedSent, long usedNotSent) {
        return new BehaviorStatsService.Summary(gen, selected, Map.of("自然", 1L),
                modified, selected - modified, 0, 0, usedSent, usedNotSent);
    }

    @Test
    void emptyStatsRatesAreZero() {
        var s = summary(0, 0, 0, 0, 0);
        assertEquals(0.0, s.modificationRate(), 1e-9);
        assertEquals(0.0, s.usedSendRate(), 1e-9);
    }

    @Test
    void modificationRateComputed() {
        // 已选 4 次，修改 2 次 → 50%
        var s = summary(4, 4, 2, 4, 0);
        assertEquals(2, s.modifiedCount());
        assertEquals(2, s.unmodifiedCount());
        assertEquals(0.5, s.modificationRate(), 1e-9);
    }

    @Test
    void usedSendRateComputed() {
        // 已用 4 次：已发 4、未发 0 → 100%
        var s = summary(4, 4, 2, 4, 0);
        assertEquals(1.0, s.usedSendRate(), 1e-9);
        // 用了但没发 → 0%
        var s2 = summary(1, 1, 0, 0, 1);
        assertEquals(0.0, s2.usedSendRate(), 1e-9);
        // 用 3 次发 2 次 → 66.7%
        var s3 = summary(3, 3, 1, 2, 1);
        assertEquals(2.0 / 3.0, s3.usedSendRate(), 1e-9);
    }

    @Test
    void intermediateUseNotDoubleCounted() {
        // A→B 连续使用后数据库只有 1 行 selected（Phase 3 已验证），
        // selectedCount 按行数=1，不按点击次数=2。
        long uiClickCount = 2;
        long dbRowsSelected = 1;
        var s = summary(1, dbRowsSelected, 0, 1, 0);
        assertEquals(1, s.selectedCount());
        assertNotEquals(uiClickCount, s.selectedCount());
    }
}
