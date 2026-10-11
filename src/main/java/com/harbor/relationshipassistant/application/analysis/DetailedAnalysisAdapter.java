package com.harbor.relationshipassistant.application.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harbor.relationshipassistant.application.llm.AnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detailed Analysis Function Output Adapter:
 * AnalysisResult.content (JSON) → UI-friendly DetailedAnalysisResult.
 *
 * Handles:
 * - Flat JSON with summary/facts/emotions at root.
 * - {items:[{summary,...}]} wrapper.
 * - JSON wrapped in code fences with leading/trailing prose.
 * - Truncated/malformed JSON: attempts lenient extraction.
 */
public final class DetailedAnalysisAdapter {
    private static final Logger log = LoggerFactory.getLogger(DetailedAnalysisAdapter.class);
    private static final ObjectMapper M = new ObjectMapper();

    public record Emotion(String label, String hint) {}
    public record Possibility(String content, String confidence) {}
    public record Recommendation(String action, String reason) {}
    public record SelfCare(boolean show, String reason) {}

    public record Result(String summary,
                         List<Emotion> meEmotions,
                         List<Emotion> otherEmotions,
                         List<String> facts,
                         List<String> inferences,
                         List<Possibility> possibilities,
                         Recommendation recommendation,
                         SelfCare selfCare,
                         String reportMarkdown,
                         String model,
                         String createdAt) {}

    public Result adapt(AnalysisResult result) {
        if (result == null) throw new IllegalArgumentException("AnalysisResult is required");
        String raw = result.getContent() == null ? "" : result.getContent().trim();
        raw = stripCodeFence(raw);

        JsonNode root = tryParse(raw);
        if (root == null || !root.isObject()) {
            // 3) Last resort: try extracting JSON substring from first { to last }
            String extracted = extractJsonObject(raw);
            if (extracted != null) {
                root = tryParse(extracted);
            }
        }
        if (root == null || !root.isObject()) {
            log.info("[DETAIL-ADAPTER] cannot parse as JSON after all attempts. len={}", raw.length());
            // Try regex extraction even when JSON can't be parsed at all
            String extractedSummary = extractStringField(raw, "summary");
            String extractedText = extractStringField(raw, "text");
            if (!extractedSummary.isEmpty() || !extractedText.isEmpty()) {
                log.info("[DETAIL-ADAPTER] recovered fields via regex: summaryLen={} textLen={}",
                        extractedSummary.length(), extractedText.length());
                return new Result(
                        extractedSummary,
                        List.of(), List.of(),
                        List.of(), List.of(),
                        List.of(),
                        new Recommendation("", ""),
                        new SelfCare(false, ""),
                        extractedText.isEmpty() ? extractedSummary : extractedText,
                        result.getModel(),
                        result.getCreatedAt() == null ? "" : result.getCreatedAt().toString());
            }
            String degraded = raw;
            if (raw.trim().startsWith("{")) {
                degraded = "（模型输出格式异常，无法解析结构化报告。请重新生成分析。）";
            }
            return new Result(
                    "",
                    List.of(), List.of(),
                    List.of(), List.of(),
                    List.of(),
                    new Recommendation("", ""),
                    new SelfCare(false, ""),
                    degraded,
                    result.getModel(),
                    result.getCreatedAt() == null ? "" : result.getCreatedAt().toString());
        }

        String summary = root.path("summary").asText("").trim();
        String report = root.path("reportMarkdown").asText("").trim();
        if (report.isEmpty()) report = root.path("text").asText("").trim();

        // Unwrap items[0] if root fields are empty
        if (summary.isEmpty() && report.isEmpty()) {
            JsonNode items = root.path("items");
            if (items != null && items.isArray() && !items.isEmpty() && items.get(0).isObject()) {
                JsonNode item = items.get(0);
                String s2 = item.path("summary").asText("").trim();
                String r2 = item.path("reportMarkdown").asText("").trim();
                if (r2.isEmpty()) r2 = item.path("text").asText("").trim();
                if (!s2.isEmpty() || !r2.isEmpty()) {
                    log.info("[DETAIL-ADAPTER] unwrapped items[0] from model response");
                    root = item;
                    summary = s2;
                    report = r2;
                }
            }
        }

        if (summary.isEmpty() && report.isEmpty()) {
            // Last resort: try regex extraction from malformed JSON text
            String extractedText = extractStringField(raw, "text");
            String extractedSummary = extractStringField(raw, "summary");
            if (!extractedSummary.isEmpty()) summary = extractedSummary;
            if (report.isEmpty() && !extractedText.isEmpty()) report = extractedText;

            if (summary.isEmpty() && report.isEmpty()) {
                log.info("[DETAIL-ADAPTER] JSON object missing all fields. len={}", raw.length());
                String degraded = raw.trim().startsWith("{")
                        ? "（模型输出格式异常，无法解析结构化报告。请重新生成分析。）"
                        : raw;
                return new Result(
                        "",
                        List.of(), List.of(),
                        List.of(), List.of(),
                        List.of(),
                        new Recommendation("", ""),
                        new SelfCare(false, ""),
                        degraded,
                        result.getModel(),
                        result.getCreatedAt() == null ? "" : result.getCreatedAt().toString());
            }
        }

        List<Emotion> meEmotions = parseEmotions(root.path("emotions").path("me"));
        List<Emotion> otherEmotions = parseEmotions(root.path("emotions").path("other"));
        List<String> facts = parseStringList(root.path("facts"));
        if (facts.isEmpty()) facts = parseStringList(root.path("evidence"));
        List<String> inferences = parseStringList(root.path("inferences"));

        List<Possibility> possibilities = new ArrayList<>();
        JsonNode ps = root.path("possibilities");
        if (ps != null && ps.isArray()) {
            for (JsonNode p : ps) {
                String c = p.path("content").asText("").trim();
                if (c.isEmpty()) continue;
                String conf = p.path("confidence").asText("medium").trim().toLowerCase();
                if (!conf.equals("high") && !conf.equals("medium") && !conf.equals("low")) conf = "medium";
                possibilities.add(new Possibility(c, conf));
            }
        }

        Recommendation rec;
        JsonNode recNode = root.path("recommendation");
        if (recNode.isObject()) {
            String action = recNode.path("action").asText("").trim().toUpperCase();
            String reason = recNode.path("reason").asText("").trim();
            rec = new Recommendation(action, reason);
        } else {
            rec = new Recommendation("", recNode.asText("").trim());
        }

        SelfCare sc;
        JsonNode scNode = root.path("selfCare");
        if (scNode != null && scNode.isObject()) {
            sc = new SelfCare(scNode.path("show").asBoolean(false),
                    scNode.path("reason").asText("").trim());
        } else {
            sc = new SelfCare(false, "");
        }

        return new Result(summary, meEmotions, otherEmotions, facts, inferences,
                possibilities, rec, sc, report,
                result.getModel(),
                result.getCreatedAt() == null ? "" : result.getCreatedAt().toString());
    }

