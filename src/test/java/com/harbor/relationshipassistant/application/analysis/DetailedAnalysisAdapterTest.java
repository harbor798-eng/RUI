package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.application.llm.AnalysisResult;
import com.harbor.relationshipassistant.application.skill.context.AnalysisFunction;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DetailedAnalysisAdapterTest {

    private final DetailedAnalysisAdapter adapter = new DetailedAnalysisAdapter();

    private AnalysisResult resultOf(String content) {
        return new AnalysisResult(AnalysisFunction.DETAILED_ANALYSIS, "goutoujunshi", content, "test-model", LocalDateTime.now());
    }

    @Test
    void unwrapsItemsArrayWhenRootFieldsMissing() {
        String json = """
        {"items":[{
          "summary":"测试结论",
          "facts":["事实一"],
          "emotions":{"me":[{"label":"紧张","hint":"h"}],"other":[]},
          "reportMarkdown":"# 报告"
        }]}
        """;
        DetailedAnalysisAdapter.Result r = adapter.adapt(resultOf(json));
        assertEquals("测试结论", r.summary());
        assertEquals(1, r.facts().size());
        assertEquals("事实一", r.facts().get(0));
        assertEquals(1, r.meEmotions().size());
        assertEquals("紧张", r.meEmotions().get(0).label());
        assertEquals("# 报告", r.reportMarkdown());
        assertFalse(r.reportMarkdown().trim().startsWith("{"));
    }

    @Test
    void readsFlatRootFieldsDirectly() {
        String json = """
        {"summary":"直接结论","facts":["f1"],"reportMarkdown":"# 直接报告"}
        """;
        DetailedAnalysisAdapter.Result r = adapter.adapt(resultOf(json));
        assertEquals("直接结论", r.summary());
        assertEquals(1, r.facts().size());
        assertEquals("# 直接报告", r.reportMarkdown());
    }

    @Test
    void emptyItemsFallsBackToRawText() {
        String json = """
        {"items":[],"other":"x"}
        """;
        DetailedAnalysisAdapter.Result r = adapter.adapt(resultOf(json));
        assertEquals("", r.summary());
        // reportMarkdown should be the raw text fallback
        assertTrue(r.reportMarkdown().length() > 0);
    }

    @Test
    void nonJsonContentFallsBackToRawText() {
        DetailedAnalysisAdapter.Result r = adapter.adapt(resultOf("这是一段纯文本分析"));
        assertEquals("", r.summary());
        assertEquals("这是一段纯文本分析", r.reportMarkdown());
    }

    @Test
    void itemsFirstNotObjectFallsBack() {
        String json = """
        {"items":["not-an-object"]}
        """;
        DetailedAnalysisAdapter.Result r = adapter.adapt(resultOf(json));
        assertEquals("", r.summary());
        assertTrue(r.reportMarkdown().length() > 0);
    }

    @Test
    void itemsUnwrapExtractsAllFields() {
        String json = """
        {"items":[{
          "summary":"S",
          "facts":["f1","f2"],
          "inferences":["i1"],
          "possibilities":[{"content":"p1","confidence":"high"}],
          "recommendation":{"action":"do","reason":"why"},
          "selfCare":{"show":true,"reason":"take care"},
          "emotions":{"me":[{"label":"e1","hint":"h1"}],"other":[{"label":"e2","hint":"h2"}]},
          "reportMarkdown":"# full"
        }]}
        """;
        DetailedAnalysisAdapter.Result r = adapter.adapt(resultOf(json));
        assertEquals("S", r.summary());
        assertEquals(2, r.facts().size());
        assertEquals(1, r.inferences().size());
        assertEquals(1, r.possibilities().size());
        assertEquals("DO", r.recommendation().action());
        assertTrue(r.selfCare().show());
        assertEquals(1, r.meEmotions().size());
        assertEquals(1, r.otherEmotions().size());
        assertEquals("# full", r.reportMarkdown());
    }

    @Test
    void stripsCodeFenceWithLeadingProse() {
        String content = "Here is the analysis:\n```json\n{\"summary\":\"结论\",\"facts\":[\"f1\"],\"reportMarkdown\":\"# 报告\"}\n```\nHope this helps!";
        DetailedAnalysisAdapter.Result r = adapter.adapt(resultOf(content));
        assertEquals("结论", r.summary());
        assertEquals("# 报告", r.reportMarkdown());
    }

    @Test
    void truncatedJsonReturnsDegradationNotRawJson() {
        // Truncated JSON: starts with { but missing closing braces
        String truncated = "{\"items\":[{\"summary\":\"部分结论\",\"facts\":[\"f1\"],\"text\":\"# 截断的报告内容，但字符串未闭合";
        DetailedAnalysisAdapter.Result r = adapter.adapt(resultOf(truncated));
        // Should not return raw JSON as reportMarkdown
        assertFalse(r.reportMarkdown().trim().startsWith("{"));
        // Should either recover via regex or show degradation message
        assertTrue(r.reportMarkdown().length() > 0);
    }

    @Test
    void malformedJsonStartingWithBraceReturnsDegradation() {
        String bad = "{\"summary\":\"结论但后面乱码!!!@#$%";
        DetailedAnalysisAdapter.Result r = adapter.adapt(resultOf(bad));
        // Should not silently show raw JSON
        assertFalse(r.reportMarkdown().trim().startsWith("{"));
        // Regex should extract the summary field value
        assertTrue(r.summary().contains("结论"));
    }

    @Test
    void plainTextNotJsonStillRendersAsMarkdown() {
        DetailedAnalysisAdapter.Result r = adapter.adapt(resultOf("# 纯文本分析\n\n这是模型直接输出的 Markdown。"));
        assertEquals("", r.summary());
        assertTrue(r.reportMarkdown().startsWith("#"));
    }

    @Test
    void extractJsonObjectSubstring() {
        // JSON embedded in other text
        String messy = "Sure! Here you go:\n{\"summary\":\"嵌入结论\",\"reportMarkdown\":\"# 嵌入报告\"}\nLet me know!";
        DetailedAnalysisAdapter.Result r = adapter.adapt(resultOf(messy));
        assertEquals("嵌入结论", r.summary());
        assertEquals("# 嵌入报告", r.reportMarkdown());
    }
}
