package com.harbor.relationshipassistant.application.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harbor.relationshipassistant.application.llm.AnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Quick Reply Application Adapter。
 *
 * <p>职责：把 {@link AnalysisResult#getContent()} 转成 UI 可用的候选列表。</p>
 *
 * <p>行为：</p>
 * <ul>
 *   <li>旧三策略 JSON（含 NATURAL/PROACTIVE/LIGHT_FLIRT）：按旧结构解析，缺策略不报错。</li>
 *   <li>非三策略输出（纯文本 / Markdown / 其它 JSON）：把整个 content 包装成一个 SINGLE 候选。</li>
 * </ul>
 *
 * <p>三策略是 Legacy UI Compatibility Format，不再是 Runtime 强制格式。</p>
 */
public final class QuickReplyAdapter {
    private static final Logger log = LoggerFactory.getLogger(QuickReplyAdapter.class);
    private static final ObjectMapper M = new ObjectMapper();
    private static final List<String> LEGACY_KEYS = List.of("NATURAL", "PROACTIVE", "LIGHT_FLIRT");

    public record Strategy(String key, String label, List<String> replies) {}

    public record Result(List<Strategy> strategies, String model, String createdAt) {}

    public Result adapt(AnalysisResult result) {
        if (result == null) throw new IllegalArgumentException("AnalysisResult is required");
        String raw = result.getContent() == null ? "" : result.getContent().trim();
        raw = stripCodeFence(raw);

        JsonNode root = tryParseJson(raw);
        if (root != null && root.has("strategies") && root.get("strategies").isArray()) {
            List<Strategy> parsed = parseStrategies(root.get("strategies"));
            if (!parsed.isEmpty()) {
                return new Result(parsed, result.getModel(),
                        result.getCreatedAt() == null ? "" : result.getCreatedAt().toString());
            }
        }
        // 非三策略输出：把整个 content 包装成一个 SINGLE 候选。
        List<Strategy> single = new ArrayList<>();
        single.add(new Strategy("SINGLE", "回复", List.of(raw)));
        return new Result(single, result.getModel(),
                result.getCreatedAt() == null ? "" : result.getCreatedAt().toString());
    }

    private static JsonNode tryParseJson(String raw) {
        if (raw.isEmpty()) return null;
        try {
            return M.readTree(raw);
        } catch (Exception e) {
            return null; // 纯文本 / Markdown
        }
    }

    private static List<Strategy> parseStrategies(JsonNode arr) {
        Map<String, Strategy> byKey = new LinkedHashMap<>();
        for (JsonNode s : arr) {
            String key = s.path("key").asText("").trim().toUpperCase();
            if (!LEGACY_KEYS.contains(key)) {
                // 未知策略 key：保留，但用其原 key 作为 label，不报错。
                List<String> replies = extractReplies(s);
                if (!replies.isEmpty() || s.has("replies")) {
                    byKey.put(key, new Strategy(key, labelOf(key), replies));
                }
                continue;
            }
            List<String> replies = extractReplies(s);
            byKey.put(key, new Strategy(key, labelOf(key), replies));
        }
        // 按 LEGACY_KEYS 顺序输出，保留额外 key 在末尾。
        List<Strategy> ordered = new ArrayList<>();
        for (String k : LEGACY_KEYS) {
            if (byKey.containsKey(k)) ordered.add(byKey.remove(k));
        }
        ordered.addAll(byKey.values());
        return ordered;
    }

    private static List<String> extractReplies(JsonNode s) {
        List<String> replies = new ArrayList<>();
        JsonNode reps = s.get("replies");
        if (reps != null && reps.isArray()) {
            for (JsonNode r : reps) {
                String t = r.path("text").asText("").trim();
                if (!t.isEmpty()) replies.add(t);
            }
        }
        return replies;
    }

    private static String labelOf(String key) {
        return switch (key) {
            case "NATURAL" -> "自然";
            case "PROACTIVE" -> "主动";
            case "LIGHT_FLIRT" -> "轻微暧昧";
            case "SINGLE" -> "回复";
            default -> key;
        };
    }

    private static String stripCodeFence(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl >= 0) t = t.substring(nl + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }
}
