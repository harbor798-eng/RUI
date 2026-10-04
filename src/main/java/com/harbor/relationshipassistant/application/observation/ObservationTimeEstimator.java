package com.harbor.relationshipassistant.application.observation;

import java.util.List;

/** 本地、离线的预计耗时估算（不联网、不调 AI）。只给区间。 */
public class ObservationTimeEstimator {

    public record Estimate(String rangeLabel) {}

    /**
     * @param chatCount  时间窗内聊天行数（本地查询得到）
     * @param targetCount 选中主体数：1=单一，3=ALL
     */
    public Estimate estimate(int chatCount, int targetCount) {
        double base;
        if (chatCount < 50) base = 1.0;
        else if (chatCount < 200) base = 3.0;
        else if (chatCount < 500) base = 8.0;
        else if (chatCount < 1500) base = 15.0;
        else base = 30.0;

        double factor = targetCount >= 3 ? 2.5 : 1.0;
        double low = Math.max(1, Math.round(base * 0.8 * factor));
        double high = Math.max(low + 1, Math.round(base * 1.4 * factor));

        String label = high <= 3 ? "约 1～3 分钟"
                : high <= 10 ? "约 3～10 分钟"
                : high <= 20 ? "约 10～20 分钟"
                : high <= 40 ? "约 20～40 分钟"
                : high <= 60 ? "约 40～60 分钟"
                : "约 1～3 小时";
        return new Estimate(label);
    }
}
