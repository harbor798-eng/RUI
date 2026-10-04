package com.harbor.relationshipassistant.application.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harbor.relationshipassistant.domain.analysis.quickreply.QuickReplyCandidate;
import com.harbor.relationshipassistant.domain.analysis.quickreply.QuickReplyResult;
import com.harbor.relationshipassistant.domain.analysis.quickreply.QuickReplyStrategy;
import com.harbor.relationshipassistant.infrastructure.ai.AIResponse;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** AIResponse → {@link QuickReplyResult}。严格要求恰好 3 个候选，策略无重复。 */
public class QuickReplyParser {

    private final ObjectMapper mapper = new ObjectMapper();

    public QuickReplyResult parse(AIResponse response) {
        System.out.println("[QuickReplyParser] parsing");
        if (response == null || response.text() == null || response.text().isBlank()) {
            throw new AnalysisResultParseException("QuickReply AI output is empty");
        }
        String json = stripFence(response.text().trim());
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (Exception e) {
            throw new AnalysisResultParseException("QuickReply output is not valid JSON: " + e.getMessage(), e);
        }
        JsonNode arr = root.path("candidates");
        if (!arr.isArray()) throw new AnalysisResultParseException("QuickReply: candidates must be array");
        if (arr.size() != 3) throw new AnalysisResultParseException("QuickReply: candidates must have exactly 3, got " + arr.size());

        List<QuickReplyCandidate> out = new ArrayList<>();
        Set<QuickReplyStrategy> seen = EnumSet.noneOf(QuickReplyStrategy.class);
        for (JsonNode c : arr) {
            String s = c.path("strategy").asText("");
            QuickReplyStrategy st;
            try {
                st = QuickReplyStrategy.valueOf(s);
            } catch (IllegalArgumentException e) {
                throw new AnalysisResultParseException("QuickReply: invalid strategy: " + s);
            }
            if (!seen.add(st)) throw new AnalysisResultParseException("QuickReply: duplicate strategy: " + st);
            String text = c.path("replyText").asText("").trim();
            if (text.isEmpty()) throw new AnalysisResultParseException("QuickReply: empty replyText for " + st);
            out.add(new QuickReplyCandidate(st, text));
        }
        return new QuickReplyResult(out);
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
