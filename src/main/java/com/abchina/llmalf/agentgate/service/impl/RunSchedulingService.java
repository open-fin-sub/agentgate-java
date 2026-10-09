package com.abchina.llmalf.agentgate.service.impl;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun;
import com.abchina.llmalf.agentgate.domain.model.run.RunStatus;
import com.abchina.llmalf.agentgate.integration.BjsJobDispatcher;
import com.abchina.llmalf.agentgate.logic.RunLogic;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Run 调度编排.
 *
 * <p>对齐 Python application/run_scheduling.py:
 * 到期释放 + WAITING 派发 + 每 API Key 并发限流
 * + 派发失败重试(attempts < MAX 转 WAITING,耗尽转 FAILED)。</p>
 */
@Slf4j
@Service
public class RunSchedulingService {

    private final RunLogic runLogic;
    private final BjsJobDispatcher dispatcher;
    private final int maxConcurrentPerApiKey;
    private final int maxDispatchAttempts;

    public RunSchedulingService(RunLogic runLogic, BjsJobDispatcher dispatcher,
            @Value("${agentgate.scheduling.max-concurrent-per-api-key:10}")
            int maxConcurrentPerApiKey,
            @Value("${agentgate.scheduling.max-dispatch-attempts:10}")
            int maxDispatchAttempts) {
        this.runLogic = runLogic;
        this.dispatcher = dispatcher;
        this.maxConcurrentPerApiKey = maxConcurrentPerApiKey;
        this.maxDispatchAttempts = maxDispatchAttempts;
    }

    /**
     * 释放并派发到期的 SCHEDULED Run.
     *
     * @param limit 批量上限
     * @return 派发成功的 Run 列表
     */
    public List<EvaluationRun> dispatchDueRuns(int limit) {
        List<EvaluationRun> dueRuns = runLogic.claimDueScheduledRuns(
                DomainValidations.utcNow(), limit);
        List<EvaluationRun> dispatched = new ArrayList<>();
        for (EvaluationRun run : dueRuns) {
            int active = runLogic.countActiveRunsByApiKey(run.apiKey());
            if (active > maxConcurrentPerApiKey) {
                EvaluationRun waiting = transitionToWaiting(run, false);
                runLogic.saveRun(waiting);
                log.warn("Scheduled run throttled to waiting: run_id={} active_count={} "
                        + "max={} api_key={}", run.id(), active, maxConcurrentPerApiKey,
                        run.apiKey() == null ? "None" : "***");
                continue;
            }
            try {
                dispatcher.submit(run.id());
            } catch (RuntimeException e) {
                handleDispatchFailure(run, e, DomainValidations.utcNow());
                continue;
            }
            log.info("Scheduled run dispatched: run_id={}", run.id());
            dispatched.add(run);
        }
        return dispatched;
    }

    /**
     * 派发 WAITING Run(并发槽位可用时).
     *
     * @param limit 批量上限
     * @return 派发成功的 Run 列表
     */
    public List<EvaluationRun> dispatchWaitingRuns(int limit) {
        List<EvaluationRun> waitingRuns = runLogic.listRunsByStatus(RunStatus.WAITING,
                limit, true, null);
        List<EvaluationRun> dispatched = new ArrayList<>();
        for (EvaluationRun run : waitingRuns) {
            int active = runLogic.countActiveRunsByApiKey(run.apiKey());
            if (active >= maxConcurrentPerApiKey) {
                log.debug("Waiting run still throttled: run_id={} active_count={} max={}",
                        run.id(), active, maxConcurrentPerApiKey);
                continue;
            }
            EvaluationRun claimed = runLogic.claimWaitingRun(run.id(),
                    DomainValidations.utcNow());
            if (claimed == null) {
                continue;
            }
            try {
                dispatcher.submit(claimed.id());
            } catch (RuntimeException e) {
                handleDispatchFailure(claimed, e, DomainValidations.utcNow());
                continue;
            }
            log.info("Waiting run dispatched: run_id={}", claimed.id());
            dispatched.add(claimed);
        }
        return dispatched;
    }

    private EvaluationRun transitionToWaiting(EvaluationRun run, boolean incrementAttempts) {
        EvaluationRun waiting = run.transition(RunStatus.WAITING,
                DomainValidations.utcNow(), null);
        if (incrementAttempts) {
            waiting = waiting.withDispatchAttempts(run.dispatchAttempts() + 1);
        }
        return waiting;
    }

    private void handleDispatchFailure(EvaluationRun run, RuntimeException exc,
            OffsetDateTime occurredAt) {
        int attempts = run.dispatchAttempts() + 1;
        if (attempts < maxDispatchAttempts) {
            EvaluationRun waiting = transitionToWaiting(run, true);
            runLogic.saveRun(waiting);
            log.warn("Run dispatch failed (attempt {}/{}): run_id={}, {}", attempts,
                    maxDispatchAttempts, run.id(), exc.getClass().getSimpleName());
            return;
        }
        EvaluationRun failed = run.transition(RunStatus.FAILED, occurredAt,
                "Run dispatch failed after " + attempts + " attempts: "
                        + exc.getClass().getSimpleName());
        runLogic.saveRun(failed);
    }
}
