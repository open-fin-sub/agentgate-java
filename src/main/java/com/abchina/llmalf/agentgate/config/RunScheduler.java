package com.abchina.llmalf.agentgate.config;

import com.abchina.llmalf.agentgate.service.impl.RunReaderService;
import com.abchina.llmalf.agentgate.service.impl.RunSchedulingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 调度扫描任务.
 *
 * <p>对齐 Python 侧调度职责(beat 10s 周期):
 * 超时恢复 → 到期释放派发 → WAITING 派发;BJS 只负责拉起执行进程。</p>
 */
@Slf4j
@Component
public class RunScheduler {

    private final RunReaderService runReaderService;
    private final RunSchedulingService runSchedulingService;
    private final long staleGraceSeconds;
    private final int scanBatchSize;

    public RunScheduler(RunReaderService runReaderService,
            RunSchedulingService runSchedulingService,
            @org.springframework.beans.factory.annotation.Value(
                    "${agentgate.scheduling.stale-grace-seconds:30}") long staleGraceSeconds,
            @org.springframework.beans.factory.annotation.Value(
                    "${agentgate.scheduling.scan-batch-size:100}") int scanBatchSize) {
        this.runReaderService = runReaderService;
        this.runSchedulingService = runSchedulingService;
        this.staleGraceSeconds = staleGraceSeconds;
        this.scanBatchSize = scanBatchSize;
    }

    /**
     * 调度扫描(默认 10s 周期,可配).
     */
    @Scheduled(fixedDelayString = "${agentgate.scheduling.interval-seconds:10}000")
    public void scan() {
        try {
            log.info("start Scheduler scan ...");
            runReaderService.failStaleRuns(staleGraceSeconds);
            int due = runSchedulingService.dispatchDueRuns(scanBatchSize).size();
            int waiting = runSchedulingService.dispatchWaitingRuns(scanBatchSize).size();
            if (due > 0 || waiting > 0) {
                log.info("Scheduler scan: dispatched due={} waiting={}", due, waiting);
            }
        } catch (RuntimeException e) {
            log.error("Scheduler scan failed: {}", e.getClass().getSimpleName(), e);
        }
    }
}
