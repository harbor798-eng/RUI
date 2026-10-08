package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.application.llm.AnalysisResult;
import com.harbor.relationshipassistant.application.skill.context.AnalysisFunction;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuickReplyAdapterTest {

    private final QuickReplyAdapter adapter = new QuickReplyAdapter();

    private AnalysisResult resultOf(String content) {
        return new AnalysisResult(AnalysisFunction.QUICK_REPLY, "quick-reply",
                content, "deepseek-chat", LocalDateTime.now());
    }

    @Test
    void test1_fullThreeStrategyJson() {
        String json = "{\"strategies\":[" +
                "{\"key\":\"NATURAL\",\"replies\":[{\"text\":\"在的。\"}]}," +
                "{\"key\":\"PROACTIVE\",\"replies\":[{\"text\":\"周末出来吃饭？\"}]}," +
                "{\"key\":\"LIGHT_FLIRT\",\"replies\":[{\"text\":\"想你了。\"}]}" +
                "]}";
        QuickReplyAdapter.Result r = adapter.adapt(resultOf(json));
        assertEquals(3, r.strategies().size());
        assertEquals("NATURAL", r.strategies().get(0).key());
        assertEquals("在的。", r.strategies().get(0).replies().get(0));
    }

    @Test
    void test2_missingStrategyDoesNotThrow() {
        String json = "{\"strategies\":[" +
                "{\"key\":\"NATURAL\",\"replies\":[{\"text\":\"在的。\"}]}" +
                "]}";
        QuickReplyAdapter.Result r = adapter.adapt(resultOf(json));
        assertEquals(1, r.strategies().size());
        assertEquals("NATURAL", r.strategies().get(0).key());
    }

    @Test
    void test3_plainText() {
        QuickReplyAdapter.Result r = adapter.adapt(resultOf("嗯，我觉得你可以先别急着解释，等她回复再说。"));
        assertEquals(1, r.strategies().size());
        assertEquals("SINGLE", r.strategies().get(0).key());
        assertTrue(r.strategies().get(0).replies().get(0).contains("等她回复"));
    }

    @Test
    void test4_markdown() {
        String md = "# 建议\n我觉得你现在应该先等等。";
        QuickReplyAdapter.Result r = adapter.adapt(resultOf(md));
        assertEquals(1, r.strategies().size());
        assertEquals("SINGLE", r.strategies().get(0).key());
        assertTrue(r.strategies().get(0).replies().get(0).startsWith("# 建议"));
    }

    @Test
    void test5_otherJsonNotTreatedAsThreeStrategy() {
        String json = "{\"reply\":\"你可以先等等她的回复。\"}";
        QuickReplyAdapter.Result r = adapter.adapt(resultOf(json));
        assertEquals(1, r.strategies().size());
        assertEquals("SINGLE", r.strategies().get(0).key());
        // 整个 JSON 字符串作为 replyText 保留
        assertTrue(r.strategies().get(0).replies().get(0).contains("reply"));
    }

    @Test
    void test6_emptyContentBecomesSingle() {
        QuickReplyAdapter.Result r = adapter.adapt(resultOf(""));
        assertEquals(1, r.strategies().size());
        assertEquals("SINGLE", r.strategies().get(0).key());
    }
}
