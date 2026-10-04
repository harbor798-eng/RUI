package com.harbor.relationshipassistant.application.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * 文件稳定性检查：判断 Skill 目录是否已经写入完成。
 * V1 只看 SKILL.md 的 size 和 lastModifiedTime 是否连续两次一致。
 * 不做 hash、不读内容。
 */
public final class SkillStabilityChecker {
    private static final Logger log = LoggerFactory.getLogger(SkillStabilityChecker.class);

    private static final long INTERVAL_MS = 100;
    private static final int MAX_CHECKS = 5;

    /**
     * 阻塞等待 SKILL.md 稳定。最多约 INTERVAL_MS * MAX_CHECKS = 500ms。
     * @return true 表示稳定可加载；false 表示超时仍不稳定或 SKILL.md 不存在。
     */
    public boolean awaitStable(Path skillDirectory) {
        Path skillMd = skillDirectory.resolve("SKILL.md");
        Long prevSize = null;
        FileTime prevMtime = null;
        for (int i = 1; i <= MAX_CHECKS; i++) {
            try {
                if (!Files.isDirectory(skillDirectory)) {
                    log.info("[SKILL-WATCH] stability check path={} attempt={} no directory", skillDirectory, i);
                    return false;
                }
                if (!Files.isRegularFile(skillMd)) {
                    log.info("[SKILL-WATCH] stability check path={} attempt={} SKILL.md not found yet", skillDirectory, i);
                    Thread.sleep(INTERVAL_MS);
                    prevSize = null;
                    prevMtime = null;
                    continue;
                }
                long size = Files.size(skillMd);
                FileTime mtime = Files.getLastModifiedTime(skillMd);
                if (prevSize != null && prevSize == size && mtime.equals(prevMtime)) {
                    log.info("[SKILL-WATCH] stability check path={} attempt={} stable=true size={}", skillDirectory, i, size);
                    return true;
                }
                prevSize = size;
                prevMtime = mtime;
                log.info("[SKILL-WATCH] stability check path={} attempt={} stable=false size={}", skillDirectory, i, size);
                Thread.sleep(INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                log.warn("[SKILL-WATCH] stability check error path={} attempt={} reason={}", skillDirectory, i, e.toString());
                return false;
            }
        }
        log.info("[SKILL-WATCH] stability check path={} giving up after {} attempts", skillDirectory, MAX_CHECKS);
        return false;
    }
}
