package com.harbor.relationshipassistant.application.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 对同一个 Path 的连续事件进行去抖。
 * <p>
 * 负责"合并重复事件"；与 Generation（让过期任务失效）分工明确：
 * <ul>
 *   <li>Debounce 只取消尚未开始执行的任务；</li>
 *   <li>任务已经开始执行后再发生变化，由 Generation 机制丢弃旧结果。</li>
 * </ul>
 * </p>
 */
public final class DebounceScheduler {
    private static final Logger log = LoggerFactory.getLogger(DebounceScheduler.class);

    public static final long DEFAULT_DELAY_MS = 300;

    private final long delayMs;
    private final ScheduledExecutorService executor;
    private final Map<Path, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    public DebounceScheduler() {
        this(DEFAULT_DELAY_MS);
    }

    public DebounceScheduler(long delayMs) {
        this.delayMs = delayMs;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "skill-debounce");
            t.setDaemon(true);
            return t;
        });
        log.info("[SKILL-DEBOUNCE] initialized delayMs={}", delayMs);
    }

    /**
     * 调度一个任务。如果同 key 已有未执行任务，取消后重新计时。
     */
    public void schedule(Path key, Runnable task) {
        ScheduledFuture<?> old = pending.remove(key);
        if (old != null) {
            boolean cancelled = old.cancel(false);
            log.debug("[SKILL-DEBOUNCE] cancelled previous pending path={} cancelled={}", key, cancelled);
        }
        final ScheduledFuture<?>[] holder = new ScheduledFuture<?>[1];
        holder[0] = executor.schedule(() -> {
            try {
                pending.remove(key, holder[0]);
                log.info("[SKILL-DEBOUNCE] firing path={}", key);
                task.run();
            } catch (Throwable t) {
                log.error("[SKILL-DEBOUNCE] task failed path={}", key, t);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
        pending.put(key, holder[0]);
        log.debug("[SKILL-DEBOUNCE] schedule path={} delayMs={}", key, delayMs);
    }

    /** 取消尚未执行的任务。 */
    public void cancel(Path key) {
        ScheduledFuture<?> f = pending.remove(key);
        if (f != null) {
            f.cancel(false);
            log.info("[SKILL-DEBOUNCE] cancel path={}", key);
        }
    }

    public void shutdown() {
        pending.clear();
        executor.shutdownNow();
        log.info("[SKILL-DEBOUNCE] shutdown");
    }
}
