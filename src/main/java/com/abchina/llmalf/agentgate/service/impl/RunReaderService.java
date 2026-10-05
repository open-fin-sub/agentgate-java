package com.abchina.llmalf.agentgate.service.impl;

import com.abchina.llmalf.agentgate.common.UserContextHolder;
import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.model.result.EvaluationResult;
import com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun;
import com.abchina.llmalf.agentgate.domain.model.run.RunStatus;
import com.abchina.llmalf.agentgate.logic.ResultLogic;
import com.abchina.llmalf.agentgate.logic.RunLogic;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Run 读取投影服务.
 *
 * <p>对齐 Python application/result_reader.py 的
 * list_runs/activity/get_run_progress/get_run_manifest/_project_run
 * 与 run_management.py::fail_stale_runs(只读 + 超时恢复面)。</p>
 */
@Service
public class RunReaderService {

    private final RunLogic runLogic;
    private final ResultLogic resultLogic;

    public RunReaderService(RunLogic runLogic, ResultLogic resultLogic) {
        this.runLogic = runLogic;
        this.resultLogic = resultLogic;
    }

    /**
     * 列出团队 Run(created_at 降序或按状态).
     *
     * @param limit 上限
     * @param status 状态(可空)
     * @return Run 列表
     */
    public List<EvaluationRun> listRuns(int limit, RunStatus status) {
        if (status != null) {
            return runLogic.listRunsByStatus(status, limit, false, teamId());
        }
        return runLogic.listRuns(limit, teamId());
    }

    /**
     * 活动视图(状态计数 + 调度/队列/运行/最近终态).
     *
     * @param recentLimit 最近终态上限
     * @return 活动投影
     */
    public Map<String, Object> activity(int recentLimit) {
        if (recentLimit < 1) {
            throw new IllegalArgumentException("recent Run limit must be at least 1");
        }
        String teamId = teamId();
        OffsetDateTime now = DomainValidations.utcNow();
        List<EvaluationRun> scheduled = runLogic.listRunsByStatus(RunStatus.SCHEDULED, null,
                true, teamId);
        List<EvaluationRun> queued = runLogic.listRunsByStatus(RunStatus.PENDING, null, true,
                teamId);
        List<EvaluationRun> running = runLogic.listRunsByStatus(RunStatus.RUNNING, null, true,
                teamId);
        List<EvaluationRun> terminal = new ArrayList<>();
        for (RunStatus status : new RunStatus[] {RunStatus.COMPLETED, RunStatus.FAILED,
                RunStatus.CANCELLED}) {
            terminal.addAll(runLogic.listRunsByStatus(status, null, false, teamId));
        }
        terminal.sort(Comparator
                .comparing((EvaluationRun run) -> run.completedAt() == null
                        ? run.createdAt() : run.completedAt())
                .thenComparing(EvaluationRun::id)
                .reversed());
        if (terminal.size() > recentLimit) {
            terminal = new ArrayList<>(terminal.subList(0, recentLimit));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<RunStatus, Integer> entry :
                runLogic.countRunsByStatus(teamId).entrySet()) {
            counts.put(entry.getKey().wireValue(), entry.getValue());
        }
        result.put("status_counts", counts);
        List<Object> scheduledProjections = new ArrayList<>();
        for (EvaluationRun run : scheduled) {
            scheduledProjections.add(projectRun(run, now, null));
        }
        result.put("scheduled", scheduledProjections);
        List<Object> queuedProjections = new ArrayList<>();
        int queuePosition = 1;
        for (EvaluationRun run : queued) {
            queuedProjections.add(projectRun(run, now, queuePosition++));
        }
        result.put("queued", queuedProjections);
        List<Object> runningProjections = new ArrayList<>();
        for (EvaluationRun run : running) {
            runningProjections.add(projectRun(run, now, null));
        }
        result.put("running", runningProjections);
        List<Object> recentProjections = new ArrayList<>();
        for (EvaluationRun run : terminal) {
            recentProjections.add(projectRun(run, now, null));
        }
        result.put("recent", recentProjections);
        return result;
    }

    /**
     * Run 进度投影(含队列位置).
     *
     * @param runId Run id
     * @return 进度投影
     */
    public Map<String, Object> getRunProgress(String runId) {
        EvaluationRun run = getRun(runId);
        Integer queuePosition = null;
        if (run.status() == RunStatus.PENDING) {
            List<EvaluationRun> queued = runLogic.listRunsByStatus(RunStatus.PENDING, null,
                    true, teamId());
            for (int i = 0; i < queued.size(); i++) {
                if (queued.get(i).id().equals(run.id())) {
                    queuePosition = i + 1;
                    break;
                }
            }
            if (queuePosition == null) {
                run = getRun(runId);
            }
        }
        return projectRun(run, DomainValidations.utcNow(), queuePosition);
    }

    /**
     * Run 清单.
     *
     * @param runId Run id
     * @return 清单 payload
     */
    public Object getRunManifest(String runId) {
        return getRun(runId).manifest().toPayload();
    }

