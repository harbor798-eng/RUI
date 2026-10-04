package com.harbor.relationshipassistant.application.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harbor.relationshipassistant.domain.analysis.AnalysisResult;
import com.harbor.relationshipassistant.domain.analysis.TimelineEvent;
import com.harbor.relationshipassistant.domain.analysis.deep.DeepAnalysisResult;
import com.harbor.relationshipassistant.domain.analysis.deep.LongTermPattern;
import com.harbor.relationshipassistant.domain.analysis.deep.RelationshipChange;
import com.harbor.relationshipassistant.domain.analysis.deep.TheoryExplanation;
import com.harbor.relationshipassistant.infrastructure.ai.AIResponse;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** AIResponse → {@link DeepAnalysisResult}。Base 部分复用 {@link AnalysisResultParser}。 */
public class DeepAnalysisResultParser {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AnalysisResultParser baseParser = new AnalysisResultParser();

    public DeepAnalysisResult parse(AIResponse response) {
        System.out.println("[DeepAnalysisResultParser] parsing");
        AnalysisResult base = baseParser.parse(response);

        String json = stripFence(response.text().trim());
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (Exception e) {
            throw new AnalysisResultParseException("Deep output is not valid JSON: " + e.getMessage(), e);
        }
        List<TimelineEvent> timeline = parseTimeline(root.path("timeline"));
        List<RelationshipChange> changes = parseChanges(root.path("relationshipChanges"));
        List<LongTermPattern> patterns = parseLongTerm(root.path("longTermPatterns"));
        List<TheoryExplanation> theories = parseTheories(root.path("theoryExplanations"));
        return new DeepAnalysisResult(base, timeline, changes, patterns, theories);
    }

    private List<TimelineEvent> parseTimeline(JsonNode arr) {
        if (arr == null || arr.isMissingNode() || arr.isNull()) return Collections.emptyList();
        if (!arr.isArray()) {
            throw new AnalysisResultParseException("timeline must be an array");
        }
        List<TimelineEvent> out = new ArrayList<>();
        int i = 0;
        for (JsonNode n : arr) {
            String eventId = n.path("eventId").asText("");
            if (eventId.isEmpty()) {
                throw new AnalysisResultParseException("timeline[" + i + "].eventId is required");
            }
            String type = n.path("type").asText("");
            if (type.isEmpty()) {
                throw new AnalysisResultParseException("timeline[" + i + "].type is required");
            }
            String description = n.path("description").asText("");
            LocalDateTime time = null;
            JsonNode timeNode = n.path("time");
            if (!timeNode.isMissingNode() && !timeNode.isNull() && !timeNode.asText("").isEmpty()) {
                try {
                    String t = timeNode.asText();
                    time = t.contains("T") ? LocalDateTime.parse(t) : LocalDateTime.parse(t);
                } catch (Exception e) {
                    try {
                        time = OffsetDateTime.parse(timeNode.asText()).toLocalDateTime();
                    } catch (Exception e2) {
                        throw new AnalysisResultParseException("timeline[" + i + "].time is not ISO-8601: " + timeNode.asText());
                    }
                }
            }
            JsonNode ev = n.path("evidenceIds");
            if (!ev.isMissingNode() && !ev.isNull() && !ev.isArray()) {
                throw new AnalysisResultParseException("timeline[" + i + "].evidenceIds must be array");
            }
            out.add(new TimelineEvent(eventId, time, type, description, strList(ev)));
            i++;
        }
        System.out.println("[DeepAnalysisResultParser] timelineCount=" + out.size());
        return out;
    }

    private List<RelationshipChange> parseChanges(JsonNode arr) {
        List<RelationshipChange> out = new ArrayList<>();
        if (!arr.isArray()) return out;
        for (JsonNode n : arr) {
            out.add(new RelationshipChange(
                    n.path("type").asText(""),
                    n.path("description").asText(""),
                    strList(n.path("evidenceIds")),
                    n.path("confidence").asDouble(0)));
        }
        return out;
    }

    private List<LongTermPattern> parseLongTerm(JsonNode arr) {
        List<LongTermPattern> out = new ArrayList<>();
        if (!arr.isArray()) return out;
        for (JsonNode n : arr) {
            out.add(new LongTermPattern(
                    n.path("patternType").asText(""),
                    n.path("description").asText(""),
                    strList(n.path("evidenceIds")),
                    strList(n.path("patternIds")),
                    n.path("confidence").asDouble(0)));
        }
        return out;
    }

    private List<TheoryExplanation> parseTheories(JsonNode arr) {
        List<TheoryExplanation> out = new ArrayList<>();
        if (!arr.isArray()) return out;
        for (JsonNode n : arr) {
            out.add(new TheoryExplanation(
                    n.path("title").asText(""),
                    n.path("explanation").asText(""),
                    strList(n.path("knowledgeIds")),
                    strList(n.path("evidenceIds"))));
        }
        return out;
    }

    private static List<String> strList(JsonNode n) {
        List<String> out = new ArrayList<>();
        if (n != null && n.isArray()) for (JsonNode e : n) if (e.isTextual()) out.add(e.asText());
        return out;
    }

    private static String stripFence(String s) {
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl < 0) throw new AnalysisResultParseException("Unclosed fence");
            String tail = s.substring(nl + 1);
            int end = tail.lastIndexOf("```");
            if (end < 0) throw new AnalysisResultParseException("Unclosed fence");
            return tail.substring(0, end).trim();
        }
        return s;
    }
}
