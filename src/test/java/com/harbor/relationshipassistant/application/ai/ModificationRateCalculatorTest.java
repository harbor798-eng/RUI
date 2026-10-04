package com.harbor.relationshipassistant.application.ai;

import com.harbor.relationshipassistant.domain.ai.ModificationType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModificationRateCalculatorTest {

    private final ModificationRateCalculator calc = new ModificationRateCalculator();

    @Test
    void unchangedTextIsNone() {
        var r = calc.calculate("今天过得怎么样呀", "今天过得怎么样呀");
        assertEquals(1.0, r.similarity(), 0.0001);
        assertEquals(0.0, r.modificationRate(), 0.0001);
        assertEquals(ModificationType.NONE, r.type());
    }

    @Test
    void prdExampleIsSmallEdit() {
        // PRD §16 示例：AI=今天过得怎么样呀；用户=今天过得怎么样，忙不忙呀
        var r = calc.calculate("今天过得怎么样呀", "今天过得怎么样，忙不忙呀");
        assertTrue(r.modificationRate() > 0 && r.modificationRate() < 0.35,
                "小幅修改，实际 rate=" + r.modificationRate());
        assertEquals(ModificationType.SMALL, r.type());
    }

    @Test
    void rewriteIsRewrite() {
        var r = calc.calculate("今天过得怎么样呀", "晚上有空一起吃饭吗？");
        assertEquals(ModificationType.REWRITE, r.type());
    }

    @Test
    void emptyOriginal() {
        var r = calc.calculate("", "你好");
        assertEquals(1.0, r.modificationRate(), 0.0001);
        assertEquals(ModificationType.REWRITE, r.type());
    }
}
