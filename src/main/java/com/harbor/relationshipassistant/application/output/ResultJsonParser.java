package com.harbor.relationshipassistant.application.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harbor.relationshipassistant.application.systemhost.Audience;
import com.harbor.relationshipassistant.application.systemhost.ResultSlot;
import com.harbor.relationshipassistant.application.systemhost.ResultSpec;
import com.harbor.relationshipassistant.application.systemhost.ResultType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ResultJsonParser：把 LLM 返回的 JSON 按 {@link ResultSpec} 严格解析成 {@link ResultItem} 列表。
 *
 * <p>权威规则：ResultSpec > LLM declared metadata。
 * LLM JSON 里的 type/audience/strategyKey 仅作为"分组线索"，最终落到 ResultItem 的字段
 * 一律以 ResultSpec 的 slot 为准。LLM 不能通过 JSON 改写类型/受众/数量/策略。</p>
 *
 * <p>失败策略（Stage 5-1 审核修正）：</p>
 * <ul>
 *   <li>JSON 解析失败 → 返回空 items + parseDegraded=true。<strong>不</strong>把整段文本冒充 SENDABLE_REPLY。</li>
 *   <li>数量不足 → 缺失槽位保持缺失（对应位置没有 ResultItem），parseDegraded=true。</li>
 *   <li>类型不匹配 → 该条 LLM 输出被丢弃，不进任何 slot。</li>
 *   <li><strong>不</strong>自动重试 LLM。</li>
 * </ul>
 */
public final class ResultJsonParser {

    private static final Logger log = LoggerFactory.getLogger(ResultJsonParser.class);
    private static final ObjectMapper M = new ObjectMapper();

    public record ParseOutcome(List<ResultItem> items, boolean degraded, String rawContent) {}

    public ParseOutcome parse(String rawContent, ResultSpec spec) {
        String raw = rawContent == null ? "" : rawContent.trim();
        String stripped = stripCodeFence(raw);

        List<ResultItem> produced = new ArrayList<>();
        boolean degraded = false;

        JsonNode root;
        try {
            root = M.readTree(stripped);
        } catch (Exception e) {
            log.warn("[ResultParser] JSON parse failed, len={}, degraded=true, err={}", stripped.length(), e.getMessage());
            return new ParseOutcome(List.of(), true, raw);
        }

        JsonNode itemsNode = root == null ? null : root.get("items");
        if (itemsNode == null || !itemsNode.isArray()) {
            log.warn("[ResultParser] missing items[] array, degraded=true");
            return new ParseOutcome(List.of(), true, raw);
        }

        // 第一步：按 LLM 自报的 (type, audience, strategyKey) 分桶。
        // 注意：这只是线索，不是权威。
        // 对于 ANALYSIS_REPORT，保留整个 JsonNode 作为 payload，供后端 Adapter 提取结构化字段。
        Map<String, List<JsonNode>> buckets = new HashMap<>();
        for (JsonNode it : itemsNode) {
            if (it == null || !it.isObject()) continue;
            String type = upper(it.path("type").asText(""));
            String audience = upper(it.path("audience").asText(""));
            String strategy = upper(it.path("strategyKey").asText(""));
            // text 优先取 text 字段；ANALYSIS_REPORT 可能把正文放在 reportMarkdown 里。
            String text = it.path("text").asText("").trim();
            if (text.isEmpty()) text = it.path("reportMarkdown").asText("").trim();
            if (type.isEmpty() || audience.isEmpty()) continue;
            // ANALYSIS_REPORT 即使没有 text/reportMarkdown 也保留（结构化字段在 payload 里）。
            if (text.isEmpty() && !type.equals("ANALYSIS_REPORT")) continue;
            buckets.computeIfAbsent(bucketKey(type, audience, strategy), k -> new ArrayList<>()).add(it);
        }

        // 第二步：按 spec 的 slot 依次从桶里取数。
        int order = 0;
        for (ResultSlot slot : spec.slots()) {
            String type = slot.type().name();
            String audience = slot.audience().name();
            String strategy = slot.strategyKey() == null ? "" : slot.strategyKey().toUpperCase();
            // 优先精确匹配（type+audience+strategyKey）。
            List<JsonNode> bucket = buckets.get(bucketKey(type, audience, strategy));
            if (bucket == null && strategy.isEmpty()) {
                // spec 没指定 strategyKey：通配匹配同 type+audience 的任意 strategyKey。
                bucket = new ArrayList<>();
                for (Map.Entry<String, List<JsonNode>> e : buckets.entrySet()) {
                    if (e.getKey().startsWith(type + "|" + audience + "|")) {
                        bucket.addAll(e.getValue());
                    }
                }
            }
            if (bucket == null) bucket = List.of();
            int take = Math.min(slot.count(), bucket.size());
            for (int i = 0; i < take; i++) {
                JsonNode node = bucket.get(i);
                String text = node.path("text").asText("").trim();
                if (text.isEmpty()) text = node.path("reportMarkdown").asText("").trim();
                // ANALYSIS_REPORT 保留整个节点作为 payload；其他类型 payload=null。
                JsonNode payload = (slot.type() == ResultType.ANALYSIS_REPORT) ? node : null;
                produced.add(new ResultItem(slot.type(), slot.audience(), slot.strategyKey(), order++, text, payload));
            }
            if (take < slot.count()) {
                degraded = true;
                log.info("[ResultParser] slot under-filled type={} audience={} strategy={} expected={} got={}",
                        type, audience, strategy.isEmpty() ? "-" : strategy, slot.count(), take);
            }
        }

        if (produced.size() < spec.expectedCount()) degraded = true;
        return new ParseOutcome(produced, degraded, raw);
    }

    private static String bucketKey(String type, String audience, String strategy) {
        return type + "|" + audience + "|" + (strategy == null ? "" : strategy);
    }

    private static String upper(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }

    private static String stripCodeFence(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl >= 0) t = t.substring(nl + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
            t = t.trim();
        }
        return t;
    }
}
