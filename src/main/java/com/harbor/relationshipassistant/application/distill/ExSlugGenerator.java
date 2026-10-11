package com.harbor.relationshipassistant.application.distill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 中文昵称 → slug。
 * 优先尝试系统 Python + pypinyin；失败时 fallback 到 ex-{relationshipId}。
 */
public final class ExSlugGenerator {
    private static final Logger log = LoggerFactory.getLogger(ExSlugGenerator.class);

    private ExSlugGenerator() {}

    public static String generate(String nickname, long relationshipId) {
        if (nickname == null || nickname.isBlank()) {
            return "ex-" + relationshipId;
        }
        String trimmed = nickname.trim();

        // 纯 ASCII：直接转小写，空格/特殊字符转 -
        if (trimmed.matches("^[\\x20-\\x7E]+$")) {
            return slugify(trimmed);
        }

        // 中文/混合：尝试 pypinyin
        String pinyin = tryPinyin(trimmed);
        if (pinyin != null && !pinyin.isBlank()) {
            return slugify(pinyin);
        }
        log.warn("[EX-SLUG] pypinyin unavailable, fallback to ex-{}", relationshipId);
        return "ex-" + relationshipId;
    }

    private static String tryPinyin(String chinese) {
        String pythonBin = detectPython();
        if (pythonBin == null) return null;
        String script = "from pypinyin import lazy_pinyin; import sys; print(' '.join(lazy_pinyin(sys.argv[1])))";
        try {
            ProcessBuilder pb = new ProcessBuilder(pythonBin, "-c", script, chinese);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                out = r.lines().collect(Collectors.joining("\n"));
            }
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            if (p.exitValue() != 0) {
                log.warn("[EX-SLUG] pypinyin exit={} out={}", p.exitValue(), out);
                return null;
            }
            return out.trim();
        } catch (Exception e) {
            log.warn("[EX-SLUG] pypinyin call failed: {}", e.getMessage());
            return null;
        }
    }

    static String detectPython() {
        for (String bin : new String[]{"python", "py", "python3"}) {
            try {
                ProcessBuilder pb = new ProcessBuilder(bin, "--version");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    return bin;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static String slugify(String s) {
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
