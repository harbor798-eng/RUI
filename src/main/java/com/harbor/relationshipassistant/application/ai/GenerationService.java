package com.harbor.relationshipassistant.application.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harbor.relationshipassistant.domain.ai.GenerationRecord;
import com.harbor.relationshipassistant.infrastructure.persistence.GenerationRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GenerationRecord 行为记录服务（Phase 3）：
 * 生成成功即建记录；「使用这条」才写选择；发送时回写 final_text/modified/sent_message_id。
 */
public class GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GenerationRecordRepository repo;

    public GenerationService(GenerationRecordRepository repo) { this.repo = repo; }

    /** 生成成功后立即落库（候选快照 + Context 快照），selected_* 为空。 */
    public GenerationRecord createFromResult(Long relationshipId, QuickReplyService.GenerationResult result) {
        try {
            GenerationRecord r = new GenerationRecord();
            r.setRelationshipId(relationshipId);
            r.setRequestId(result.requestId());
            r.setStage(result.context().stageLabel());
            r.setProvider("DEEPSEEK");
            r.setModel(result.model());
            r.setCandidateCount(result.candidates().flatten().size());
            r.setCandidatesSnapshot(buildSnapshotJson(result));
            r.setContextSnapshot(result.context().humanContext());
            repo.insert(r);
            log.info("[GENERATION][CREATE] generationId={} relationshipId={} requestId={} candidateCount={}",
                    r.getId(), relationshipId, result.requestId(), r.getCandidateCount());
            return r;
        } catch (Exception e) {
            log.error("[GENERATION][ERROR] 创建记录失败: {}", e.getMessage());
            throw new RuntimeException("保存 GenerationRecord 失败", e);
        }
    }

    private String buildSnapshotJson(QuickReplyService.GenerationResult result) throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        java.util.List<Map<String, Object>> strategies = new java.util.ArrayList<>();
        int idx = 0;
        for (var s : result.candidates().strategies) {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("name", s.name);
            java.util.List<Map<String, String>> replies = new java.util.ArrayList<>();
            for (var rep : s.replies) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("index", String.valueOf(idx++));
                m.put("text", rep.text);
                m.put("reason", rep.reason);
                replies.add(m);
            }
            sm.put("replies", replies);
            strategies.add(sm);
        }
        root.put("strategies", strategies);
        return MAPPER.writeValueAsString(root);
    }

    /** 用户点击「使用这条」。 */
    public void selectCandidate(Long recordId, String strategy, int index, String originalText) {
        repo.markSelected(recordId, strategy, index, originalText);
        log.info("[GENERATION][SELECT] generationId={} strategy={} candidateIndex={}", recordId, strategy, index);
    }

    /** 输入框编辑后的最终文本（发送前/离开前调用，避免逐键写库）。 */
    public void flushFinalText(Long recordId, String selectedOriginalText, String finalText) {
        boolean modified = selectedOriginalText != null && !selectedOriginalText.equals(finalText);
        repo.updateFinalText(recordId, finalText, modified);
        log.info("[GENERATION][EDIT] generationId={} modified={}", recordId, modified);
    }

    /** 发送完成：关联 ChatMessage。 */
    public void markSent(Long recordId, Long chatMessageId) {
        repo.markSent(recordId, chatMessageId);
        log.info("[GENERATION][SEND] generationId={} chatMessageId={}", recordId, chatMessageId);
    }
}
