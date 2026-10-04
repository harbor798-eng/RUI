package com.harbor.relationshipassistant.infrastructure.importer;

import com.harbor.relationshipassistant.common.exception.ImportException;
import com.harbor.relationshipassistant.domain.chat.MessageType;
import com.harbor.relationshipassistant.domain.chat.SenderType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML 聊天记录导入器（技术设计 §8）。
 *
 * 约定解析的 HTML 结构（不同导出工具版本有差异，PRD §51 列为待定项，
 * 后续按真实样本扩展选择器；此处先支持主流"聊天记录导出"结构）：
 * <pre>
 * &lt;div class="chat-item"&gt;
 *   &lt;div class="time"&gt;2024-05-01 12:30&lt;/div&gt;
 *   &lt;div class="speaker"&gt;我&lt;/div&gt;
 *   &lt;div class="bubble"&gt;消息内容&lt;/div&gt;
 * &lt;/div&gt;
 * </pre>
 * 不修改外部原始 HTML（PRD §6.4 只读）。
 */
public class HtmlChatImporter implements ChatImporter {

    private static final Logger log = LoggerFactory.getLogger(HtmlChatImporter.class);
    private static final DateTimeFormatter[] TIME_FORMATS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    };
    private static final Pattern TIME_PATTERN =
            Pattern.compile("(\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}[ T]\\d{1,2}:\\d{2}(:\\d{2})?)");

    @Override
    public String type() { return "HTML"; }

    @Override
    public ImportPreview parse(ImportRequest request) {
        Path file = Path.of(request.getSourceLocation());
        if (!Files.isReadable(file)) {
            throw new ImportException("HTML 文件不可读: " + request.getSourceLocation(), "HtmlChatImporter.parse");
        }
        ImportPreview preview = new ImportPreview();
        preview.setImporterType(type());
        preview.setSourceLocation(request.getSourceLocation());

        try {
            String html = Files.readString(file);
            Document doc = Jsoup.parse(html);
            for (Element item : doc.select("div.chat-item, li.chat-item")) {
                try {
                    ImportedRawMessage m = parseItem(item, request);
                    if (m != null) preview.getMessages().add(m);
                } catch (Exception e) {
                    preview.getParseErrors().add("单行解析失败: " + e.getMessage());
                }
            }
            // 兜底：没有 chat-item 结构时，按时间正则粗略提取
            if (preview.getMessages().isEmpty()) {
                preview.getParseErrors().add("未识别到 .chat-item 结构，已降级为时间正则粗解析，可能不完整");
                parseByTimeHeuristic(doc, request, preview);
            }
        } catch (IOException e) {
            throw new ImportException("读取 HTML 文件失败", "HtmlChatImporter.parse", e);
        }
        log.info("[HTML_PARSE] file={} total={} me={} other={} system={} errors={}",
                request.getSourceLocation(), preview.getTotalParsed(),
                preview.getMeCount(), preview.getOtherCount(), preview.getSystemCount(),
                preview.getParseErrors().size());
        return preview;
    }

    private ImportedRawMessage parseItem(Element item, ImportRequest request) {
        String timeText = item.selectFirst("div.time, span.time") != null
                ? item.selectFirst("div.time, span.time").text().trim() : "";
        String speaker = item.selectFirst("div.speaker, span.speaker") != null
                ? item.selectFirst("div.speaker, span.speaker").text().trim() : "";
        Element bubble = item.selectFirst("div.bubble, span.bubble");
        String content = bubble != null ? bubble.text().trim() : item.text().trim();
        if (content.isEmpty()) return null;

        LocalDateTime time = parseTime(timeText);
        SenderType sender = isMe(speaker, request) ? SenderType.ME : SenderType.OTHER;
        MessageType type = detectType(content);

        ImportedRawMessage m = new ImportedRawMessage();
        m.setMessageTime(time);
        m.setSenderType(sender);
        m.setMessageType(type);
        m.setContent(content);
        // 原始内容：用户在 Preview 中改 content 不覆盖它；source_message_id 也只绑定原始时间+原始内容，
        // 与用户对 ME/OTHER 的修正解耦，保证重复导入同一文件幂等。
        m.setSourceContent(content);
        // 时间缺失/无法解析的行：now() 每次解析都不同，不能进幂等键，否则重复导入会重复入库。
        // 这类行用「无时间前缀 + 内容哈希」作为稳定幂等键。
        boolean timeKnown = hasKnownTime(timeText);
        m.setSourceMessageId(timeKnown
                ? "html:" + hash(time + "|" + content)
                : "html:nt:" + hash(content));
        m.setSourceHash(hash(time + "|" + sender + "|" + content));
        return m;
    }

    /** 该 timeText 是否能解析出明确时间（区分「未知时间」行，避免 now() 破坏幂等键）。 */
    private boolean hasKnownTime(String timeText) {
        if (timeText == null || timeText.isBlank()) return false;
        String t = timeText.trim();
        for (DateTimeFormatter f : TIME_FORMATS) {
            try { LocalDateTime.parse(t, f); return true; } catch (Exception ignored) {}
        }
        return false;
    }

    private void parseByTimeHeuristic(Document doc, ImportRequest request, ImportPreview preview) {
        String text = doc.body() != null ? doc.body().text() : "";
        Matcher m = TIME_PATTERN.matcher(text);
        int idx = 0;
        while (m.find()) {
            ImportedRawMessage msg = new ImportedRawMessage();
            LocalDateTime t = parseTime(m.group(1));
            msg.setMessageTime(t);
            msg.setSenderType(idx++ % 2 == 0 ? SenderType.OTHER : SenderType.ME);
            msg.setMessageType(MessageType.TEXT);
            msg.setContent("(粗解析，请人工核对)");
            msg.setSourceContent(msg.getContent());
            msg.setSourceMessageId("html:" + hash(t + "|" + msg.getContent()));
            preview.getMessages().add(msg);
        }
    }

    private boolean isMe(String speaker, ImportRequest request) {
        String self = request.getSelfNickname();
        if (self == null || self.isBlank()) {
            // 无 selfNickname 时默认"我"字开头为 ME
            return speaker.contains("我");
        }
        return speaker.equals(self) || speaker.contains(self);
    }

    private MessageType detectType(String content) {
        if (content.startsWith("[图片]") || content.startsWith("（图片）")) return MessageType.IMAGE;
        if (content.startsWith("[语音]")) return MessageType.VOICE;
        if (content.startsWith("[视频]")) return MessageType.VIDEO;
        if (content.startsWith("[通话") || content.startsWith("通话时长")) return MessageType.CALL;
        if (content.startsWith("[转账]") || content.startsWith("转账")) return MessageType.TRANSFER;
        return MessageType.TEXT;
    }

    private LocalDateTime parseTime(String text) {
        if (text == null || text.isBlank()) return LocalDateTime.now();
        String t = text.trim();
        for (DateTimeFormatter f : TIME_FORMATS) {
            try { return LocalDateTime.parse(t, f); } catch (Exception ignored) {}
        }
        Matcher m = TIME_PATTERN.matcher(t);
        if (m.find()) {
            String g = m.group(1).replace("/", "-").replace("T", " ");
            for (DateTimeFormatter f : TIME_FORMATS) {
                try { return LocalDateTime.parse(g, f); } catch (Exception ignored) {}
            }
        }
        return LocalDateTime.now();
    }

    static String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }
}
