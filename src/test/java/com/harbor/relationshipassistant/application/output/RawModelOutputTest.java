package com.harbor.relationshipassistant.application.output;

import com.harbor.relationshipassistant.application.skill.context.AnalysisFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RawModelOutputTest {

    @Test
    void preservesPlainTextContent() {
        String raw = "我觉得他今天其实有点冷淡，你可以先别急着追问。";
        RawModelOutput out = new RawModelOutput(raw, "deepseek-chat",
                AnalysisFunction.QUICK_REPLY, "quick-reply", null);
        assertEquals(raw, out.getContent());
        assertEquals("quick-reply", out.getSkillName());
        assertEquals(AnalysisFunction.QUICK_REPLY, out.getFunction());
        assertNotNull(out.getCreatedAt());
    }

    @Test
    void doesNotParseJsonContent() {
        String raw = "{\"analysis\":\"...\",\"advice\":\"...\"}";
        RawModelOutput out = new RawModelOutput(raw, "deepseek-chat",
                AnalysisFunction.QUICK_REPLY, "json-skill", null);
        // Runtime 不解析 JSON，content 原样保留
        assertEquals(raw, out.getContent());
    }
}
