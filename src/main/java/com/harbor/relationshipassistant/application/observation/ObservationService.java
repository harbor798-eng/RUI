package com.harbor.relationshipassistant.application.observation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.harbor.relationshipassistant.application.ai.AiConfigService;
import com.harbor.relationshipassistant.common.exception.AIException;
import com.harbor.relationshipassistant.domain.observation.FactStats;
import com.harbor.relationshipassistant.domain.observation.Observation;
import com.harbor.relationshipassistant.domain.observation.ObservationBatch;
import com.harbor.relationshipassistant.domain.observation.ObservationBatchChatSnapshot;
import com.harbor.relationshipassistant.domain.observation.ObservationEvidence;
import com.harbor.relationshipassistant.infrastructure.ai.AIProvider;
import com.harbor.relationshipassistant.infrastructure.ai.AIRequest;
import com.harbor.relationshipassistant.infrastructure.ai.AIResponse;
import com.harbor.relationshipassistant.infrastructure.persistence.ObservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 3：长期观察最小闭环。
 * 只读 Batch 冻结数据（snapshot + context_snapshot_json），不回查 chat_message/profile。
 */
public class ObservationService {

    private static final Logger log = LoggerFactory.getLogger(ObservationService.class);
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Set<String> VALID_SUBJECTS = Set.of("SELF", "OTHER", "RELATIONSHIP");
    /** 聊天快照直接进 prompt 的上限；超过则报错，不偷偷截断。 */
    private static final int MAX_CHAT_ROWS = 300;

    private final ObservationRepository repo;
    private final FactStatsComputer factStats;
    private final AIProvider provider;

    public ObservationService(ObservationRepository repo, FactStatsComputer factStats, AIProvider provider) {
        this.repo = repo;
        this.factStats = factStats;
        this.provider = provider;
    }

    /** 创建 Batch（复制快照）并把事实统计冻结进 context_snapshot_json。 */
    public ObservationBatch createBatch(long relationshipId, List<String> targets,
                                        LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                        String profileSnapshotJson) {
        ObservationBatch b = new ObservationBatch();
        b.setRelationshipId(relationshipId);
        b.setTargets(targets);
        b.setRangeStart(rangeStart);
        b.setRangeEnd(rangeEnd);
        b = repo.createBatchWithSnapshot(b);
        FactStats stats = factStats.computeFrom(repo.listSnapshots(b.getId()));
        try {
            var root = JSON.createObjectNode();
            root.set("factStats", JSON.valueToTree(stats));
            if (profileSnapshotJson != null) root.set("profile", JSON.readTree(profileSnapshotJson));
            b.setContextSnapshotJson(JSON.writeValueAsString(root));
        } catch (Exception e) {
            throw new AIException("冻结 context_snapshot 失败", "createBatch", e);
        }
        log.info("[OBS_CONTEXT_BUILD] batchId={} targets={} chatRows={}", b.getId(), targets, b.getSnapshotChatCount());
        return b;
    }