    /**
     * 超时恢复:将超过恢复截止时间的 RUNNING Run 置为 FAILED.
     *
     * @param graceSeconds 宽限秒数
     */
    public void failStaleRuns(double graceSeconds) {
        if (graceSeconds < 0) {
            throw new IllegalArgumentException("grace_seconds must not be negative");
        }
        OffsetDateTime now = DomainValidations.utcNow();
        for (EvaluationRun run : runLogic.listRunsByStatus(RunStatus.RUNNING, null, false,
                null)) {
            int caseCount = run.manifest().executionCases().size();
            int maxParallel = run.manifest().maxParallelCases();
            int batchCount = (caseCount + maxParallel - 1) / maxParallel;
            double retryBudget = 0;
            for (int retry = 1; retry <= run.manifest().maxRetries(); retry++) {
                retryBudget += retryDelaySeconds(retry);
            }
            double executionSeconds = run.manifest().timeoutSeconds() * batchCount
                    + caseCount * (run.manifest().timeoutSeconds()
                            * run.manifest().maxRetries() + retryBudget);
            OffsetDateTime deadline = run.startedAt().plus(
                    Duration.ofMillis((long) ((executionSeconds + graceSeconds) * 1000)));
            if (deadline.isAfter(now)) {
                continue;
            }
            EvaluationRun failed = run.transition(RunStatus.FAILED, now,
                    "Execution worker exceeded its recovery deadline");
            try {
                runLogic.saveRun(failed);
            } catch (RuntimeException e) {
                // 并发下 Run 已被执行进程推进:重查状态,非 RUNNING 则跳过(对齐 Python)
                EvaluationRun current = runLogic.getRun(run.id(), null);
                if (current == null || current.status() == RunStatus.RUNNING) {
                    throw e;
                }
            }
        }
    }

    private Map<String, Object> projectRun(EvaluationRun run, OffsetDateTime now,
            Integer queuePosition) {
        Set<String> expectedEvaluators = new HashSet<>();
        for (com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSpec spec
                : run.manifest().evaluatorSpecs()) {
            expectedEvaluators.add(spec.id());
        }
        Map<String, Set<String>> resultsByCase = new HashMap<>();
        for (EvaluationResult result : resultLogic.listResults(run.id())) {
            resultsByCase.computeIfAbsent(result.caseId(), key -> new HashSet<>())
                    .add(result.evaluatorId());
        }
        int completedCases = 0;
        int totalCases = run.manifest().executionCases().size();
        for (com.abchina.llmalf.agentgate.domain.model.cases.Case caseItem
                : run.manifest().executionCases()) {
            Set<String> evaluators = resultsByCase.get(caseItem.id());
            if (evaluators != null && evaluators.containsAll(expectedEvaluators)) {
                completedCases++;
            }
        }
        Double durationSeconds = null;
        if (run.startedAt() != null) {
            OffsetDateTime end = run.completedAt() == null ? now : run.completedAt();
            // 微秒精度对齐 Python total_seconds()(避免毫秒截断)
            durationSeconds = Math.max(0.0,
                    Duration.between(run.startedAt(), end).toNanos() / 1_000_000_000.0);
        }

        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("run_id", run.id());
        projection.put("status", run.status().wireValue());
        projection.put("dataset_id", run.manifest().dataset().datasetId());
        projection.put("dataset_version", run.manifest().dataset().version());
        projection.put("dataset_name", run.manifest().dataset().datasetName());
        projection.put("target_name", run.manifest().target().displayName());
        projection.put("target_version",
                run.manifest().target().ref().externalVersionId());
        projection.put("total_cases", totalCases);
        projection.put("completed_cases", completedCases);
        projection.put("progress", totalCases == 0 ? 0.0 : (double) completedCases
                / totalCases);
        projection.put("created_at", DomainValidations.isoFormat(run.createdAt()));
        projection.put("scheduled_for", run.scheduledFor() == null ? null
                : DomainValidations.isoFormat(run.scheduledFor()));
        projection.put("started_at", run.startedAt() == null ? null
                : DomainValidations.isoFormat(run.startedAt()));
        projection.put("completed_at", run.completedAt() == null ? null
                : DomainValidations.isoFormat(run.completedAt()));
        projection.put("duration_seconds", durationSeconds);
        projection.put("error", run.error());
        projection.put("queue_position", queuePosition);
        return projection;
    }

    private EvaluationRun getRun(String runId) {
        EvaluationRun run = runLogic.getRun(runId, teamId());
        if (run == null) {
            throw new IllegalArgumentException("unknown EvaluationRun: " + runId);
        }
        return run;
    }

    private static double retryDelaySeconds(int retryNumber) {
        if (retryNumber >= 6) {
            return 30.0;
        }
        return (double) (1 << (retryNumber - 1));
    }

    private static String teamId() {
        return UserContextHolder.current().userTeamId();
    }
}
