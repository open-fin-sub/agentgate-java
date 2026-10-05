package com.abchina.llmalf.agentgate.service.impl;

import com.abchina.llmalf.agentgate.common.AgentException;
import com.abchina.llmalf.agentgate.common.UserContextHolder;
import com.abchina.llmalf.agentgate.domain.model.cases.Case;
import com.abchina.llmalf.agentgate.domain.model.dataset.Dataset;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersion;
import com.abchina.llmalf.agentgate.domain.model.report.EvaluationReport;
import com.abchina.llmalf.agentgate.domain.model.report.ReportAssembler;
import com.abchina.llmalf.agentgate.domain.model.result.EvaluationResult;
import com.abchina.llmalf.agentgate.domain.model.result.Outcome;
import com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun;
import com.abchina.llmalf.agentgate.domain.model.run.RunStatus;
import com.abchina.llmalf.agentgate.domain.model.trace.Trace;
import com.abchina.llmalf.agentgate.logic.DatasetLogic;
import com.abchina.llmalf.agentgate.logic.DatasetVersioning;
import com.abchina.llmalf.agentgate.logic.ResultLogic;
import com.abchina.llmalf.agentgate.logic.RunLogic;
import com.abchina.llmalf.agentgate.logic.TraceLogic;
import com.abchina.llmalf.agentgate.logic.TraceRedactor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 结果读取服务.
 *
 * <p>对齐 Python application/result_reader.py 与
 * result_case_writeback.py:报告组装/概览/样本/轨迹脱敏/历史用例/回写。</p>
 */
@Service
public class ResultService {

    private final RunLogic runLogic;
    private final ResultLogic resultLogic;
    private final TraceLogic traceLogic;
    private final DatasetLogic datasetLogic;
    private final int maxRuns;

    public ResultService(RunLogic runLogic, ResultLogic resultLogic,
            TraceLogic traceLogic, DatasetLogic datasetLogic,
            @org.springframework.beans.factory.annotation.Value(
                    "${agentgate.overview.max-runs:50}") int maxRuns) {
        this.runLogic = runLogic;
        this.resultLogic = resultLogic;
        this.traceLogic = traceLogic;
        this.datasetLogic = datasetLogic;
        this.maxRuns = maxRuns;
    }

