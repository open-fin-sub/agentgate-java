package com.abchina.llmalf.agentgate.service.impl;

import com.abchina.llmalf.agentgate.common.AgentException;
import com.abchina.llmalf.agentgate.common.UserContext;
import com.abchina.llmalf.agentgate.common.UserContextHolder;
import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersion;
import com.abchina.llmalf.agentgate.domain.model.evaluationtask.EvaluationTask;
import com.abchina.llmalf.agentgate.domain.model.evaluationtask.EvaluationTaskKind;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorRef;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSpec;
import com.abchina.llmalf.agentgate.domain.model.gate.ReleaseGateSpec;
import com.abchina.llmalf.agentgate.domain.model.metric.MetricPlan;
import com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun;
import com.abchina.llmalf.agentgate.domain.model.run.RunLifecycle;
import com.abchina.llmalf.agentgate.domain.model.run.RunManifest;
import com.abchina.llmalf.agentgate.domain.model.run.RunStatus;
import com.abchina.llmalf.agentgate.domain.model.target.TargetDescriptor;
import com.abchina.llmalf.agentgate.domain.model.target.TargetRef;
import com.abchina.llmalf.agentgate.domain.model.target.TargetSnapshot;
import com.abchina.llmalf.agentgate.domain.model.target.TargetType;
import com.abchina.llmalf.agentgate.integration.BjsJobDispatcher;
import com.abchina.llmalf.agentgate.logic.DatasetLogic;
import com.abchina.llmalf.agentgate.logic.RunLogic;
import com.abchina.llmalf.agentgate.logic.TargetLogic;
import com.abchina.llmalf.agentgate.logic.TaskLogic;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Run 创建与派发服务.
 *
 * <p>对齐 Python RunManagement.create_run/dispatch_run 与
 * ServerDependencies.submit_demo_run/_resolve_demo_target:
 * demo Loan Agent 目标目录固化、规格选择校验、PENDING/SCHEDULED 创建、
 * 限流 WAITING、BJS 提交。</p>
 */
@Slf4j
@Service
public class RunLaunchService {

    /**
     * demo 目录固定时间戳(对齐 DEMO_CREATED_AT)
     */
    public static final OffsetDateTime DEMO_CREATED_AT =
            OffsetDateTime.parse("2026-01-01T00:00:00Z");

    private final RunLogic runLogic;
    private final DatasetLogic datasetLogic;
    private final TargetLogic targetLogic;
    private final TaskLogic taskLogic;
    private final BjsJobDispatcher dispatcher;
    private final EvaluatorSelection evaluatorSelection;
    private final int maxConcurrentPerApiKey;
    private final int maxDispatchAttempts;

    public RunLaunchService(RunLogic runLogic, DatasetLogic datasetLogic,
                            TargetLogic targetLogic, TaskLogic taskLogic, BjsJobDispatcher dispatcher,
                            EvaluatorSelection evaluatorSelection,
                            @Value("${agentgate.scheduling.max-concurrent-per-api-key:10}")
                            int maxConcurrentPerApiKey,
                            @Value("${agentgate.scheduling.max-dispatch-attempts:10}")
                            int maxDispatchAttempts) {
        this.runLogic = runLogic;
        this.datasetLogic = datasetLogic;
        this.targetLogic = targetLogic;
        this.taskLogic = taskLogic;
        this.dispatcher = dispatcher;
        this.evaluatorSelection = evaluatorSelection;
        this.maxConcurrentPerApiKey = maxConcurrentPerApiKey;
        this.maxDispatchAttempts = maxDispatchAttempts;
    }

    /**
     * 创建并按需派发一个 demo Run.
     *
     * @param version          demo 目标版本
     * @param datasetId        数据集 id
     * @param datasetVersion   数据集版本(可空,默认最新发布)
     * @param caseIds          用例 id(可空)
     * @param evaluatorIds     评测器 id(可空,默认内置全套)
     * @param timeoutSeconds   超时秒
     * @param maxParallelCases 并行度
     * @param maxRetries       重试上限
     * @param scheduledFor     计划时间(可空)
     * @param apiKey           API Key(可空)
     * @return Run
     */
    public EvaluationRun submitDemoRun(String version, String datasetId,
                                       Integer datasetVersion, List<String> caseIds, List<String> evaluatorIds,
                                       double timeoutSeconds, int maxParallelCases, int maxRetries,
                                       OffsetDateTime scheduledFor, String apiKey) {
        EvaluationRun run = createRun(resolveDemoTarget(version), datasetId, datasetVersion,
                caseIds, evaluatorIds, null, timeoutSeconds, maxParallelCases, maxRetries,
                scheduledFor, apiKey);
        taskLogic.saveTaskRuns(
                EvaluationTask.of(
                        run.id(),
                        EvaluationTaskKind.SINGLE,
                        null, Collections.singletonList(run.id()), null, null, null),
                Collections.singletonList(run));
        if (run.status() == RunStatus.SCHEDULED) {
            return run;
        }
        return dispatchPersisted(run);
    }

