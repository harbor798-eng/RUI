package com.harbor.relationshipassistant.application.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harbor.relationshipassistant.domain.analysis.AnalysisObservation;
import com.harbor.relationshipassistant.domain.analysis.AnalysisResult;
import com.harbor.relationshipassistant.domain.analysis.AnalysisResultMetadata;
import com.harbor.relationshipassistant.domain.analysis.EmotionIntensity;
import com.harbor.relationshipassistant.domain.analysis.EmotionObservation;
import com.harbor.relationshipassistant.domain.analysis.Fact;
import com.harbor.relationshipassistant.domain.analysis.FactType;
import com.harbor.relationshipassistant.domain.analysis.IssueSeverity;
import com.harbor.relationshipassistant.domain.analysis.Possibility;
import com.harbor.relationshipassistant.domain.analysis.PossibilityStatus;
import com.harbor.relationshipassistant.domain.analysis.Recommendation;
import com.harbor.relationshipassistant.domain.analysis.RecommendationPriority;
import com.harbor.relationshipassistant.domain.analysis.Unknown;
import com.harbor.relationshipassistant.domain.analysis.UserIssue;
import com.harbor.relationshipassistant.domain.chat.SenderType;
import com.harbor.relationshipassistant.infrastructure.ai.AIResponse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AIResponse.text → {@link AnalysisResult}。
 * <p>
 * 只做严格 JSON 映射；不"聪明修复"、不用正则提取、不改写 evidenceId/概率。
 * 允许最外层 ```json ... ``` 代码围栏。
 * </p>
 */
public class AnalysisResultParser {

    private final ObjectMapper mapper = new ObjectMapper();

