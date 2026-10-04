package com.harbor.relationshipassistant.application.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 单个 Skill 在 JEVE 中的运行时管理状态。
 * <p>
 * 与 {@link SkillDescriptor} 分离：
 * <ul>
 *   <li>SkillDescriptor 代表 Skill 内容本身，不可变；</li>
 *   <li>SkillRuntimeState 代表 JEVE 当前如何管理这个 Skill（生命周期、generation、错误信息）。</li>
 * </ul>
 * </p>
 */
public final class SkillRuntimeState {
    private static final Logger log = LoggerFactory.getLogger(SkillRuntimeState.class);

    private final Path skillDirectory;
    private final AtomicReference<SkillLifecycleState> state =
            new AtomicReference<>(SkillLifecycleState.DISCOVERED);
    private final AtomicLong generation = new AtomicLong(0);
    private final AtomicReference<String> errorMessage = new AtomicReference<>();

    public SkillRuntimeState(Path skillDirectory) {
        this.skillDirectory = Objects.requireNonNull(skillDirectory, "skillDirectory");
    }

    public Path getSkillDirectory() { return skillDirectory; }

    public SkillLifecycleState getState() { return state.get(); }

    public void setState(SkillLifecycleState newState) {
        SkillLifecycleState old = state.getAndSet(newState);
        log.info("[SKILL-MANAGER] state transition path={} {} -> {}", skillDirectory, old, newState);
    }

    public long getGeneration() { return generation.get(); }

    public long nextGeneration() {
        long g = generation.incrementAndGet();
        log.info("[SKILL-GENERATION] path={} generation={}", skillDirectory, g);
        return g;
    }

    public boolean isCurrentGeneration(long taskGeneration) {
        return taskGeneration == generation.get();
    }

    public String getErrorMessage() { return errorMessage.get(); }

    public void setError(String message) {
        errorMessage.set(message);
        log.warn("[SKILL-MANAGER] error path={} message={}", skillDirectory, message);
    }

    public void clearError() {
        errorMessage.set(null);
    }

    @Override
    public String toString() {
        return "SkillRuntimeState{dir=" + skillDirectory + ", state=" + state.get() + ", gen=" + generation.get() + "}";
    }
}
