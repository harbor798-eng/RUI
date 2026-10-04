package com.harbor.relationshipassistant.infrastructure.importer;

import com.harbor.relationshipassistant.domain.chat.MessageType;
import com.harbor.relationshipassistant.domain.chat.SenderType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class HtmlChatImporterTest {

    @TempDir
    Path tempDir;

    private static final String HTML = """
            <html><body>
            <div class="chat-item">
              <div class="time">2024-05-01 12:30</div>
              <div class="speaker">我</div>
              <div class="bubble">在干嘛呢</div>
            </div>
            <div class="chat-item">
              <div class="time">2024-05-01 12:31</div>
              <div class="speaker">小美</div>
              <div class="bubble">[图片]</div>
            </div>
            <div class="chat-item">
              <div class="time">2024-05-01 12:32:10</div>
              <div class="speaker">我</div>
              <div class="bubble">晚上吃火锅？</div>
            </div>
            </body></html>
            """;

    private ImportPreview parse(String html, String selfNick) throws Exception {
        Path file = tempDir.resolve("chat.html");
        Files.writeString(file, html);
        ImportRequest req = new ImportRequest();
        req.setImporterType("HTML");
        req.setSourceLocation(file.toString());
        req.setSelfNickname(selfNick);
        return new HtmlChatImporter().parse(req);
    }

    @Test
    void parsesChatItems() throws Exception {
        ImportPreview preview = parse(HTML, "我");
        assertEquals(3, preview.getTotalParsed());
        assertEquals(SenderType.ME, preview.getMessages().get(0).getSenderType());
        assertEquals("在干嘛呢", preview.getMessages().get(0).getContent());
        assertEquals(SenderType.OTHER, preview.getMessages().get(1).getSenderType());
        assertEquals(MessageType.IMAGE, preview.getMessages().get(1).getMessageType());
        assertNotNull(preview.getMessages().get(0).getSourceHash());
    }

    @Test
    void identityMappingByNickname() throws Exception {
        ImportPreview p = parse(HTML, "我");
        assertEquals(2, p.getMeCount());
        assertEquals(1, p.getOtherCount());
        // 换一个昵称：原来叫"我"的行不再算 ME
        ImportPreview p2 = parse(HTML, "不存在的人");
        assertEquals(0, p2.getMeCount());
        assertEquals(3, p2.getOtherCount());
    }

    @Test
    void timeParsesAsWallClock() throws Exception {
        ImportPreview p = parse(HTML, "我");
        assertEquals(LocalDateTime.of(2024, 5, 1, 12, 30, 0), p.getMessages().get(0).getMessageTime());
        assertEquals(LocalDateTime.of(2024, 5, 1, 12, 32, 10), p.getMessages().get(2).getMessageTime());
        assertEquals(LocalDateTime.of(2024, 5, 1, 12, 30, 0), p.getEarliest());
    }

    @Test
    void sourceContentSurvivesUserEdit() throws Exception {
        ImportPreview p = parse(HTML, "我");
        ImportedRawMessage m = p.getMessages().get(0);
        String original = m.getSourceContent();
        assertNotNull(original);
        assertEquals(original, m.getContent());
        // 用户在 Preview 修改 content，sourceContent 必须不变
        m.setContent("改过的内容");
        assertEquals("在干嘛呢", m.getSourceContent());
        assertEquals("改过的内容", m.getContent());
    }

    @Test
    void idempotencyKeyIsStableAcrossReParse() throws Exception {
        ImportPreview p1 = parse(HTML, "我");
        ImportPreview p2 = parse(HTML, "我");
        for (int i = 0; i < p1.getTotalParsed(); i++) {
            String id1 = p1.getMessages().get(i).getSourceMessageId();
            String id2 = p2.getMessages().get(i).getSourceMessageId();
            assertNotNull(id1);
            assertTrue(id1.startsWith("html:"));
            assertEquals(id1, id2, "同一文件重复解析的幂等键必须一致");
        }
    }

    @Test
    void malformedHtmlDoesNotCrash() throws Exception {
        String bad = """
                <html><body>
                <div class="chat-item">
                  <div class="time">2026-01-01 10:00</div>
                  <div class="speaker">我</div>
                </div>
                <div class="chat-item">
                  <div class="speaker">小美</div>
                  <div class="bubble">只有内容没有时间</div>
                </div>
                </body></html>
                """;
        ImportPreview p = parse(bad, "我");
        // 缺 bubble 的行退化为整段文本（不崩溃、不丢行）；缺时间的行按墙钟解析
        assertEquals(2, p.getTotalParsed());
        assertEquals("只有内容没有时间", p.getMessages().get(1).getContent());
        assertTrue(p.getMessages().get(0).getContent().contains("2026-01-01"));
    }

    @Test
    void noTimeRowIdempotencyKeyIsStable() throws Exception {
        String bad = """
                <html><body>
                <div class="chat-item">
                  <div class="speaker">小美</div>
                  <div class="bubble">你撤回了一条消息</div>
                </div>
                </body></html>
                """;
        ImportPreview p1 = parse(bad, "我");
        ImportPreview p2 = parse(bad, "我");
        String id1 = p1.getMessages().get(0).getSourceMessageId();
        String id2 = p2.getMessages().get(0).getSourceMessageId();
        assertNotNull(id1);
        assertTrue(id1.startsWith("html:nt:"), "无时间行必须用稳定前缀");
        assertEquals(id1, id2, "无时间行重复解析幂等键必须一致（不能带 now()）");
    }
}
