package com.harbor.relationshipassistant.application.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 监听 skills/ 根目录以及每个直接 Skill 子目录的文件变化。
 * <p>
 * 职责边界：
 * <ul>
 *   <li>只发现变化并通知 {@link SkillManager}，不加载、不解析、不改 Registry；</li>
 *   <li>所有加载前的去抖交给 {@link SkillManager#onSkillChanged(Path)} 内已有的 DebounceScheduler；</li>
 *   <li>稳定性检查在 Watcher 线程内完成，稳定后才回调 onSkillChanged。</li>
 * </ul>
 * </p>
 */
public final class SkillWatcher {
    private static final Logger log = LoggerFactory.getLogger(SkillWatcher.class);

    private final Path skillsRoot;
    private final SkillManager skillManager;
    private final SkillStabilityChecker stabilityChecker = new SkillStabilityChecker();
    private final Map<WatchKey, Path> keyToDir = new ConcurrentHashMap<>();
    private final Map<Path, WatchKey> dirToKey = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private WatchService watchService;
    private Thread watcherThread;
    private ExecutorService stabilityExecutor;

    public SkillWatcher(Path skillsRoot, SkillManager skillManager) {
        this.skillsRoot = skillsRoot.toAbsolutePath().normalize();
        this.skillManager = skillManager;
    }

    public void start() throws IOException {
        if (running.get()) return;
        Files.createDirectories(skillsRoot);
        watchService = skillsRoot.getFileSystem().newWatchService();
        stabilityExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "skill-stability");
            t.setDaemon(true);
            return t;
        });

        registerDirectory(skillsRoot);
        registerExistingSkillDirs();

        running.set(true);
        watcherThread = new Thread(this::watchLoop, "skill-watcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
        log.info("[SKILL-WATCH] started root={}", skillsRoot);
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        log.info("[SKILL-WATCH] stopping");
        try {
            if (watchService != null) watchService.close();
        } catch (IOException e) {
            log.warn("[SKILL-WATCH] close watchService error: {}", e.toString());
        }
        if (watcherThread != null) {
            watcherThread.interrupt();
            try { watcherThread.join(500); } catch (InterruptedException ignore) { Thread.currentThread().interrupt(); }
        }
        if (stabilityExecutor != null) stabilityExecutor.shutdownNow();
        keyToDir.clear();
        dirToKey.clear();
        log.info("[SKILL-WATCH] stopped");
    }

    private void registerExistingSkillDirs() throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(skillsRoot, Files::isDirectory)) {
            for (Path child : stream) {
                if (isDirectSkillDirectory(child)) {
                    registerDirectory(child);
                }
            }
        }
    }

    private void registerDirectory(Path dir) throws IOException {
        WatchKey key = dir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        keyToDir.put(key, dir);
        dirToKey.put(dir, key);
        log.info("[SKILL-WATCH] registered directory={}", dir);
    }

    private boolean isDirectSkillDirectory(Path path) {
        Path parent = path.toAbsolutePath().normalize().getParent();
        return parent != null && parent.equals(skillsRoot) && Files.isDirectory(path);
    }

    private void watchLoop() {
        while (running.get()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception closed) {
                if (running.get()) log.warn("[SKILL-WATCH] watchService take error: {}", closed.toString());
                break;
            }
            Path watchDir = keyToDir.get(key);
            if (watchDir == null) {
                key.reset();
                continue;
            }
            try {
                for (WatchEvent<?> raw : key.pollEvents()) {
                    handleEvent(watchDir, raw);
                }
            } catch (Throwable t) {
                log.error("[SKILL-WATCH] event loop error dir={}", watchDir, t);
            } finally {
                boolean valid = key.reset();
                if (!valid) {
                    log.info("[SKILL-WATCH] watch key invalid directory={}", watchDir);
                    keyToDir.remove(key);
                    dirToKey.remove(watchDir);
                    if (Files.isDirectory(watchDir)) {
                        try {
                            registerDirectory(watchDir);
                        } catch (IOException e) {
                            log.warn("[SKILL-WATCH] re-register failed dir={}", watchDir, e);
                        }
                    } else if (!watchDir.equals(skillsRoot)) {
                        // 目录被删除导致 key 失效：兜底通知删除
                        log.info("[SKILL-WATCH] skill directory removed (key invalid) path={}", watchDir);
                        skillManager.onSkillDeleted(watchDir);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleEvent(Path watchDir, WatchEvent<?> raw) {
        WatchEvent.Kind<?> kind = raw.kind();
        if (kind == StandardWatchEventKinds.OVERFLOW) return;
        Path relative = ((WatchEvent<Path>) raw).context();
        Path absolute = watchDir.resolve(relative).normalize();

        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
            log.info("[SKILL-WATCH] event kind=ENTRY_CREATE path={}", absolute);
            if (watchDir.equals(skillsRoot) && Files.isDirectory(absolute)) {
                if (isDirectSkillDirectory(absolute)) {
                    try { registerDirectory(absolute); } catch (IOException e) { log.warn("[SKILL-WATCH] register new skill dir failed {}", absolute, e); }
                    scheduleStableChange(absolute);
                }
                // 嵌套 category 目录：不递归、不加载
            } else {
                // Skill 内部文件创建：通知所属 skill 目录
                Path owner = ownerSkillDir(absolute);
                if (owner != null) scheduleStableChange(owner);
            }
        } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
            log.info("[SKILL-WATCH] event kind=ENTRY_MODIFY path={}", absolute);
            Path owner = watchDir.equals(skillsRoot) ? null : ownerSkillDir(absolute);
            if (owner != null) {
                scheduleStableChange(owner);
            } else if (watchDir.equals(skillsRoot) && Files.isDirectory(absolute)) {
                // root 上直接目录的 MODIFY 一般不出现；忽略
            }
        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            log.info("[SKILL-WATCH] event kind=ENTRY_DELETE path={}", absolute);
            if (watchDir.equals(skillsRoot)) {
                // 直接子项被删：如果之前注册过（或曾经是 skill 目录）
                boolean known = dirToKey.containsKey(absolute);
                boolean directChild = absolute.getParent() != null && absolute.getParent().equals(skillsRoot);
                if (known || directChild) {
                    WatchKey removed = dirToKey.remove(absolute);
                    if (removed != null) keyToDir.remove(removed);
                    log.info("[SKILL-WATCH] skill directory removed path={}", absolute);
                    skillManager.onSkillDeleted(absolute);
                }
            } else {
                // Skill 内部文件删除：SKILL.md 没了也走 changed（稳定性检查会发现 SKILL.md 缺失）
                Path owner = ownerSkillDir(absolute);
                if (owner != null) scheduleStableChange(owner);
            }
        }
    }

    /** 找到 absolute 所属的直接 Skill 目录（必须 parent == skillsRoot）。 */
    private Path ownerSkillDir(Path absolute) {
        Path normalized = absolute.toAbsolutePath().normalize();
        // absolute 可能在 skillsRoot/<skill>/... 下
        if (normalized.getParent() == null) return null;
        if (normalized.getParent().equals(skillsRoot)) {
            // 直接位于 skills/ 下的文件事件（来自 skill 子目录的 watch 不会到这里，因为 watchDir 是 skill 本身）
            return null;
        }
        Path candidate = normalized.getParent();
        while (candidate != null && !candidate.equals(skillsRoot)) {
            if (candidate.getParent() != null && candidate.getParent().equals(skillsRoot)) {
                return Files.isDirectory(candidate) ? candidate : null;
            }
            candidate = candidate.getParent();
        }
        return null;
    }

    /**
     * 在独立线程等待稳定，稳定后才回调 onSkillChanged。
     * 不稳定也不抛错，不标记 INVALID；下一次文件事件会再触发。
     */
    private void scheduleStableChange(Path skillDirectory) {
        stabilityExecutor.submit(() -> {
            try {
                boolean stable = stabilityChecker.awaitStable(skillDirectory);
                if (!stable) {
                    log.info("[SKILL-WATCH] not stable yet, waiting for next event path={}", skillDirectory);
                    return;
                }
                skillManager.onSkillChanged(skillDirectory);
            } catch (Throwable t) {
                log.error("[SKILL-WATCH] stability/onSkillChanged error path={}", skillDirectory, t);
            }
        });
    }
}