    /**
     * 派发一个已落库的 PENDING Run(对齐 Python dispatch_run:
     * 限流转 WAITING;提交失败 attempts+1 转 WAITING 重试,耗尽转 FAILED 并抛 503).
     *
     * @param run 已落库 Run
     * @return 派发后 Run(WAITING 或原状态)
     */
    private EvaluationRun dispatchPersisted(EvaluationRun run) {
        int active = runLogic.countActiveRunsByApiKey(run.apiKey());
        if (active > maxConcurrentPerApiKey) {
            EvaluationRun waiting = run.transition(RunStatus.WAITING,
                    DomainValidations.utcNow(), null);
            runLogic.saveRun(waiting);
            log.warn("Run throttled to waiting: run_id={} active_count={} max={}",
                    run.id(), active, maxConcurrentPerApiKey);
            return waiting;
        }
        try {
            dispatcher.submit(run.id());
        } catch (RuntimeException e) {
            int attempts = run.dispatchAttempts() + 1;
            if (attempts < maxDispatchAttempts) {
                EvaluationRun waiting = run.transition(RunStatus.WAITING,
                        DomainValidations.utcNow(), null);
                runLogic.saveRun(waiting.withDispatchAttempts(attempts));
                log.warn("Run dispatch failed, retrying: run_id={} attempt={}/{} error={}",
                        run.id(), attempts, maxDispatchAttempts,
                        e.getClass().getSimpleName());
                return waiting;
            }
            EvaluationRun failed = run.transition(RunStatus.FAILED,
                    DomainValidations.utcNow(),
                    "Run dispatch failed after " + attempts + " attempts: "
                            + e.getClass().getSimpleName());
            runLogic.saveRun(failed);
            log.error("Run dispatch exhausted retries: run_id={} attempts={} status=failed",
                    run.id(), attempts);
            throw new IllegalStateException("Run dispatch failed");
        }
        log.info("Run dispatched: run_id={}", run.id());
        return run;
    }

    /**
     * A/B 派发并重载(对齐 Python _dispatch_and_reload:
     * 派发失败耗尽重试导致 FAILED 时返回失败态,否则透传 503).
     */
    private EvaluationRun dispatchAndReload(EvaluationRun run) {
        EvaluationRun dispatched;
        try {
            dispatched = dispatchPersisted(run);
        } catch (IllegalStateException e) {
            EvaluationRun current = runLogic.getRun(run.id(),
                    UserContextHolder.current().userTeamId());
            if (current == null || current.status() != RunStatus.FAILED) {
                throw new AgentException(503, "Evaluation dispatch service is unavailable");
            }
            return current;
        }
        EvaluationRun current = runLogic.getRun(dispatched.id(),
                UserContextHolder.current().userTeamId());
        return current == null ? dispatched : current;
    }

    /**
     * 创建 A/B 对照两个 Run.
     *
     * @param baselineVersion  基线目标版本
     * @param candidateVersion 候选目标版本
     * @param datasetId        数据集 id
     * @param datasetVersion   数据集版本
     * @param evaluatorRefs    评测器引用(可空)
     * @return 两个 Run(baseline/candidate)
     */
    public EvaluationRun[] submitAbRuns(String baselineVersion, String candidateVersion,
                                        String datasetId, Integer datasetVersion, List<EvaluatorRef> evaluatorRefs) {
        TargetSnapshot baselineTarget = resolveDemoTarget(baselineVersion);
        TargetSnapshot candidateTarget = resolveDemoTarget(candidateVersion);
        validateVariants(baselineTarget, candidateTarget);
        EvaluationRun baseline = createRun(baselineTarget, datasetId,
                datasetVersion, null, null, evaluatorRefs, 300, 1, 0, null, null);
        EvaluationRun candidate = createRun(candidateTarget, datasetId,
                datasetVersion, null, null, evaluatorRefs, 300, 1, 0, null, null);
        taskLogic.saveTaskRuns(
                EvaluationTask.of(
                        baseline.id(),
                        EvaluationTaskKind.AB,
                        null, Arrays.asList(baseline.id(), candidate.id()), null, null, null),
                Arrays.asList(baseline, candidate));
        return new EvaluationRun[]{dispatchAndReload(baseline), dispatchAndReload(candidate)};
    }

