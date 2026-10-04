package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.domain.analysis.knowledge.KnowledgeDefinition;
import com.harbor.relationshipassistant.domain.analysis.knowledge.KnowledgeLoaderException;
import com.harbor.relationshipassistant.domain.analysis.knowledge.KnowledgeRegistry;
import com.harbor.relationshipassistant.domain.analysis.knowledge.KnowledgeSelection;
import com.harbor.relationshipassistant.domain.analysis.knowledge.LoadedKnowledge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 按 KnowledgeSelection 加载对应知识正文（UTF-8 Markdown 原文）。
 * <p>
 * 不自己选择知识、不缓存、不调用 LLM、不读全部 20 个 MD。
 * 路径必须 resolve 到知识根目录内部，防止 {@code ../} 穿越。
 * </p>
 */
public class KnowledgeLoader {

    private static final long MAX_FILE_BYTES = 512L * 1024; // 512KB

    private final KnowledgeRegistry registry;
    private final Path knowledgeRoot;

    public KnowledgeLoader(KnowledgeRegistry registry) {
        this(registry, Path.of("skills", "goutoujunshi", "knowledge").toAbsolutePath().normalize());
    }

    public KnowledgeLoader(KnowledgeRegistry registry, Path knowledgeRoot) {
        this.registry = registry;
        this.knowledgeRoot = knowledgeRoot.normalize().toAbsolutePath();
    }

    public LoadedKnowledge load(KnowledgeSelection selection) {
        System.out.println("[KnowledgeLoader] start loading");
        if (selection == null) throw new IllegalArgumentException("selection must not be null");
        String id = selection.getKnowledgeId();
        if (id == null || id.isBlank()) throw new IllegalArgumentException("knowledgeId must not be blank");

        KnowledgeDefinition def = registry.get(id);
        if (def == null) {
            throw new KnowledgeLoaderException("Unknown knowledgeId: " + id);
        }

        Path resolved = knowledgeRoot.resolve(def.getFile()).normalize().toAbsolutePath();
        if (!resolved.startsWith(knowledgeRoot)) {
            System.out.println("[KnowledgeLoader][SECURITY] rejected path: knowledgeId=" + id
                    + ", resolved=" + resolved);
            throw new KnowledgeLoaderException("Path escapes knowledge root: id=" + id + ", file=" + def.getFile());
        }
        if (!Files.exists(resolved)) {
            System.out.println("[KnowledgeLoader][ERROR] file not found: knowledgeId=" + id
                    + ", file=" + resolved);
            throw new KnowledgeLoaderException("Knowledge file not found: id=" + id + ", path=" + resolved);
        }
        try {
            long size = Files.size(resolved);
            if (size > MAX_FILE_BYTES) {
                throw new KnowledgeLoaderException("Knowledge file too large: id=" + id + ", size=" + size);
            }
            String content = Files.readString(resolved, StandardCharsets.UTF_8);
            if (content.isEmpty()) {
                System.out.println("[KnowledgeLoader] WARN empty file: knowledgeId=" + id);
            }
            System.out.println("[KnowledgeLoader] loaded: knowledgeId=" + id
                    + ", file=" + def.getFile() + ", contentLength=" + content.length());
            return new LoadedKnowledge(def, content);
        } catch (IOException e) {
            throw new KnowledgeLoaderException("Failed to read knowledge id=" + id + ": " + e.getMessage(), e);
        }
    }

    public List<LoadedKnowledge> loadAll(List<KnowledgeSelection> selections) {
        if (selections == null || selections.isEmpty()) return new ArrayList<>();
        List<LoadedKnowledge> out = new ArrayList<>(selections.size());
        for (KnowledgeSelection s : selections) out.add(load(s));
        return out;
    }
}
