package com.harbor.relationshipassistant.application.distill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QQ 聊天记录解析器（Java 内置，无需 Python）。
 *
 * 支持格式：
 *   .txt —— QQ 消息管理器导出的纯文本，典型格式：
 *            "2024-01-01 12:30 张三: 你好"
 *            "张三 2024-01-01 12:30\n你好"
 *   .csv —— 三列：时间,发送人,内容
 *   .html —— QQ 导出的 HTML，用 Jsoup 提取
 *
 * 不支持 QQ 本地数据库（Msg3.0.db），需要先由用户用 QQ 自带"消息管理器"导出。
 */
public final class QqChatParser {
    private static final DateTimeFormatter[] FMTS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy/M/d HH:mm", Locale.ROOT),
    };

    // "2024-01-01 12:30 张三: 你好"
    private static final Pattern LINE_A = Pattern.compile(
            "^(\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}[ T]\\d{1,2}:\\d{2}(?::\\d{2})?)\\s+([^:：]{1,30}?)[:：]\\s*(.+)$");
    // "张三(2024-01-01 12:30): 你好"
    private static final Pattern LINE_B = Pattern.compile(
            "^([^:：(（]{1,30}?)[（(](\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}[ T]\\d{1,2}:\\d{2}(?::\\d{2})?)[)）]\\s*[:：]?\\s*(.+)$");

    private QqChatParser() {}

    public record QqMessage(LocalDateTime time, String sender, String content) {}

    public static List<QqMessage> parse(Path file, String targetName) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("QQ 导出文件不是普通文件: " + file);
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv")) return parseCsv(file, targetName);
        if (name.endsWith(".html") || name.endsWith(".htm")) return parseHtml(file, targetName);
        return parseTxt(file, targetName);
    }

    private static List<QqMessage> parseTxt(Path file, String target) throws IOException {
        List<QqMessage> out = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty()) continue;
            Matcher m = LINE_A.matcher(line);
            if (m.matches()) {
                LocalDateTime t = tryParse(m.group(1));
                String sender = m.group(2).trim();
                String content = m.group(3).trim();
                if (target == null || target.isBlank() || sender.contains(target)) {
                    out.add(new QqMessage(t, sender, content));
                }
                continue;
            }
            m = LINE_B.matcher(line);
            if (m.matches()) {
                String sender = m.group(1).trim();
                LocalDateTime t = tryParse(m.group(2));
                String content = m.group(3).trim();
                if (target == null || target.isBlank() || sender.contains(target)) {
                    out.add(new QqMessage(t, sender, content));
                }
            }
        }
        return out;
    }

    private static List<QqMessage> parseCsv(Path file, String target) throws IOException {
        List<QqMessage> out = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("时间") || line.startsWith("timestamp")) continue;
            String[] parts = line.split(",", 3);
            if (parts.length < 3) continue;
            LocalDateTime t = tryParse(parts[0].trim());
            String sender = parts[1].trim();
            String content = parts[2].trim();
            if (target == null || target.isBlank() || sender.contains(target)) {
                out.add(new QqMessage(t, sender, content));
            }
        }
        return out;
    }

    private static List<QqMessage> parseHtml(Path file, String target) throws IOException {
        // 轻量实现：用正则提取 <div ...>...</div> 里的文本，避免引入 Jsoup 依赖
        String html = Files.readString(file, StandardCharsets.UTF_8);
        List<QqMessage> out = new ArrayList<>();
        Matcher m = Pattern.compile(
                "(\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}[ T]\\d{1,2}:\\d{2}(?::\\d{2})?)\\s*[-—]\\s*([^<:：]{1,30}?)[:：]?\\s*([^<]+)").matcher(html);
        while (m.find()) {
            LocalDateTime t = tryParse(m.group(1));
            String sender = m.group(2).trim();
            String content = m.group(3).trim();
            if (target == null || target.isBlank() || sender.contains(target)) {
                out.add(new QqMessage(t, sender, content));
            }
        }
        return out;
    }

    private static LocalDateTime tryParse(String s) {
        s = s.trim().replace("T", " ");
        for (DateTimeFormatter f : FMTS) {
            try { return LocalDateTime.parse(s, f); } catch (Exception ignored) {}
        }
        return null;
    }

    public static String formatAsText(List<QqMessage> msgs) {
        StringBuilder sb = new StringBuilder();
        for (QqMessage m : msgs) {
            sb.append("[").append(m.time() == null ? "?" : m.time().toLocalDate()).append("] ")
              .append(m.sender()).append(": ").append(m.content()).append("\n");
        }
        return sb.toString();
    }
}
