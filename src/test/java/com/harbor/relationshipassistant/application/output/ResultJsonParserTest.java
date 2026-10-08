package com.harbor.relationshipassistant.application.output;

import com.harbor.relationshipassistant.application.systemhost.Audience;
import com.harbor.relationshipassistant.application.systemhost.ResultSpec;
import com.harbor.relationshipassistant.application.systemhost.ResultType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 5-2: ResultJsonParser 严格按 ResultSpec 解析，缺失即缺失，不 fallback。
 */
class ResultJsonParserTest {

    private final ResultJsonParser parser = new ResultJsonParser();

    @Test
    void threeByThree_parses9ItemsGroupedByStrategy() {
        String json = "```json\n" +
                "{\"items\":[" +
                slot("SENDABLE_REPLY","OTHER","NATURAL","自然1") + "," +
                slot("SENDABLE_REPLY","OTHER","NATURAL","自然2") + "," +
                slot("SENDABLE_REPLY","OTHER","NATURAL","自然3") + "," +
                slot("SENDABLE_REPLY","OTHER","PROACTIVE","主动1") + "," +
                slot("SENDABLE_REPLY","OTHER","PROACTIVE","主动2") + "," +
                slot("SENDABLE_REPLY","OTHER","PROACTIVE","主动3") + "," +
                slot("SENDABLE_REPLY","OTHER","LIGHT_FLIRT","暧昧1") + "," +
                slot("SENDABLE_REPLY","OTHER","LIGHT_FLIRT","暧昧2") + "," +
                slot("SENDABLE_REPLY","OTHER","LIGHT_FLIRT","暧昧3") +
                "]}\n```";
        var out = parser.parse(json, ResultSpec.quickReplyDefaultThreeStrategy());
        assertEquals(9, out.items().size());
        assertFalse(out.degraded());
        assertEquals(ResultType.SENDABLE_REPLY, out.items().get(0).type());
        assertEquals(Audience.OTHER, out.items().get(0).audience());
        assertEquals("NATURAL", out.items().get(0).strategyKey());
        assertEquals("LIGHT_FLIRT", out.items().get(8).strategyKey());
    }

    @Test
    void tongJincheng_parsesReplyPlusShortAnalysis() {
        String json = "{\"items\":[" +
                slot("SENDABLE_REPLY","OTHER",null,"给对方的话") + "," +
                slot("SHORT_ANALYSIS","USER",null,"兄弟点评") +
                "]}";
        var out = parser.parse(json, ResultSpec.quickReplyTongJincheng());
        assertEquals(2, out.items().size());
        assertFalse(out.degraded());
        assertEquals(ResultType.SENDABLE_REPLY, out.items().get(0).type());
        assertEquals(ResultType.SHORT_ANALYSIS, out.items().get(1).type());
    }

    @Test
    void nonJsonRaw_fallsBackToDegradedEmpty_notFakedAsReply() {
        // Stage 5-1 审核：不允许把未知文本冒充 SENDABLE_REPLY。
        String raw = "这里是我自由发挥的一段话，不是 JSON。";
        var out = parser.parse(raw, ResultSpec.quickReplySingleSendable());
        assertTrue(out.degraded());
        assertTrue(out.items().isEmpty());
    }

    @Test
    void underfilledSlot_marksDegraded_keepsMissingMissing() {
        String json = "{\"items\":[" + slot("SENDABLE_REPLY","OTHER","NATURAL","只给了1条") + "]}";
        var out = parser.parse(json, ResultSpec.quickReplyDefaultThreeStrategy());
        assertEquals(1, out.items().size());
        assertTrue(out.degraded());
    }

    @Test
    void llmDeclaredWrongType_isDropped_notMappedToSpec() {
        // LLM 把 SHORT_ANALYSIS 塞到 SENDABLE_REPLY 的桶里 → 丢弃，不冒充给对方。
        String json = "{\"items\":[" + slot("SHORT_ANALYSIS","USER",null,"分析") + "]}";
        var out = parser.parse(json, ResultSpec.quickReplySingleSendable());
        assertTrue(out.items().isEmpty());
        assertTrue(out.degraded());
    }

    private static String slot(String type, String audience, String strategy, String text) {
        String s = strategy == null ? "null" : ("\"" + strategy + "\"");
        return "{\"type\":\"" + type + "\",\"audience\":\"" + audience +
                "\",\"strategyKey\":" + s + ",\"text\":\"" + text + "\"}";
    }
}
