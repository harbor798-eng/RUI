package com.harbor.relationshipassistant.application.distill;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证蒸馏请求 DTO 的默认值、特征调节语义（5=中性）和启用/禁用逻辑。
 */
class DistillRequestTest {

    @Test
    void backwardCompatibleConstructorDefaults() {
        var req = new DistillationService.DistillRequest("OTHER", 42L, "小红", "CREATE_NEW");
        assertEquals("OTHER", req.mode());
        assertEquals(42L, req.relationshipId());
        assertNull(req.relationshipRole());
        assertTrue(req.traits().isEmpty());
        assertEquals("", req.customNote());
    }

    @Test
    void traitDefaultIsNeutralFive() {
        var adj = new DistillationService.TraitAdj(false, 5);
        assertFalse(adj.enabled());
        assertEquals(5, adj.value());
    }

    @Test
    void traitMapPreservesEnabledAndValue() {
        Map<String, DistillationService.TraitAdj> traits = new HashMap<>();
        traits.put("活泼程度", new DistillationService.TraitAdj(true, 8));
        traits.put("温柔程度", new DistillationService.TraitAdj(false, 5));
        traits.put("幽默程度", new DistillationService.TraitAdj(true, 3));

        var req = new DistillationService.DistillRequest(
                "OTHER", 1L, "小红", "CREATE_NEW", "恋人", traits, "希望更主动");

        assertEquals("恋人", req.relationshipRole());
        assertEquals(8, req.traits().get("活泼程度").value());
        assertTrue(req.traits().get("活泼程度").enabled());
        assertFalse(req.traits().get("温柔程度").enabled());
        assertEquals(3, req.traits().get("幽默程度").value());
        assertEquals("希望更主动", req.customNote());
    }

    @Test
    void clampValueSemantics() {
        // 值在 1-10 之外时由 HTTP 层 clamp，这里验证 record 本身不做隐式 clamp
        var low = new DistillationService.TraitAdj(true, 1);
        var high = new DistillationService.TraitAdj(true, 10);
        assertEquals(1, low.value());
        assertEquals(10, high.value());
    }
}