    public AnalysisResult parse(AIResponse response) {
        System.out.println("[AnalysisResultParser] parsing AI response");
        if (response == null) throw new AnalysisResultParseException("AIResponse is null");
        String raw = response.text();
        if (raw == null || raw.isBlank()) throw new AnalysisResultParseException("AIResponse text is empty");

        String json = stripOuterFence(raw.trim());
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (Exception e) {
            System.out.println("[AnalysisResultParser][ERROR] type=" + e.getClass().getSimpleName()
                    + " message=" + e.getMessage());
            throw new AnalysisResultParseException("AI output is not valid JSON: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new AnalysisResultParseException("AI output is not a JSON object");
        }

        try {
            AnalysisResultMetadata meta = parseMetadata(root.path("metadata"));
            List<Fact> facts = parseFacts(root.path("facts"));
            List<AnalysisObservation> obs = parseObservations(root.path("observations"));
            List<Possibility> poss = parsePossibilities(root.path("possibilities"));
            List<Unknown> unk = parseUnknowns(root.path("unknowns"));
            List<EmotionObservation> emo = parseEmotions(root.path("emotions"));
            List<UserIssue> issues = parseUserIssues(root.path("userIssues"));
            List<Recommendation> recs = parseRecommendations(root.path("recommendations"));
            System.out.println("[AnalysisResultParser] JSON parsed successfully");
            return new AnalysisResult(meta, facts, obs, poss, unk, emo, issues, recs);
        } catch (AnalysisResultParseException e) {
            throw e;
        } catch (Exception e) {
            System.out.println("[AnalysisResultParser][ERROR] bind: " + e.getClass().getSimpleName() + " " + e.getMessage());
            throw new AnalysisResultParseException("Failed to bind JSON to AnalysisResult: " + e.getMessage(), e);
        }
    }

    private String stripOuterFence(String s) {
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl < 0) throw new AnalysisResultParseException("Unclosed code fence");
            String tail = s.substring(nl + 1);
            int end = tail.lastIndexOf("```");
            if (end < 0) throw new AnalysisResultParseException("Unclosed code fence");
            return tail.substring(0, end).trim();
        }
        return s;
    }

    private AnalysisResultMetadata parseMetadata(JsonNode n) {
        Long taskId = n.path("taskId").isNumber() ? n.path("taskId").asLong() : null;
        long rel = n.path("relationshipId").asLong(0L);
        return new AnalysisResultMetadata(taskId, rel, LocalDateTime.now());
    }

    private List<Fact> parseFacts(JsonNode arr) {
        List<Fact> out = new ArrayList<>();
        if (arr.isMissingNode() || arr.isNull()) return out;
        if (!arr.isArray()) throw new AnalysisResultParseException("facts must be array");
        for (JsonNode f : arr) {
            FactType type = enumValue(FactType.class, f.path("type").asText(""));
            String content = requiredText(f, "content", "facts");
            List<String> eids = strList(f.path("evidenceIds"));
            List<String> skeys = strList(f.path("statisticKeys"));
            String pfield = f.hasNonNull("profileField") ? f.get("profileField").asText() : null;
            out.add(new Fact(type, content, eids, skeys, pfield));
        }
        return out;
    }

    private List<AnalysisObservation> parseObservations(JsonNode arr) {
        List<AnalysisObservation> out = new ArrayList<>();
        if (!arr.isArray()) return out;
        for (JsonNode o : arr) {
            String c = requiredText(o, "content", "observations");
            out.add(new AnalysisObservation(c, strList(o.path("evidenceIds")), o.path("confidence").asDouble(0)));
        }
        return out;
    }

    private List<Possibility> parsePossibilities(JsonNode arr) {
        List<Possibility> out = new ArrayList<>();
        if (!arr.isArray()) return out;
        for (JsonNode p : arr) {
            String c = requiredText(p, "content", "possibilities");
            Double prob = p.hasNonNull("estimatedProbability") ? p.get("estimatedProbability").asDouble() : null;
            PossibilityStatus st = enumValue(PossibilityStatus.class,
                    p.path("status").asText("UNCONFIRMED"));
            out.add(new Possibility(c, prob, strList(p.path("evidenceIds")),
                    strList(p.path("counterEvidenceIds")), st));
        }
        return out;
    }

    private List<Unknown> parseUnknowns(JsonNode arr) {
        List<Unknown> out = new ArrayList<>();
        if (!arr.isArray()) return out;
        for (JsonNode u : arr) {
            out.add(new Unknown(requiredText(u, "content", "unknowns"), strList(u.path("relatedEvidenceIds"))));
        }
        return out;
    }

    private List<EmotionObservation> parseEmotions(JsonNode arr) {
        List<EmotionObservation> out = new ArrayList<>();
        if (!arr.isArray()) return out;
        for (JsonNode e : arr) {
            SenderType person = enumValue(SenderType.class, e.path("person").asText(""));
            if (person == SenderType.SYSTEM) throw new AnalysisResultParseException("emotion person cannot be SYSTEM");
            String emotion = requiredText(e, "emotion", "emotions");
            Double prob = e.hasNonNull("estimatedProbability") ? e.get("estimatedProbability").asDouble() : null;
            EmotionIntensity inten = enumValue(EmotionIntensity.class, e.path("intensity").asText(""));
            out.add(new EmotionObservation(person, emotion, prob, inten, strList(e.path("evidenceIds"))));
        }
        return out;
    }

    private List<UserIssue> parseUserIssues(JsonNode arr) {
        List<UserIssue> out = new ArrayList<>();
        if (!arr.isArray()) return out;
        for (JsonNode u : arr) {
            String c = requiredText(u, "content", "userIssues");
            IssueSeverity sev = enumValue(IssueSeverity.class, u.path("severity").asText(""));
            out.add(new UserIssue(c, sev, strList(u.path("evidenceIds")), u.path("confidence").asDouble(0)));
        }
        return out;
    }

    private List<Recommendation> parseRecommendations(JsonNode arr) {
        List<Recommendation> out = new ArrayList<>();
        if (!arr.isArray()) return out;
        for (JsonNode r : arr) {
            String c = requiredText(r, "content", "recommendations");
            RecommendationPriority pri = enumValue(RecommendationPriority.class,
                    r.path("priority").asText("MEDIUM"));
            out.add(new Recommendation(c, strList(r.path("reasonEvidenceIds")), pri));
        }
        return out;
    }

    private static List<String> strList(JsonNode n) {
        List<String> out = new ArrayList<>();
        if (n == null || !n.isArray()) return out;
        for (JsonNode e : n) if (e.isTextual()) out.add(e.asText());
        return out;
    }

    private static String requiredText(JsonNode n, String field, String ctx) {
        if (!n.hasNonNull(field) || !n.get(field).isTextual() || n.get(field).asText().isBlank()) {
            throw new AnalysisResultParseException(ctx + ": missing required field '" + field + "'");
        }
        return n.get(field).asText();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> cls, String v) {
        if (v == null || v.isBlank()) throw new AnalysisResultParseException("Missing enum for " + cls.getSimpleName());
        try {
            return Enum.valueOf(cls, v);
        } catch (IllegalArgumentException e) {
            throw new AnalysisResultParseException("Invalid enum " + cls.getSimpleName() + ": " + v);
        }
    }
}
