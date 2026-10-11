package com.harbor.relationshipassistant.application.distill;

import com.harbor.relationshipassistant.domain.chat.ChatMessage;
import com.harbor.relationshipassistant.domain.chat.SenderType;
import com.harbor.relationshipassistant.infrastructure.persistence.ChatMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 多来源材料采集。
 *
 * 支持的输入类型（见 ExMaterialInput）：
 *   DB          —— RUI 数据库
 *   WECHAT_FILE —— 微信导出文件（wechat_parser.py）
 *   SMS_FILE    —— 短信导出（sms_parser.py）
 *   SOCIAL_FILE —— 社交媒体导出（social_media_parser.py）
 *   PHOTO_DIR   —— 照片目录（photo_analyzer.py）
 *   QQ_FILE     —— QQ 导出文件（Java 内置 QqChatParser）
 *
 * 多个来源会按顺序拼接成一段文本，总长度仍受 MAX_TOTAL_CHARS 限制。
 */
public class ExMaterialSource {
    private static final Logger log = LoggerFactory.getLogger(ExMaterialSource.class);

    private static final int MAX_DB_MESSAGES = 800;
    private static final int MIN_DB_MESSAGES = 20;
    private static final int MAX_TOTAL_CHARS = 12000;
    private static final long PY_TIMEOUT_SECONDS = 60;

    private final ChatMessageRepository chatRepo;

    public ExMaterialSource(ChatMessageRepository chatRepo) {
        this.chatRepo = chatRepo;
    }

    public record Material(String text, int messageCount, LocalDateTime from, LocalDateTime to,
                           int truncatedChars, String sourceDescription) {}

    /** 多入口：遍历 inputs，分别采集，合并成一段材料。 */
    public Material collect(long relationshipId, String targetName, List<ExMaterialInput> inputs)
            throws Exception {
        if (inputs == null || inputs.isEmpty()) {
            // 向后兼容：无输入默认走 DB
            inputs = List.of(ExMaterialInput.db());
        }
        StringBuilder sb = new StringBuilder();
        int totalCount = 0;
        LocalDateTime first = null, last = null;
        List<String> sources = new ArrayList<>();
        int truncatedChars = 0;

        for (ExMaterialInput in : inputs) {
            String type = in.type() == null ? "DB" : in.type();
            try {
                Material m = switch (type) {
                    case "DB" -> fromDatabase(relationshipId);
                    case "WECHAT_FILE" -> runPythonParser("wechat_parser.py",
                            new String[]{"--file", in.path(), "--target", targetName}, "微信导出");
                    case "SMS_FILE" -> runPythonParser("sms_parser.py",
                            new String[]{"--file", in.path(), "--target", targetName}, "短信导出");
                    case "SOCIAL_FILE" -> runPythonParser("social_media_parser.py",
                            new String[]{"--file", in.path()}, "社交媒体导出");
                    case "PHOTO_DIR" -> runPythonParser("photo_analyzer.py",
                            new String[]{"--dir", in.path()}, "照片目录");
                    case "QQ_FILE" -> fromQqFile(in.path(), targetName);
                    default -> throw new IllegalStateException("未知材料类型: " + type);
                };
                if (sb.length() + m.text().length() > MAX_TOTAL_CHARS) {
                    truncatedChars += m.text().length();
                    log.warn("[EX-MATERIAL] source={} truncated (over limit)", type);
                    continue;
                }
                sb.append("\n--- ").append(m.sourceDescription()).append(" ---\n").append(m.text());
                totalCount += m.messageCount();
                if (first == null) first = m.from();
                last = m.to() != null ? m.to() : last;
                sources.add(m.sourceDescription() + "(" + m.messageCount() + ")");
            } catch (Exception e) {
                log.warn("[EX-MATERIAL] source={} failed: {}", type, e.getMessage());
                throw e; // 任一来源失败即整体失败，不静默跳过
            }
        }

        if (totalCount == 0) {
            throw new IllegalStateException("所有材料来源均未产生有效消息");
        }
        log.info("[EX-MATERIAL] total sources={} messages={} truncated={} sources={}",
                sources.size(), totalCount, truncatedChars, sources);
        return new Material(truncate(sb.toString()), totalCount, first, last, truncatedChars,
                String.join(" + ", sources));
    }

