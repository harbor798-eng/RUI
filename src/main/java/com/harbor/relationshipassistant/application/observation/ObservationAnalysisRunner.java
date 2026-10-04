package com.harbor.relationshipassistant.application.observation;

import com.harbor.relationshipassistant.domain.observation.ObservationBatch;
import com.harbor.relationshipassistant.infrastructure.persistence.ObservationRepository;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 4A：后台执行 Observation 分析。
 * JavaFX 主线程只调用 submit()，不阻塞；网络请求与计算全部在后台线程。
 */
public class ObservationAnalysisRunner {

    private static final Logger log = LoggerFactory.getLogger(ObservationAnalysisRunner.class);
    private static final Set<Long> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private final ObservationRepository repo;
    private final ObservationService service;

    public ObservationAnalysisRunner(ObservationRepository repo, ObservationService service) {
        this.repo = repo;
        this.service = service;
    }

    /** 提交一个 Batch 到后台。返回 false 表示拒绝重复/终态启动。 */
    public boolean submit(long batchId) {
        ObservationBatch b = repo.getBatch(batchId);
        String status = b.getStatus();
        if (IN_FLIGHT.contains(batchId)) {
            log.warn("[OBS_RUNNER_REJECT] batchId={} reason=ALREADY_IN_FLIGHT", batchId);
            return false;
        }
        if (!"RUNNING".equals(status)) {
            log.warn("[OBS_RUNNER_REJECT] batchId={} reason=status={}", batchId, status);
            return false;
        }
        IN_FLIGHT.add(batchId);

        Thread t = new Thread(() -> {
            long t0 = System.currentTimeMillis();
            try {
                log.info("[OBS_RUNNER_START] batchId={} relationshipId={}", batchId, b.getRelationshipId());
                log.info("[OBS_RUNNER_CONTEXT] batchId={} chatSnapshotRows={}", batchId, b.getSnapshotChatCount());
                log.info("[OBS_RUNNER_AI_START] batchId={}", batchId);
                service.runAnalysis(batchId);
                log.info("[OBS_RUNNER_AI_DONE] batchId={} elapsedMs={}", batchId, System.currentTimeMillis() - t0);
                log.info("[OBS_RUNNER_BATCH_DONE] batchId={}", batchId);
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - t0;
                String code = e.getClass().getSimpleName();
                String msg = e.getMessage() == null ? "unknown" : e.getMessage();
                repo.markFailed(batchId, code, msg);
                log.error("[OBS_RUNNER_FAILED] batchId={} elapsedMs={} errorType={}", batchId, elapsed, code, e);
            } finally {
                IN_FLIGHT.remove(batchId);
            }
        }, "obs-runner-" + batchId);
        t.setDaemon(true);
        t.start();
        return true;
    }
}
