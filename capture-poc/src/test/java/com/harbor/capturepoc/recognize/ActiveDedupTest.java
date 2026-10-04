package com.harbor.capturepoc.recognize;

import org.junit.Test;
import static org.junit.Assert.*;

public class ActiveDedupTest {

    private static final long REL = 2L;
    private static final MessageRecognizer.Sender ME = MessageRecognizer.Sender.ME;
    private static final MessageRecognizer.Sender OTHER = MessageRecognizer.Sender.OTHER;

    @Test
    public void test1_sameTextMultipleFrames_onlyOneInsert() {
        ActiveDedup d = new ActiveDedup();
        long t0 = 1_000_000;
        ActiveDedup.Result r1 = d.check(REL, OTHER, "你好", 200, t0);
        assertEquals(ActiveDedup.Decision.NEW, r1.decision);

        // 同 frame 相同
        ActiveDedup.Result r2 = d.check(REL, OTHER, "你好", 200, t0 + 1000);
        assertEquals(ActiveDedup.Decision.DEDUP, r2.decision);
        assertEquals(r1.sourceMessageId, r2.sourceMessageId);

        ActiveDedup.Result r3 = d.check(REL, OTHER, "你好", 205, t0 + 2000);
        assertEquals(ActiveDedup.Decision.DEDUP, r3.decision);
        assertEquals(r1.sourceMessageId, r3.sourceMessageId);
    }

    @Test
    public void test2_ocrSlightVariation_reusesSourceId() {
        ActiveDedup d = new ActiveDedup();
        long t0 = 1_000_000;
        ActiveDedup.Result r1 = d.check(REL, OTHER, "是啊充电宝赌博", 632, t0);
        assertEquals(ActiveDedup.Decision.NEW, r1.decision);

        ActiveDedup.Result r2 = d.check(REL, OTHER, "是啊充电宝赌博i", 630, t0 + 1500);
        assertEquals(ActiveDedup.Decision.DEDUP, r2.decision);
        assertEquals(r1.sourceMessageId, r2.sourceMessageId);

        ActiveDedup.Result r3 = d.check(REL, OTHER, "是啊充电宝赌博", 631, t0 + 3000);
        assertEquals(ActiveDedup.Decision.DEDUP, r3.decision);
        assertEquals(r1.sourceMessageId, r3.sourceMessageId);
    }

    @Test
    public void test3_sameTextDifferentSender_twoInserts() {
        ActiveDedup d = new ActiveDedup();
        long t0 = 1_000_000;
        ActiveDedup.Result r1 = d.check(REL, OTHER, "你好", 200, t0);
        assertEquals(ActiveDedup.Decision.NEW, r1.decision);

        ActiveDedup.Result r2 = d.check(REL, ME, "你好", 200, t0 + 1000);
        assertEquals(ActiveDedup.Decision.NEW, r2.decision);
        assertNotEquals(r1.sourceMessageId, r2.sourceMessageId);
    }

    @Test
    public void test4_sameSenderDifferentY_twoInserts() {
        ActiveDedup d = new ActiveDedup();
        long t0 = 1_000_000;
        ActiveDedup.Result r1 = d.check(REL, OTHER, "你好", 200, t0);
        assertEquals(ActiveDedup.Decision.NEW, r1.decision);

        // y=300, dy=100 > 60
        ActiveDedup.Result r2 = d.check(REL, OTHER, "你好啊", 300, t0 + 1000);
        assertEquals(ActiveDedup.Decision.NEW, r2.decision);
        assertNotEquals(r1.sourceMessageId, r2.sourceMessageId);
    }

    @Test
    public void test5_longLivingBubble_updatesLastSeen_noReinsert() {
        ActiveDedup d = new ActiveDedup();
        long t0 = 1_000_000;
        ActiveDedup.Result r1 = d.check(REL, OTHER, "长消息内容测试", 400, t0);
        assertEquals(ActiveDedup.Decision.NEW, r1.decision);

        // 14 秒后仍在窗口内
        ActiveDedup.Result r2 = d.check(REL, OTHER, "长消息内容测试", 405, t0 + 14_000);
        assertEquals(ActiveDedup.Decision.DEDUP, r2.decision);
        assertEquals(r1.sourceMessageId, r2.sourceMessageId);

        // 又 14 秒后，lastSeenAt 已更新，仍在窗口
        ActiveDedup.Result r3 = d.check(REL, OTHER, "长消息内容测试", 403, t0 + 28_000);
        assertEquals(ActiveDedup.Decision.DEDUP, r3.decision);
        assertEquals(r1.sourceMessageId, r3.sourceMessageId);
    }

    @Test
    public void test6_afterWindow_sameTextNewBubble_newMessage() {
        ActiveDedup d = new ActiveDedup();
        long t0 = 1_000_000;
        ActiveDedup.Result r1 = d.check(REL, OTHER, "你好", 200, t0);
        assertEquals(ActiveDedup.Decision.NEW, r1.decision);

        // 16 秒后，超窗口，y 也变了
        ActiveDedup.Result r2 = d.check(REL, OTHER, "你好", 500, t0 + 16_000);
        assertEquals(ActiveDedup.Decision.NEW, r2.decision);
        assertNotEquals(r1.sourceMessageId, r2.sourceMessageId);
    }

    @Test
    public void test7_shortTextMustBeExact() {
        ActiveDedup d = new ActiveDedup();
        long t0 = 1_000_000;
        ActiveDedup.Result r1 = d.check(REL, OTHER, "测试A", 200, t0);
        assertEquals(ActiveDedup.Decision.NEW, r1.decision);

        // "测试B" 短文本不同，不命中
        ActiveDedup.Result r2 = d.check(REL, OTHER, "测试B", 205, t0 + 1000);
        assertEquals(ActiveDedup.Decision.NEW, r2.decision);
    }

    @Test
    public void test8_unknownSender_alwaysNew() {
        ActiveDedup d = new ActiveDedup();
        long t0 = 1_000_000;
        ActiveDedup.Result r1 = d.check(REL, MessageRecognizer.Sender.UNKNOWN, "模糊消息", 200, t0);
        assertEquals(ActiveDedup.Decision.NEW, r1.decision);
        ActiveDedup.Result r2 = d.check(REL, MessageRecognizer.Sender.UNKNOWN, "模糊消息", 200, t0 + 1000);
        assertEquals(ActiveDedup.Decision.NEW, r2.decision);
    }
}