    /**
     * A/B 变体校验(对齐 Python _validate_variants 四条规则).
     */
    private static void validateVariants(TargetSnapshot baseline, TargetSnapshot candidate) {
        if (baseline.ref().targetType() != TargetType.AGENT
                || candidate.ref().targetType() != TargetType.AGENT) {
            throw new AgentException(422, "A/B variants must be Agent Targets");
        }
        boolean sameIdentity = baseline.ref().sourceId()
                .equals(candidate.ref().sourceId())
                && baseline.ref().externalTargetId()
                .equals(candidate.ref().externalTargetId());
        if (!sameIdentity) {
            throw new AgentException(422,
                    "A/B variants must reference the same logical Agent");
        }
        if (baseline.ref().externalVersionId()
                .equals(candidate.ref().externalVersionId())) {
            throw new AgentException(422,
                    "A/B variants must use different Agent versions");
        }
        if (baseline.contentSha256().equals(candidate.contentSha256())) {
            throw new AgentException(422,
                    "A/B variants must use different immutable Target snapshots");
        }
    }

    /**
     * 创建稳定性实验 repetitions 个 Run(共享清单语义由清单一致性约束保证).
     *
     * @param version 目标版本
     * @param repetitions 重复次数(2-20)
     * @param datasetId 数据集 id
     * @param datasetVersion 数据集版本
     * @param caseIds 用例 id
     * @param evaluatorIds 评测器 id
     * @param timeoutSeconds 超时秒
     * @param maxParallelCases 并行度
     * @param maxRetries 重试上限
     * @param apiKey API Key
     * @return Run 列表
     */
    /**
     * 创建稳定性实验 repetitions 个 Run(共享清单),落 stability 任务后逐个派发.
     *
     * <p>对齐 Python submit_stability_runs:单 Run 派发失败保留整组(吞 503 继续)。</p>
     *
     * @param version          目标版本
     * @param repetitions      重复次数(2-20)
     * @param datasetId        数据集 id
     * @param datasetVersion   数据集版本
     * @param caseIds          用例 id(可空)
     * @param evaluatorIds     评测器 id(可空)
     * @param timeoutSeconds   超时秒
     * @param maxParallelCases 并行度
     * @param maxRetries       重试上限
     * @param apiKey           API Key
     * @return 稳定性任务
     */
    public EvaluationTask
    submitStabilityRuns(String version, int repetitions,
                        String datasetId, Integer datasetVersion, List<String> caseIds,
                        List<String> evaluatorIds, double timeoutSeconds, int maxParallelCases,
                        int maxRetries, String apiKey) {
        EvaluationRun template = createRun(resolveDemoTarget(version), datasetId,
                datasetVersion, caseIds, evaluatorIds, null, timeoutSeconds,
                maxParallelCases, maxRetries, null, apiKey);
        if (template.status() != RunStatus.PENDING) {
            throw new AgentException(422, "stability does not support scheduling");
        }
        List<EvaluationRun> runs = new ArrayList<>(repetitions);
        runs.add(template);
        UserContext context = UserContextHolder.current();
        for (int index = 1; index < repetitions; index++) {
            EvaluationRun copy = EvaluationRun.of(
                    UUID.randomUUID().toString(), template.manifest(),
                    RunLifecycle.of(RunStatus.PENDING, DomainValidations.utcNow(),
                            null, null, null, null),
                    context.userTeamId(), context.userId(), context.userName(),
                    apiKey, 0);
            runs.add(copy);
        }
        List<String> runIds = new ArrayList<>(runs.size());
        for (EvaluationRun run : runs) {
            runIds.add(run.id());
        }
        EvaluationTask task =
                EvaluationTask.of(
                        template.id(),
                        EvaluationTaskKind.STABILITY,
                        null, runIds, null, null, null);
        taskLogic.saveTaskRuns(task, runs);
        for (EvaluationRun run : runs) {
            try {
                dispatchPersisted(run);
            } catch (IllegalStateException e) {
                // 派发失败已落库,保留整组供检查(对齐 Python 吞 RuntimeError 继续)
            }
        }
        return task;
    }

