package com.harbor.relationshipassistant.domain.analysis;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * 证据窗口中的一个具体信号。
 * <p>
 * 第一批只承载数据；不实现 SignalDetector。
 * </p>
 */
public final class EvidenceSignal {

    private final String type;
    private final String description;
    private final Map<String, Object> data;

    public EvidenceSignal(String type, String description, Map<String, Object> data) {
        this.type = Objects.requireNonNull(type, "type");
        this.description = description;
        this.data = (data == null) ? Collections.emptyMap() : Collections.unmodifiableMap(data);
    }

    public String getType() { return type; }
    public String getDescription() { return description; }
    public Map<String, Object> getData() { return data; }
}
