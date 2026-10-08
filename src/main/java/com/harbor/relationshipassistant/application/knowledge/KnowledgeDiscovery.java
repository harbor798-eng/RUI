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

/**
 * Knowledge Discovery: scan a collection source directory and return document metadata.
 * Does NOT load full content. Does NOT do retrieval.
 */
public final class KnowledgeDiscovery {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeDiscovery.class);

    public record DiscoveredDocument(String id, String title, String source, String collectionId) {}

    private KnowledgeDiscovery() {}

    public static List<DiscoveredDocument> discover(String collectionId, String displayName, Path sourceDir) {
        List<DiscoveredDocument> out = new ArrayList<>();
        if (sourceDir == null || !Files.isDirectory(sourceDir)) {
            log.warn("[KNOWLEDGE-DISCOVERY] dir not found: {}", sourceDir);
            return out;
        }
        try (var files = Files.list(sourceDir)) {
            files.filter(Files::isRegularFile)
                 .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                 .sorted()
                 .forEach(p -> out.add(toDoc(collectionId, p)));
        } catch (IOException e) {
            log.warn("[KNOWLEDGE-DISCOVERY] failed to list {}: {}", sourceDir, e.toString());
        }
        log.info("[KNOWLEDGE-DISCOVERY] collection={} discovered={} from {}", collectionId, out.size(), sourceDir);
        return out;
    }

    private static DiscoveredDocument toDoc(String collectionId, Path p) {
        String fname = p.getFileName().toString();
        String id = fname.replaceAll("\\.md$", "");
        String title = extractTitle(p, id);
        return new DiscoveredDocument(id, title, fname, collectionId);
    }

    private static String extractTitle(Path p, String fallback) {
        try {
            List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
            for (String line : lines) {
                String t = line.trim();
                if (t.startsWith("# ")) return t.substring(2).trim();
                if (!t.isEmpty() && !t.startsWith("---") && !t.startsWith("name:") && !t.startsWith("description:")) {
                    // first non-frontmatter line as fallback
                    return t;
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return fallback;
    }
}
