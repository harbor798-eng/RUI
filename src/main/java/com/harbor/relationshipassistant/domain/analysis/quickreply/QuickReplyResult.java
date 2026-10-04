package com.harbor.relationshipassistant.domain.analysis.quickreply;

import com.harbor.relationshipassistant.domain.analysis.AnalysisOutput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Quick Reply 输出：恰好 3 个候选（NATURAL/ACTIVE/SLIGHTLY_FLIRTATIOUS）。
 * 不包含关系评分、不包含关系分析字段。
 */
public final class QuickReplyResult implements AnalysisOutput {

    private final List<QuickReplyCandidate> candidates;

    public QuickReplyResult(List<QuickReplyCandidate> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
    }

    public List<QuickReplyCandidate> getCandidates() { return candidates; }
}
