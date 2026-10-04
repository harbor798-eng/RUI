package com.harbor.relationshipassistant.domain.analysis;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 本次分析允许使用的聊天时间范围。
 * <p>
 * 由一个 {@link Kind} 和可选的 start/end 组成。
 * 除 {@link Kind#CUSTOM} 外，其他 Kind 的 start/end 由后续 ChatProcessor 在运行时解析，
 * 构造时可为 null。
 * </p>
 */
public final class AnalysisRange {

    public enum Kind {
        /** 只看当前会话窗口（最近若干条消息）。 */
        CURRENT_CONTEXT,
        /** 最近 7 天。 */
        RECENT_7_DAYS,
        /** 最近 3 个月。 */
        RECENT_3_MONTHS,
        /** 最近 1 年。 */
        RECENT_YEAR,
        /** 全部历史数据。 */
        ALL,
        /** 用户自定义时间区间。 */
        CUSTOM
    }

    private final Kind kind;
    private final LocalDateTime start;
    private final LocalDateTime end;

    private AnalysisRange(Kind kind, LocalDateTime start, LocalDateTime end) {
        this.kind = Objects.requireNonNull(kind, "kind");
        if (kind == Kind.CUSTOM) {
            Objects.requireNonNull(start, "start must not be null for CUSTOM range");
            Objects.requireNonNull(end, "end must not be null for CUSTOM range");
            if (end.isBefore(start)) {
                throw new IllegalArgumentException("end must not be before start");
            }
        }
        this.start = start;
        this.end = end;
    }

    public static AnalysisRange of(Kind kind) {
        if (kind == Kind.CUSTOM) {
            throw new IllegalArgumentException("use custom(start, end) for CUSTOM range");
        }
        return new AnalysisRange(kind, null, null);
    }

    public static AnalysisRange custom(LocalDateTime start, LocalDateTime end) {
        return new AnalysisRange(Kind.CUSTOM, start, end);
    }

    public Kind getKind() { return kind; }
    public LocalDateTime getStart() { return start; }
    public LocalDateTime getEnd() { return end; }

    @Override
    public String toString() {
        return "AnalysisRange{" + kind + ", start=" + start + ", end=" + end + '}';
    }
}
