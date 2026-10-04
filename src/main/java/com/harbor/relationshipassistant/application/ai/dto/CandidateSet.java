package com.harbor.relationshipassistant.application.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harbor.relationshipassistant.common.exception.AIException;
import com.harbor.relationshipassistant.domain.ai.ReplyStrategy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek 返回的严格 JSON 结构（规范 §18）：
 * { "strategies": [ { "name": "自然", "replies": [ {"text":"...","reason":"..."}, x3 ] }, x3 ] }
 * 本 DTO 只做解析与校验，不落库（Phase 1 候选在内存）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CandidateSet {

    public static class Reply {
        public String text;
        public String reason;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Strategy {
        public String name;
        public List<Reply> replies = new ArrayList<>();
    }

    public List<Strategy> strategies = new ArrayList<>();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 解析并校验 AI 返回。校验失败抛 AIException（调用方决定是否重试）。
     *
     * @param expected 本阶段允许的 3 个策略（顺序敏感）
     */
    public static CandidateSet parseAndValidate(String json, List<ReplyStrategy> expected) {
        CandidateSet set;
        try {
            set = MAPPER.readValue(json == null ? "{}" : json, CandidateSet.class);
        } catch (Exception e) {
            throw new AIException("AI 返回不是合法 JSON", "CandidateSet.parse", e);
        }
        return validate(set, expected);
    }

    public static CandidateSet validate(CandidateSet set, List<ReplyStrategy> expected) {
        if (set == null || set.strategies == null) {
            throw new AIException("AI 返回缺少 strategies", "CandidateSet.validate");
        }
        if (set.strategies.size() != expected.size()) {
            throw new AIException("策略数量不对: expected=" + expected.size() + " got=" + set.strategies.size(),
                    "CandidateSet.validate");
        }
        List<String> expectedNames = expected.stream().map(ReplyStrategy::getLabel).toList();
        List<String> seen = new ArrayList<>();
        for (int i = 0; i < set.strategies.size(); i++) {
            Strategy s = set.strategies.get(i);
            if (s == null || s.name == null || !expectedNames.contains(s.name)) {
                throw new AIException("策略非法: " + (s == null ? "null" : s.name), "CandidateSet.validate");
            }
            seen.add(s.name);
            if (s.replies == null || s.replies.size() != 3) {
                throw new AIException("策略[" + s.name + "]候选数量不对", "CandidateSet.validate");
            }
            for (Reply r : s.replies) {
                if (r == null || r.text == null || r.text.isBlank()
                        || r.reason == null || r.reason.isBlank()) {
                    throw new AIException("策略[" + s.name + "]存在空 text/reason", "CandidateSet.validate");
                }
            }
        }
        return set;
    }

    /** 单策略 AI 返回：{"strategy":"自然","replies":[{"text":"...","reason":"..."}]} */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SingleResponse {
        public String strategy;
        public List<Reply> replies = new ArrayList<>();
    }

    public record SingleResult(String strategyLabel, String text, String reason) {}

    public static SingleResult parseSingle(String json, String expectedLabel) {
        SingleResponse s;
        try {
            s = MAPPER.readValue(json == null ? "{}" : json, SingleResponse.class);
        } catch (Exception e) {
            throw new AIException("单策略 AI 返回不是合法 JSON", "CandidateSet.parseSingle", e);
        }
        if (s.strategy == null || !s.strategy.equals(expectedLabel)) {
            throw new AIException("单策略 strategy 不匹配: expected=" + expectedLabel + " got=" + s.strategy, "parseSingle");
        }
        if (s.replies == null || s.replies.isEmpty() || s.replies.size() > 3) {
            throw new AIException("单策略 replies 数量必须 1~3", "parseSingle");
        }
        Reply r = s.replies.get(0);
        if (r == null || r.text == null || r.text.isBlank() || r.reason == null || r.reason.isBlank()) {
            throw new AIException("单策略 text/reason 为空", "parseSingle");
        }
        return new SingleResult(s.strategy, r.text.trim(), r.reason.trim());
    }
    /** 扁平化：9 个候选（策略名, text, reason）。 */
    public List<Map<String, String>> flatten() {
        List<Map<String, String>> out = new ArrayList<>();
        for (Strategy s : strategies) {
            for (Reply r : s.replies) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("strategy", s.name);
                m.put("text", r.text.trim());
                m.put("reason", r.reason.trim());
                out.add(m);
            }
        }
        return out;
    }
}
