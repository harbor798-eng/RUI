package com.harbor.relationshipassistant.application.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harbor.relationshipassistant.application.llm.AnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Detailed Analysis Function Output Adapter:
 * AnalysisResult.content (JSON) → UI-friendly DetailedAnalysisResult.
 *
 * Phase 22: extended with emotions / facts / inferences / confidence / recommendation.action / selfCare.
 * Backward compat: legacy "evidence" is folded into "facts"; legacy string recommendation is kept optional.
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
        JsonNode root;
        try {
            root = M.readTree(raw);
        } catch (Exception e) {
            // 4.19-D.1: 自然连续文本 fallback —— 不抛 JSON 错误，整段作为 reportMarkdown 交付。
            log.info("[DETAIL-ADAPTER] non-JSON content, falling back to raw text. len={}", raw.length());
            return new Result(
                    "",
                    List.of(), List.of(),
                    List.of(), List.of(),
                    List.of(),
                    new Recommendation("", ""),
                    new SelfCare(false, ""),
                    raw,
                    result.getModel(),
                    result.getCreatedAt() == null ? "" : result.getCreatedAt().toString());
        }
        if (root == null || !root.isObject()) {
            // 4.19-D.1: 顶层不是 JSON object（例如直接是数组/字符串），同样 fallback 为连续文本。
            log.info("[DETAIL-ADAPTER] root is not a JSON object, falling back to raw text.");
            return new Result(
                    "",
                    List.of(), List.of(),
                    List.of(), List.of(),
                    List.of(),
                    new Recommendation("", ""),
                    new SelfCare(false, ""),
                    raw,
                    result.getModel(),
                    result.getCreatedAt() == null ? "" : result.getCreatedAt().toString());
        }
        String summary = root.path("summary").asText("").trim();
        String report = root.path("reportMarkdown").asText("").trim();
        if (report.isEmpty()) report = root.path("text").asText("").trim();
        if (summary.isEmpty() && report.isEmpty()) {
            // 4.19-D.1: 合法 JSON 但缺 summary/reportMarkdown，不抛错；把整个 raw 作为 reportMarkdown。
            log.info("[DETAIL-ADAPTER] JSON object missing summary/reportMarkdown, using raw as reportMarkdown.");
            return new Result(
                    "",
                    List.of(), List.of(),
                    List.of(), List.of(),
                    List.of(),
                    new Recommendation("", ""),
                    new SelfCare(false, ""),
                    raw,
                    result.getModel(),
                    result.getCreatedAt() == null ? "" : result.getCreatedAt().toString());
        }

        List<Emotion> meEmotions = parseEmotions(root.path("emotions").path("me"));
        List<Emotion> otherEmotions = parseEmotions(root.path("emotions").path("other"));

        // facts: prefer new "facts"; fall back to legacy "evidence"
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
            // legacy string form
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
