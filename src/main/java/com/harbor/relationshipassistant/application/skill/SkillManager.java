package com.harbor.relationshipassistant.application.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skill 生命周期协调中心。
 * 协调 SkillRuntimeState / DebounceScheduler / SkillLoader / SkillValidator / SkillRegistry。
 */
public final class SkillManager {
    private static final Logger log = LoggerFactory.getLogger(SkillManager.class);

    private final Path skillsRoot;
    private final SkillLoader loader;
    private final SkillValidator validator = new SkillValidator();
    private final SkillRegistry registry = new SkillRegistry();
    private final DebounceScheduler debounce = new DebounceScheduler();
    private final Map<Path, SkillRuntimeState> runtimeStates = new ConcurrentHashMap<>();

    public SkillManager(Path skillsRoot, SkillLoader loader) {
        this.skillsRoot = Objects.requireNonNull(skillsRoot, "skillsRoot");
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    public SkillRegistry getRegistry() { return registry; }

    public void initialize() {
        log.info("[SKILL-MANAGER] initialize skillsRoot={}", skillsRoot);
        if (!Files.isDirectory(skillsRoot)) {
            log.warn("[SKILL-MANAGER] skillsRoot not a directory: {}", skillsRoot);
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(skillsRoot, Files::isDirectory)) {
            for (Path child : stream) {
                try {
                    SkillRuntimeState state = runtimeStates.computeIfAbsent(child.toAbsolutePath().normalize(),
                            SkillRuntimeState::new);
                    long gen = state.nextGeneration();
                    loadForGeneration(child.toAbsolutePath().normalize(), gen);
                } catch (Throwable t) {
                    log.error("[SKILL-MANAGER] initialize failed for {}", child, t);
                }
            }
        } catch (IOException e) {
            log.error("[SKILL-MANAGER] initialize scan failed", e);
        }
        log.info("[SKILL-MANAGER] initialize done, registry size={}", registry.size());
    }

    public void onSkillChanged(Path skillDirectory) {
        Path normalized = skillDirectory.toAbsolutePath().normalize();
        log.info("[SKILL-MANAGER] onSkillChanged path={}", normalized);
        SkillRuntimeState state = runtimeStates.computeIfAbsent(normalized, SkillRuntimeState::new);
        long gen = state.nextGeneration();
        state.setState(SkillLifecycleState.LOADING);
        state.clearError();
        debounce.schedule(normalized, () -> loadForGeneration(normalized, gen));
    }

    public void onSkillDeleted(Path skillDirectory) {
        Path normalized = skillDirectory.toAbsolutePath().normalize();
        log.info("[SKILL-MANAGER] onSkillDeleted path={}", normalized);
        SkillRuntimeState state = runtimeStates.get(normalized);
        if (state != null) {
            state.nextGeneration();
            state.setState(SkillLifecycleState.REMOVED);
        }
        registry.unregister(normalized);
        debounce.cancel(normalized);
    }

    private void loadForGeneration(Path skillDirectory, long taskGeneration) {
        SkillRuntimeState state = runtimeStates.get(skillDirectory);
        if (state == null) {
            log.warn("[SKILL-LOAD] no runtime state for {}", skillDirectory);
            return;
        }
        if (!state.isCurrentGeneration(taskGeneration)) {
            log.info("[SKILL-GENERATION] stale task ignored path={} task={} current={}",
                    skillDirectory, taskGeneration, state.getGeneration());
            return;
        }
        log.info("[SKILL-LOAD] start path={} generation={}", skillDirectory, taskGeneration);
        state.setState(SkillLifecycleState.LOADING);

        SkillDescriptor descriptor;
        try {
            descriptor = loader.load(skillDirectory);
        } catch (Throwable t) {
            if (!state.isCurrentGeneration(taskGeneration)) {
                log.info("[SKILL-GENERATION] stale task failure ignored path={} task={} current={}",
                        skillDirectory, taskGeneration, state.getGeneration());
                return;
            }
            log.warn("[SKILL-VALIDATE] failed path={} reason={}", skillDirectory, t.toString());
            state.setError(t.getMessage() == null ? t.toString() : t.getMessage());
            state.setState(SkillLifecycleState.INVALID);
            return;
        }

        if (!state.isCurrentGeneration(taskGeneration)) {
            log.info("[SKILL-GENERATION] stale task ignored after load path={} task={} current={}",
                    skillDirectory, taskGeneration, state.getGeneration());
            return;
        }

        ValidationResult vr = validator.validate(descriptor);
        if (!vr.isValid()) {
            log.warn("[SKILL-VALIDATE] failed name={} reason={}", descriptor.getName(), vr.firstErrorMessage());
            state.setError(vr.firstErrorMessage());
            state.setState(SkillLifecycleState.INVALID);
            return;
        }

        // 重复 name 冲突：旧 Skill 保持不变，新 Skill 不进入 Registry
        SkillDescriptor existing = registry.getByName(descriptor.getName());
        if (existing != null && !existing.getSkillDirectory().equals(descriptor.getSkillDirectory())) {
            log.warn("[SKILL-REGISTRY] conflict name={} existingPath={} newPath={}",
                    descriptor.getName(), existing.getSkillDirectory(), descriptor.getSkillDirectory());
            state.setError("Duplicate name conflict with " + existing.getSkillDirectory());
            state.setState(SkillLifecycleState.INVALID);
            return;
        }

        registry.replace(descriptor);
        state.clearError();
        state.setState(SkillLifecycleState.READY);
        log.info("[SKILL-VALIDATE] success name={}", descriptor.getName());
    }

    public SkillRuntimeState getRuntimeState(Path skillDirectory) {
        return runtimeStates.get(skillDirectory.toAbsolutePath().normalize());
    }

    /** 当前所有 READY Skill 的不可修改快照。 */
    public List<SkillDescriptor> getReadySkills() {
        List<SkillDescriptor> all = registry.getAll();
        log.info("[SKILL-REGISTRY] getAll size={}", all.size());
        return all;
    }

    public Optional<SkillDescriptor> getSkill(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        SkillDescriptor d = registry.getByName(name);
        log.info("[SKILL-REGISTRY] get name={} found={}", name, d != null);
        return Optional.ofNullable(d);
    }

    public Optional<SkillDescriptor> findSkill(Path path) {
        if (path == null) return Optional.empty();
        return Optional.ofNullable(registry.getByPath(path));
    }

    public boolean hasSkill(String name) {
        return name != null && registry.containsName(name);
    }

    public void shutdown() {
        debounce.shutdown();
        log.info("[SKILL-MANAGER] shutdown");
    }
}
