package com.harbor.relationshipassistant.domain.analysis;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 本次分析允许 AI 看到的档案信息。
 * <p>
 * <b>不</b>是完整数据库 Profile。进入这里的字段已经过
 * {@code usage_weight} 权限过滤（NONE 字段不会出现）。
 * 因此这里不需要再携带 usage_weight 原始值。
 * </p>
 */
public final class ProfileContext {

    private final Map<String, String> me;
    private final Map<String, String> other;

    public ProfileContext(Map<String, String> me, Map<String, String> other) {
        this.me = immutable(me);
        this.other = immutable(other);
    }

    private static Map<String, String> immutable(Map<String, String> src) {
        if (src == null || src.isEmpty()) return Collections.emptyMap();
        return Collections.unmodifiableMap(new HashMap<>(src));
    }

    public Map<String, String> getMe() { return me; }
    public Map<String, String> getOther() { return other; }
}