    /** 同步执行一次完整分析（Phase 3 单线程，不做后台调度）。 */
    public List<Observation> runAnalysis(long batchId) {
        long t0 = System.currentTimeMillis();
        log.info("[OBS_ANALYSIS_START] batchId={}", batchId);

        ObservationBatch batch = repo.getBatch(batchId);
        List<String> targets = batch.getTargets();
        List<ObservationBatchChatSnapshot> chat = repo.listSnapshots(batchId);
        if (chat.size() > MAX_CHAT_ROWS) {
            String msg = "聊天快照 " + chat.size() + " 行超过上限 " + MAX_CHAT_ROWS + "，1.0 不做分块，请缩小时间范围";
            log.error("[OBS_ANALYSIS_ERROR] batchId={} {}", batchId, msg);
            throw new AIException(msg, "runAnalysis");
        }
        FactStats stats = factStats.computeFrom(chat);
        log.info("[OBS_FACT_STATS] batchId={} total={} activeDays={}", batchId, stats.totalMessages(), stats.activeDays());

        String userPrompt = buildUserPrompt(batch, targets, chat, stats);
        AIRequest req = AIRequest.of(systemPrompt(), List.of(new AIRequest.Turn("user", userPrompt)));
        req.setTemperature(0.4);
        req.setMaxTokens(1200);

        JsonNode root = null;
        Exception last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                log.info("[OBS_AI_REQUEST] batchId={} provider={} attempt={} chatRows={}",
                        batchId, provider.getProviderName(), attempt, chat.size());
                AIResponse resp = provider.generate(req);
                log.info("[OBS_AI_RESPONSE] batchId={} elapsedMs={} len={}",
                        batchId, System.currentTimeMillis() - t0, resp.text() == null ? 0 : resp.text().length());
                root = parseAndValidate(resp.text(), targets);
                break;
            } catch (Exception e) {
                last = e;
                log.warn("[OBS_ANALYSIS_ERROR] batchId={} attempt={} err={}", batchId, attempt, e.getMessage());
            }
        }
        if (root == null) throw new AIException("AI 分析两次失败: " + last.getMessage(), "runAnalysis");

        List<Observation> out = new ArrayList<>();
        for (JsonNode node : root.path("observations")) {
            String subject = node.path("subject").asText();
            String content = node.path("content").asText();
            Observation o = new Observation();
            o.setBatchId(batchId);
            o.setSubject(subject);
            o.setAiRawText(content);
            o = repo.insertObservation(o);
            // Evidence：事实统计快照 + 聊天样本快照
            repo.insertEvidence(evidence(o.getId(), "FACT_STATS",
                    "{\"totalMessages\":" + stats.totalMessages() + ",\"me\":" + stats.meCount()
                            + ",\"other\":" + stats.otherCount() + ",\"activeDays\":" + stats.activeDays() + "}"));
            repo.insertEvidence(evidence(o.getId(), "CHAT_SNIPPET",
                    "{\"sampleRows\":" + Math.min(5, chat.size()) + ",\"note\":\"AI 当时基于时间窗内快照文本分析\"}"));
            log.info("[OBS_INSERT] batchId={} observationId={} subject={} len={}",
                    batchId, o.getId(), subject, content.length());
            out.add(o);
        }
        repo.finishBatch(batchId, "DONE", System.currentTimeMillis() - t0);
        log.info("[OBS_BATCH_DONE] batchId={} observations={} elapsedMs={}", batchId, out.size(), System.currentTimeMillis() - t0);
        return out;
    }

    JsonNode parseAndValidate(String text, List<String> targets) {
        try {
            JsonNode root = new ObjectMapper().readTree(stripCodeFence(text));
            JsonNode arr = root.path("observations");
            if (!arr.isArray() || arr.isEmpty()) throw new AIException("缺少 observations", "parse");
            Set<String> allowed = new HashSet<>(targets);
            for (JsonNode n : arr) {
                String s = n.path("subject").asText();
                String c = n.path("content").asText();
                if (!VALID_SUBJECTS.contains(s)) throw new AIException("非法 subject: " + s, "parse");
                if (!allowed.contains(s)) throw new AIException("返回了未选择的 subject: " + s, "parse");
                if (c == null || c.isBlank()) throw new AIException("content 为空: " + s, "parse");
            }
            log.info("[OBS_PARSE_SUCCESS] subjects={}", arr.size());
            return root;
        } catch (AIException e) {
            throw e;
        } catch (Exception e) {
            throw new AIException("JSON 解析失败: " + e.getMessage(), "parse", e);
        }
    }

    private static String stripCodeFence(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            t = nl > 0 ? t.substring(nl + 1) : t;
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }

    private ObservationEvidence evidence(Long obsId, String type, String snapshot) {
        ObservationEvidence e = new ObservationEvidence();
        e.setObservationId(obsId);
        e.setEvidenceType(type);
        e.setEvidenceSnapshot(snapshot);
        return e;
    }

    private String systemPrompt() {
        return "你是一个恋爱关系的长期观察助手。基于指定时间窗内冻结的聊天事实、客观统计与用户资料，" +
                "对指定主体做一次独立观察。不要预测未来、不判断谁对谁错、不打分、不替用户做决定。" +
                "每条观察必须区分'聊天中明确出现的事实'和'你的推断'，不虚构聊天内容，不把用户自述 Profile 当成聊天事实。" +
                "严格输出 JSON：{\"observations\":[{\"subject\":\"SELF|OTHER|RELATIONSHIP\",\"content\":\"...\"}]}，" +
                "每个主体只输出一条。";
    }

    private String buildUserPrompt(ObservationBatch b, List<String> targets,
                                   List<ObservationBatchChatSnapshot> chat, FactStats stats) {
        StringBuilder sb = new StringBuilder();
        sb.append("任务=LONG_TERM_OBSERVATION\n");
        sb.append("分析主体=").append(String.join(",", targets)).append("\n");
        sb.append("时间范围=").append(b.getRangeStart()).append(" ~ ").append(b.getRangeEnd()).append("\n");
        sb.append("【客观事实统计】\n");
        sb.append("总消息=").append(stats.totalMessages())
          .append(" 我=").append(stats.meCount()).append(" 对方=").append(stats.otherCount())
          .append(" 活跃天数=").append(stats.activeDays()).append("\n");
        sb.append("【聊天快照（TEXT/EMOJI 等，按时间排序）】\n");
        for (ObservationBatchChatSnapshot r : chat) {
            if (r.getMessageTime() == null) continue;
            sb.append(r.getMessageTime().toLocalDate()).append(" ")
              .append(r.getSenderType()).append("[").append(r.getMessageType()).append("]: ")
              .append(r.getContent() == null ? "" : r.getContent()).append("\n");
        }
        return sb.toString();
    }
}