    /**
     * 派发一个已落库的 PENDING Run(公开入口,对齐 Python dispatch_run).
     *
     * @param runId Run id
     * @return Run(可能为 WAITING)
     */
    public EvaluationRun dispatchRun(String runId) {
        EvaluationRun run = runLogic.getRun(runId,
                UserContextHolder.current().userTeamId());
        if (run == null) {
            throw new AgentException(422, "unknown EvaluationRun: " + runId);
        }
        if (run.status() != RunStatus.PENDING) {
            throw new AgentException(422, "only a pending EvaluationRun can be dispatched");
        }
        return dispatchPersisted(run);
    }

    /**
     * 以终态源 Run 的原始清单创建并派发一个新 Run.
     *
     * <p>新 Run 与单次任务先原子落库,再调用外部派发器。源 Run 的清单和
     * 用户归属保持不变,生命周期和执行产物不继承。</p>
     *
     * @param sourceRunId 源 Run id
     * @return 派发后的新 Run(PENDING 或 WAITING)
     */
    public EvaluationRun rerunRun(String sourceRunId) {
        String currentTeamId = UserContextHolder.current().userTeamId();
        EvaluationRun source = runLogic.getRun(sourceRunId, currentTeamId);
        if (source == null) {
            throw new AgentException(404, "unknown EvaluationRun: " + sourceRunId);
        }
        if (source.status() == RunStatus.SCHEDULED
                || source.status() == RunStatus.PENDING
                || source.status() == RunStatus.WAITING
                || source.status() == RunStatus.RUNNING) {
            throw new AgentException(409,
                    "cannot rerun " + source.status().wireValue() + " EvaluationRun");
        }

        String rerunId = UUID.randomUUID().toString();
        EvaluationRun rerun = EvaluationRun.of(rerunId, source.manifest(),
                RunLifecycle.of(RunStatus.PENDING, DomainValidations.utcNow(),
                        null, null, null, null),
                source.userTeamId(), source.userId(), source.userName(),
                source.apiKey(), 0);
        taskLogic.saveTaskRuns(
                EvaluationTask.of(
                        rerunId,
                        EvaluationTaskKind.SINGLE,
                        null, Collections.singletonList(rerunId), null, null, null),
                Collections.singletonList(rerun));
        try {
            return dispatchRun(rerunId);
        } catch (IllegalStateException e) {
            throw new AgentException(503, "Evaluation dispatch service is unavailable");
        }
    }

    /**
     * 取消 Run(对齐 Python cancel_run:未知 404、已取消幂等 200、终态 409).
     *
     * @param runId Run id
     * @return 取消后的 Run
     */
    public EvaluationRun cancelRun(String runId) {
        String teamId = UserContextHolder.current().userTeamId();
        EvaluationRun run = runLogic.getRun(runId, teamId);
        if (run == null) {
            throw new AgentException(404, "unknown EvaluationRun: " + runId);
        }
        if (run.status() == RunStatus.CANCELLED) {
            return run;
        }
        if (run.status() == RunStatus.COMPLETED || run.status() == RunStatus.FAILED) {
            throw new AgentException(409,
                    "cannot cancel " + run.status().wireValue() + " EvaluationRun");
        }
        boolean wasDispatched = run.status() == RunStatus.PENDING
                || run.status() == RunStatus.RUNNING;
        EvaluationRun cancelled = runLogic.cancelRun(runId, DomainValidations.utcNow(),
                teamId);
        if (cancelled == null) {
            EvaluationRun current = runLogic.getRun(runId, teamId);
            if (current == null) {
                throw new AgentException(404, "unknown EvaluationRun: " + runId);
            }
            if (current.status() == RunStatus.CANCELLED) {
                return current;
            }
            if (current.status() == RunStatus.COMPLETED
                    || current.status() == RunStatus.FAILED) {
                throw new AgentException(409,
                        "cannot cancel " + current.status().wireValue()
                                + " EvaluationRun");
            }
            throw new AgentException(503, "Run cancellation could not be persisted");
        }
        if (wasDispatched) {
            dispatcher.cancel(run.id());
        }
        return cancelled;
    }

