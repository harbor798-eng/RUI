package com.harbor.relationshipassistant.application.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * 把 skills/goutoujunshi/knowledge/*.md 读成 KnowledgeItem 列表。
 * 只读文件，不缓存数据库，不修改旧 KnowledgeRegistry。
 */
public final class GoutoujunshiKnowledgeSeeder {
    private static final Logger log = LoggerFactory.getLogger(GoutoujunshiKnowledgeSeeder.class);

    private GoutoujunshiKnowledgeSeeder() {}

    public static List<KnowledgeItem> load(Path knowledgeDir) {
        List<KnowledgeItem> out = new ArrayList<>();
        if (knowledgeDir == null || !Files.isDirectory(knowledgeDir)) {
            log.warn("[GTJ-KNOWLEDGE] dir not found: {}", knowledgeDir);
            return out;
        }
        try (Stream<Path> files = Files.list(knowledgeDir)) {
            files.filter(Files::isRegularFile)
                 .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                 .sorted()
                 .forEach(p -> out.add(toItem(p)));
        } catch (IOException e) {
            log.warn("[GTJ-KNOWLEDGE] failed to list {}: {}", knowledgeDir, e.toString());
        }
        log.info("[GTJ-KNOWLEDGE] seeded {} items from {}", out.size(), knowledgeDir);
        return out;
    }

    private static KnowledgeItem toItem(Path p) {
        String fname = p.getFileName().toString();
        String id = fname.replaceAll("\\.md$", "");
        String title = id;
        String content;
        try {
            content = Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            content = "";
        }
        return new KnowledgeItem(
                id,
                title,
                content,
                "RELATIONSHIP",
                "goutoujunshi",
                p.toAbsolutePath().normalize().toString());
    }
}
