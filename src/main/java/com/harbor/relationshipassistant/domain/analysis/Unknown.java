package com.harbor.relationshipassistant.domain.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 当前证据不足、无法可靠判断的内容。
 * <p>
 * Unknown 是合法的分析结果——不能因为没有证据就强行生成 Possibility。
 * 例："目前无法判断对方减少回复是否与关系态度变化有关。"
 * </p>
 */
public final class Unknown {

    private final String content;
    private final List<String> relatedEvidenceIds;

    public Unknown(String content, List<String> relatedEvidenceIds) {
        this.content = Objects.requireNonNull(content, "content");
        this.relatedEvidenceIds = (relatedEvidenceIds == null || relatedEvidenceIds.isEmpty())
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(relatedEvidenceIds));
    }

    public String getContent() { return content; }
    public List<String> getRelatedEvidenceIds() { return relatedEvidenceIds; }
}