    /** 兼容旧调用：单一路径。 */
    Material fromDatabase(long relationshipId) {
        List<ChatMessage> all = chatRepo.findLatest(relationshipId, MAX_DB_MESSAGES);
        List<ChatMessage> others = all.stream()
                .filter(m -> m.getSenderType() == SenderType.OTHER)
                .filter(m -> m.getContent() != null && !m.getContent().isBlank())
                .filter(m -> m.getMessageType() == null
                        || m.getMessageType().name().equals("TEXT")
                        || m.getMessageType().name().equals("EMOJI"))
                .collect(Collectors.toList());
        Collections.reverse(others);

        if (others.size() < MIN_DB_MESSAGES) {
            throw new IllegalStateException("样本不足：关系 " + relationshipId + " 只有 " + others.size()
                    + " 条对方消息，至少需要 " + MIN_DB_MESSAGES + " 条");
        }

        StringBuilder sb = new StringBuilder();
        LocalDateTime first = null, last = null;
        for (ChatMessage m : others) {
            if (m.getMessageTime() != null) {
                if (first == null) first = m.getMessageTime();
                last = m.getMessageTime();
            }
            sb.append("[").append(m.getMessageTime() != null ? m.getMessageTime().toLocalDate() : "?")
              .append("] ").append(m.getContent().trim()).append("\n");
        }
        return new Material(sb.toString(), others.size(), first, last, 0,
                "RUI数据库(relId=" + relationshipId + ")");
    }

    Material fromQqFile(String pathStr, String target) throws IOException {
        Path p = Path.of(pathStr);
        if (!Files.isRegularFile(p)) {
            throw new IOException("QQ 导出文件不存在或不是普通文件: " + pathStr);
        }
        var msgs = QqChatParser.parse(p, target);
        if (msgs.isEmpty()) {
            throw new IOException("QQ 解析未提取到目标人物 '" + target + "' 的消息");
        }
        String text = QqChatParser.formatAsText(msgs);
        log.info("[EX-MATERIAL] QQ parsed: {} messages", msgs.size());
        return new Material(text, msgs.size(), null, null, 0,
                "QQ导出(" + p.getFileName() + ")");
    }

    /** 通用 Python 解析器调用。 */
    Material runPythonParser(String script, String[] extraArgs, String label) throws IOException, InterruptedException {
        String pythonBin = ExSlugGenerator.detectPython();
        if (pythonBin == null) {
            throw new IOException("未检测到 Python（python/py/python3 均不可用），无法解析" + label);
        }
        Path toolsDir = Files.createTempDirectory("rui-exskill-");
        Path parser = toolsDir.resolve(script);
        try (InputStream in = getClass().getResourceAsStream("/exskill/tools/" + script)) {
            if (in == null) throw new IOException(script + " 不在 resources 中");
            Files.copy(in, parser, StandardCopyOption.REPLACE_EXISTING);
        }
        Path outFile = toolsDir.resolve("out.txt");

        List<String> cmd = new ArrayList<>();
        cmd.add(pythonBin);
        cmd.add(parser.toString());
        Collections.addAll(cmd, extraArgs);
        cmd.add("--output");
        cmd.add(outFile.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        log.info("[EX-MATERIAL] running {}: {}", script, String.join(" ", cmd));
        Process p = pb.start();
        String stdout;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            stdout = r.lines().collect(Collectors.joining("\n"));
        }
        boolean finished = p.waitFor(PY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new IOException(script + " 超时（" + PY_TIMEOUT_SECONDS + "s）");
        }
        if (p.exitValue() != 0) {
            throw new IOException(script + " 退出码=" + p.exitValue() + " output=" + stdout);
        }
        if (!Files.exists(outFile)) {
            throw new IOException(script + " 未生成输出文件。stdout=" + stdout);
        }
        String text = Files.readString(outFile, StandardCharsets.UTF_8);
        int lines = (int) text.lines().filter(l -> !l.isBlank()).count();
        cleanup(toolsDir);
        log.info("[EX-MATERIAL] {} parsed: {} lines, {} chars", label, lines, text.length());
        return new Material(text, lines, null, null, 0, label);
    }

    /** 兼容旧方法签名。 */
    public Material fromWechatExport(Path exportFile, String targetName) throws Exception {
        return runPythonParser("wechat_parser.py",
                new String[]{"--file", exportFile.toAbsolutePath().toString(), "--target", targetName},
                "微信导出");
    }

    private String truncate(String text) {
        if (text.length() <= MAX_TOTAL_CHARS) return text;
        return text.substring(0, MAX_TOTAL_CHARS) + "\n...(截断)";
    }

    private void cleanup(Path dir) {
        try {
            Files.walk(dir).sorted(Collections.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }
}