    private EvaluationRun createRun(TargetSnapshot target, String datasetId,
                                    Integer datasetVersion, List<String> caseIds, List<String> evaluatorIds,
                                    List<EvaluatorRef> evaluatorRefs, double timeoutSeconds,
                                    int maxParallelCases, int maxRetries, OffsetDateTime scheduledFor,
                                    String apiKey) {
        DatasetVersion dataset = datasetVersion != null
                ? requirePublishedVersion(datasetId, datasetVersion)
                : latestPublishedVersion(datasetId);
        if (evaluatorIds != null && evaluatorRefs != null) {
            throw new AgentException(422,
                    "use evaluator_ids or evaluator_refs, not both");
        }
        List<EvaluatorSpec> selected = evaluatorRefs != null
                ? evaluatorSelection.selectVersions(evaluatorRefs)
                : evaluatorSelection.select(evaluatorIds);
        evaluatorSelection.validatePlan(dataset, selected);
        UserContext context = UserContextHolder.current();
        OffsetDateTime scheduled = scheduledFor == null ? null
                : DomainValidations.normalizeUtc(scheduledFor,
                "EvaluationRun scheduled_for");
        RunManifest manifest = RunManifest.of(dataset, caseIds, target, selected,
                primaryIds(selected), MetricPlan.of(), ReleaseGateSpec.of(), timeoutSeconds,
                maxRetries, maxParallelCases, null, "");
        RunStatus status = scheduled != null ? RunStatus.SCHEDULED : RunStatus.PENDING;
        EvaluationRun run = EvaluationRun.of(UUID.randomUUID().toString(),
                manifest,
                RunLifecycle.of(status, DomainValidations.utcNow(), scheduled, null, null,
                        null),
                context.userTeamId(), context.userId(), context.userName(), apiKey, 0);
        // 不在此落库:由 saveTaskRuns 首次插入(对齐 Python persist=False + save_task_runs)
        log.info("Run created: run_id={} status={} dataset_id={} case_count={}",
                run.id(), run.status().wireValue(), dataset.datasetId(),
                run.manifest().executionCases().size());
        return run;
    }

    private static List<String> primaryIds(List<EvaluatorSpec> specs) {
        List<String> ids = new ArrayList<>(specs.size());
        for (EvaluatorSpec spec : specs) {
            ids.add(spec.id());
        }
        return ids;
    }

    private DatasetVersion requirePublishedVersion(String datasetId, int version) {
        // 两段式对齐 Python:get_dataset 身份先行,再查版本
        if (datasetLogic.getDataset(datasetId, teamId()) == null) {
            throw new AgentException(422, "unknown Dataset: " + datasetId);
        }
        DatasetVersion item = datasetLogic.getPublishedDatasetVersion(datasetId, version,
                teamId());
        if (item == null) {
            throw new AgentException(422,
                    "unknown Dataset version: " + datasetId + " v" + version);
        }
        return item;
    }

    private DatasetVersion latestPublishedVersion(String datasetId) {
        if (datasetLogic.getDataset(datasetId, teamId()) == null) {
            throw new AgentException(422, "unknown Dataset: " + datasetId);
        }
        DatasetVersion item = datasetLogic.getLatestPublishedDatasetVersion(datasetId,
                teamId());
        if (item == null) {
            throw new AgentException(422,
                    "Dataset has no published version: " + datasetId);
        }
        return item;
    }

    private TargetSnapshot resolveDemoTarget(String version) {
        if (!"loan-agent-v1-risky".equals(version)
                && !"loan-agent-v2-fixed".equals(version)) {
            throw new AgentException(422, "unknown demo Target version: " + version);
        }
        TargetRef ref = TargetRef.of("agentgate-demo", TargetType.AGENT,
                "loan-agent", version);
        TargetDescriptor descriptor =
                targetLogic.getTargetDescriptor(
                        DemoCatalog.descriptor(version).contentSha256());
        if (descriptor == null) {
            throw new AgentException(422,
                    "unknown TargetDescriptor: " + version);
        }
        if (!sameRef(descriptor.ref(), ref)) {
            throw new AgentException(422,
                    "TargetDescriptor reference does not match TargetSnapshot");
        }
        Map<String, Object> invocationConfig = new HashMap<>();
        invocationConfig.put("provider", "deterministic");
        return TargetSnapshot.of(ref, "Loan Agent", "demo_loan", "1",
                descriptor.contentSha256(), invocationConfig, null,
                DomainValidations.utcNow(), "");
    }

    private static boolean sameRef(TargetRef left, TargetRef right) {
        return left.sourceId().equals(right.sourceId())
                && left.targetType() == right.targetType()
                && left.externalTargetId().equals(right.externalTargetId())
                && left.externalVersionId().equals(right.externalVersionId());
    }

    private static String teamId() {
        return UserContextHolder.current().userTeamId();
    }
}