    /**
     * 团队概览.
     *
     * @return 概览投影
     */
    public Map<String, Object> overview() {
        String teamId = teamId();
        List<EvaluationRun> runs = runLogic.listRuns(maxRuns, teamId);
        Map<RunStatus, Integer> statuses = runLogic.countRunsByStatus(teamId);
        List<Dataset> datasets = datasetLogic.listDatasets(false, teamId);
        int caseCount = 0;
        for (Dataset dataset : datasets) {
            DatasetVersion version = datasetLogic.getLatestPublishedDatasetVersion(
                    dataset.id(), teamId);
            if (version != null) {
                caseCount += version.cases().size();
            }
        }
        EvaluationRun latestRun = null;
        for (EvaluationRun run : runs) {
            if (run.status() == RunStatus.COMPLETED) {
                latestRun = run;
                break;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        int total = 0;
        for (int count : statuses.values()) {
            total += count;
        }
        result.put("total_runs", total);
        result.put("scheduled_runs", statuses.get(RunStatus.SCHEDULED));
        result.put("pending_runs", statuses.get(RunStatus.PENDING));
        result.put("running_runs", statuses.get(RunStatus.RUNNING));
        result.put("completed_runs", statuses.get(RunStatus.COMPLETED));
        result.put("failed_runs", statuses.get(RunStatus.FAILED));
        result.put("cancelled_runs", statuses.get(RunStatus.CANCELLED));
        result.put("dataset_count", datasets.size());
        result.put("case_count", caseCount);
        result.put("latest", latestRun == null ? null
                : getReport(latestRun.id()).toPayload());
        return result;
    }

    /**
     * 组装报告(要求已完成).
     *
     * @param runId Run id
     * @return 报告
     */
    public EvaluationReport getReport(String runId) {
        EvaluationRun run = getRun(runId);
        if (run.status() != RunStatus.COMPLETED) {
            throw new AgentException(409,
                    "EvaluationReport requires a completed EvaluationRun");
        }
        try {
            return ReportAssembler.build(run, resultLogic.listResults(run.id()));
        } catch (IllegalArgumentException e) {
            throw new AgentException(409, e.getMessage());
        }
    }

    /**
     * 持久化证据样本(不限终态).
     *
     * @param runId Run id
     * @return 样本投影
     */
    public Map<String, Object> samples(String runId) {
        EvaluationRun run = getRun(runId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("run", run.toPayload());
        List<Object> resultPayloads = new ArrayList<>();
        for (EvaluationResult item : resultLogic.listResults(run.id())) {
            resultPayloads.add(item.toPayload());
        }
        result.put("results", resultPayloads);
        result.put("complete", run.status() == RunStatus.COMPLETED);
        return result;
    }

    /**
     * 轨迹脱敏视图.
     *
     * @param runId Run id
     * @param caseId 用例 id
     * @return 脱敏轨迹
     */
    public Trace getTrace(String runId, String caseId) {
        getRun(runId);
        Trace trace = traceLogic.getTrace(runId, caseId);
        if (trace == null) {
            throw new AgentException(404, "unknown Trace: " + runId + "/" + caseId);
        }
        return TraceRedactor.redact(trace);
    }

    /**
     * 历史用例快照与结果.
     *
     * @param runId Run id
     * @param caseId 用例 id
     * @return 历史用例投影
     */
    public Map<String, Object> getHistoricalCase(String runId, String caseId) {
        EvaluationRun run = getRun(runId);
        Case caseItem = null;
        for (Case item : run.manifest().executionCases()) {
            if (item.id().equals(caseId)) {
                caseItem = item;
                break;
            }
        }
        if (caseItem == null) {
            throw new AgentException(404,
                    "EvaluationRun did not execute Case: " + runId + "/" + caseId);
        }
        List<Object> resultPayloads = new ArrayList<>();
        for (EvaluationResult result : resultLogic.listResults(run.id())) {
            if (result.caseId().equals(caseItem.id())) {
                resultPayloads.add(result.toPayload());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("run_id", run.id());
        result.put("dataset_id", run.manifest().dataset().datasetId());
        result.put("dataset_version", run.manifest().dataset().version());
        result.put("case", caseItem.toPayload());
        result.put("results", resultPayloads);
        return result;
    }

    /**
     * 将失败历史用例回写为数据集草稿.
     *
     * @param runId Run id
     * @param caseId 用例 id
     * @param editedCase 编辑后用例
     * @return 回写结果投影
     */
    public Map<String, Object> writeback(String runId, String caseId, Case editedCase) {
        Map<String, Object> source = getHistoricalCase(runId, caseId);
        boolean hasFailed = false;
        for (EvaluationResult result : resultLogic.listResults(runId)) {
            if (result.caseId().equals(caseId) && result.outcome() == Outcome.FAIL) {
                hasFailed = true;
                break;
            }
        }
        if (!hasFailed) {
            throw new AgentException(409,
                    "Case has no failed Result: " + runId + "/" + caseId);
        }
        if (!editedCase.id().equals(caseId)) {
            throw new AgentException(422,
                    "edited Case id must match the historical Case id");
        }
        String datasetId = (String) source.get("dataset_id");
        Dataset dataset = datasetLogic.getDataset(datasetId, teamId());
        if (dataset == null) {
            throw new AgentException(404, "unknown Dataset: " + datasetId);
        }
        if (dataset.archived()) {
            throw new AgentException(409, "archived Dataset cannot be edited");
        }
        DatasetVersion draft = datasetLogic.getDatasetDraft(datasetId, teamId());
        if (draft == null) {
            DatasetVersion latest = datasetLogic.getLatestPublishedDatasetVersion(
                    datasetId, teamId());
            if (latest == null) {
                throw new AgentException(409,
                        "Dataset has no published version: " + datasetId);
            }
            draft = DatasetVersioning.createDraft(dataset, latest,
                    java.util.UUID.randomUUID().toString(),
                    com.abchina.llmalf.agentgate.domain.DomainValidations.utcNow());
            datasetLogic.saveDatasetVersion(draft);
        }
        DatasetVersion updated = DatasetVersioning.withCase(draft, editedCase,
                com.abchina.llmalf.agentgate.domain.DomainValidations.utcNow());
        datasetLogic.saveDatasetVersion(updated);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source_run_id", runId);
        result.put("source_dataset_id", datasetId);
        result.put("source_dataset_version", source.get("dataset_version"));
        result.put("source_case_id", caseId);
        result.put("draft", updated.toPayload());
        return result;
    }

    private EvaluationRun getRun(String runId) {
        EvaluationRun run = runLogic.getRun(runId, teamId());
        if (run == null) {
            throw new AgentException(404, "unknown EvaluationRun: " + runId);
        }
        return run;
    }

    private static String teamId() {
        return UserContextHolder.current().userTeamId();
    }
}