    private static JsonNode tryParse(String s) {
        try {
            return M.readTree(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** Extract substring from first { to last } for truncated JSON. */
    private static String extractJsonObject(String s) {
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        return null;
    }

    /** Leniently extract a JSON string field value via regex. Handles truncated strings. */
    private static String extractStringField(String json, String field) {
        // Normal: "field": "value"
        Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return unescape(m.group(1));
        }
        // Truncated: "field": "value without closing quote
        Pattern p2 = Pattern.compile("\"" + field + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\\s*[,}\\n]");
        Matcher m2 = p2.matcher(json);
        if (m2.find()) {
            return unescape(m2.group(1));
        }
        // Mid-string truncation: "field": "value... (no closing, no trailing delimiter)
        Pattern p3 = Pattern.compile("\"" + field + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\\s*$");
        Matcher m3 = p3.matcher(json);
        if (m3.find()) {
            return unescape(m3.group(1));
        }
        return "";
    }

    private static String unescape(String s) {
        return s.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static List<Emotion> parseEmotions(JsonNode arr) {
        List<Emotion> out = new ArrayList<>();
        if (arr == null || !arr.isArray()) return out;
        for (JsonNode e : arr) {
            String label = e.path("label").asText("").trim();
            if (label.isEmpty()) continue;
            String hint = e.path("hint").asText("").trim();
            out.add(new Emotion(label, hint));
        }
        return out;
    }

    private static List<String> parseStringList(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr == null || !arr.isArray()) return out;
        for (JsonNode e : arr) {
            String s = e.asText("").trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    /**
     * Strip code fences. Handles both:
     * - String starting with ``` fences
     * - String with leading prose before the first ``` fence
     * - Multiple fence blocks (takes the largest JSON-looking block)
     */
    static String stripCodeFence(String s) {
        if (s == null) return "";
        String t = s.trim();
        // Find first ``` and last ```
        int first = t.indexOf("```");
        if (first < 0) return t;
        int afterFirst = t.indexOf('\n', first);
        if (afterFirst < 0) return t;
        int last = t.lastIndexOf("```");
        if (last <= afterFirst) return t;
        String inner = t.substring(afterFirst + 1, last).trim();
        // If the inner content looks like JSON (starts with { or [), use it
        if (inner.startsWith("{") || inner.startsWith("[")) return inner;
        // Otherwise, return the original trimmed string (might be plain text)
        return t;
    }
}
