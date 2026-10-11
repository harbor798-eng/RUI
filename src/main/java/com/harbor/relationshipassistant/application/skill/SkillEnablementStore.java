package com.harbor.relationshipassistant.application.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skill 启用/停用状态持久化。
 *
 * 存储位置：{skillsRoot}/.enabled.json
 * 格式：{ "skill-name": true, "another-skill": false }
 *
 * - 未记录的 Skill 默认启用（true）。
 * - 状态绑定 skill name（稳定标识），不依赖 UI 顺序。
 * - SkillWatcher 热加载时不会触碰此文件，停用状态保留。
 * - 文件损坏时退化为全启用并记录警告，不阻塞启动。
 */
public final class SkillEnablementStore {
    private static final Logger log = LoggerFactory.getLogger(SkillEnablementStore.class);
    private static final String FILE_NAME = ".enabled.json";

    private final Path filePath;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final Map<String, Boolean> state = new ConcurrentHashMap<>();

    public SkillEnablementStore(Path skillsRoot) {
        this.filePath = skillsRoot.resolve(FILE_NAME);
        load();
    }

    private void load() {
        if (!Files.exists(filePath)) {
            log.info("[SKILL-ENABLE] no existing state file at {}", filePath);
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            if (bytes.length == 0) return;
            @SuppressWarnings("unchecked")
            Map<String, Boolean> raw = mapper.readValue(bytes, HashMap.class);
            state.putAll(raw);
            log.info("[SKILL-ENABLE] loaded {} entries from {}", state.size(), filePath);
        } catch (IOException e) {
            log.warn("[SKILL-ENABLE] failed to load {}: {}", filePath, e.toString());
        }
    }

    private synchronized void save() {
        try {
            if (filePath.getParent() != null) Files.createDirectories(filePath.getParent());
            Path tmp = filePath.resolveSibling(FILE_NAME + ".tmp");
            mapper.writeValue(tmp.toFile(), new HashMap<>(state));
            Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("[SKILL-ENABLE] failed to save {}: {}", filePath, e.toString());
        }
    }

    /** 默认 true。 */
    public boolean isEnabled(String skillName) {
        if (skillName == null || skillName.isBlank()) return true;
        Boolean v = state.get(skillName);
        return v == null || v;
    }

    public void setEnabled(String skillName, boolean enabled) {
        if (skillName == null || skillName.isBlank()) return;
        state.put(skillName, enabled);
        save();
        log.info("[SKILL-ENABLE] set {} = {}", skillName, enabled);
    }
}
