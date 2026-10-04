package com.harbor.relationshipassistant.application.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 保存当前所有 READY 状态的 SkillDescriptor。
 * 同时维护 name -> descriptor 和 path -> descriptor 两个索引；
 * 复合操作由 private lock 保护。
 */
public final class SkillRegistry {
    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);

    private final Object lock = new Object();
    private final Map<String, SkillDescriptor> byName = new LinkedHashMap<>();
    private final Map<Path, SkillDescriptor> byPath = new LinkedHashMap<>();

    private static Path norm(Path p) {
        return p.toAbsolutePath().normalize();
    }

    public void replace(SkillDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        synchronized (lock) {
            SkillDescriptor oldByPath = byPath.get(descriptor.getSkillDirectory());
            if (oldByPath != null && !oldByPath.getName().equals(descriptor.getName())) {
                byName.remove(oldByPath.getName());
                log.info("[SKILL-REGISTRY] removed old name entry={} path={}", oldByPath.getName(), descriptor.getSkillDirectory());
            }
            SkillDescriptor oldByName = byName.get(descriptor.getName());
            if (oldByName != null && !oldByName.getSkillDirectory().equals(descriptor.getSkillDirectory())) {
                byPath.remove(oldByName.getSkillDirectory());
                log.info("[SKILL-REGISTRY] removed old path entry name={} oldPath={}", descriptor.getName(), oldByName.getSkillDirectory());
            }
            byName.put(descriptor.getName(), descriptor);
            byPath.put(descriptor.getSkillDirectory(), descriptor);
            log.info("[SKILL-REGISTRY] replace name={} path={}", descriptor.getName(), descriptor.getSkillDirectory());
        }
    }

    public void unregister(Path skillDirectory) {
        Objects.requireNonNull(skillDirectory, "skillDirectory");
        synchronized (lock) {
            SkillDescriptor removed = byPath.remove(norm(skillDirectory));
            if (removed != null) {
                byName.remove(removed.getName());
                log.info("[SKILL-REGISTRY] unregister name={} path={}", removed.getName(), skillDirectory);
            }
        }
    }

    public SkillDescriptor getByName(String name) {
        if (name == null) return null;
        synchronized (lock) {
            return byName.get(name);
        }
    }

    public SkillDescriptor getByPath(Path path) {
        if (path == null) return null;
        synchronized (lock) {
            return byPath.get(norm(path));
        }
    }

    /** 返回不可修改快照。 */
    public List<SkillDescriptor> getAll() {
        synchronized (lock) {
            return List.copyOf(byName.values());
        }
    }

    public boolean containsName(String name) {
        if (name == null) return false;
        synchronized (lock) {
            return byName.containsKey(name);
        }
    }

    public boolean containsPath(Path path) {
        if (path == null) return false;
        synchronized (lock) {
            return byPath.containsKey(norm(path));
        }
    }

    public int size() {
        synchronized (lock) {
            return byName.size();
        }
    }
}
