package com.abchina.llmalf.agentgate.controller;

import com.abchina.llmalf.agentgate.common.AgentException;
import com.abchina.llmalf.agentgate.common.ResponseBase;
import com.abchina.llmalf.agentgate.common.UserContextHolder;
import com.abchina.llmalf.agentgate.domain.model.evaluationtask.EvaluationTask;
import com.abchina.llmalf.agentgate.domain.model.evaluationtask.EvaluationTaskKind;
import com.abchina.llmalf.agentgate.domain.model.report.EvaluationReport;
import com.abchina.llmalf.agentgate.domain.model.run.RunStatus;
import com.abchina.llmalf.agentgate.logic.RunLogic;
import com.abchina.llmalf.agentgate.logic.TaskLogic;
import com.abchina.llmalf.agentgate.service.impl.ResultService;
import com.abchina.llmalf.agentgate.service.impl.RunReaderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 稳定性实验列表端点(启动归 BJS 适配切片,summary 归 results 域切片).
 *
 * <p>对齐 Python server/routes/stability.py 的 GET 列表
 * (过滤 stability 类型 + 团队可见)。</p>
 */
@RestController
@org.springframework.web.bind.annotation.RequestMapping("/api/stability-experiments")
public class StabilityController {

    private final TaskLogic taskLogic;
    private final RunLogic runLogic;
    private final RunReaderService runReaderService;
    private final ResultService resultService;

    public StabilityController(TaskLogic taskLogic, RunLogic runLogic,
            RunReaderService runReaderService, ResultService resultService) {
        this.taskLogic = taskLogic;
        this.runLogic = runLogic;
        this.runReaderService = runReaderService;
        this.resultService = resultService;
    }

    /**
     * 稳定性实验列表.
     *
     * @return 任务 payload 列表(stability 类型)
     */
    @GetMapping
    public ResponseBase<List<Object>> listExperiments() {
        String teamId = UserContextHolder.current().userTeamId();
        List<Object> payloads = new ArrayList<>();
        for (EvaluationTask task : taskLogic.listEvaluationTasks()) {
            if (task.kind() == EvaluationTaskKind.STABILITY && allRunsVisible(task, teamId)) {
                payloads.add(task.toPayload());
            }
        }
        return ResponseBase.success(payloads);
    }

    private boolean allRunsVisible(EvaluationTask task, String teamId) {
        for (String runId : task.runIds()) {
            if (runLogic.getRun(runId, teamId) == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * 稳定性实验摘要(均值/方差/标准差,未完成不计分).
     *
     * @param taskId 任务 id
     * @return 摘要投影
     */
    @GetMapping("/{taskId}")
    public ResponseBase<Map<String, Object>> summary(@PathVariable("taskId") String taskId) {
        EvaluationTask task = taskLogic.getEvaluationTask(taskId);
        String teamId = UserContextHolder.current().userTeamId();
        if (task == null || task.kind() != EvaluationTaskKind.STABILITY
                || !allRunsVisible(task, teamId)) {
            throw new AgentException(404, "unknown stability experiment");
        }
        List<Map<String, Object>> rows = new ArrayList<>(task.runIds().size());
        List<Double> scores = new ArrayList<>();
        boolean complete = true;
        for (String runId : task.runIds()) {
            Map<String, Object> progress = runReaderService.getRunProgress(runId);
            Double score = null;
            String outcome = null;
            if ("completed".equals(progress.get("status"))) {
                EvaluationReport report = resultService.getReport(runId);
                for (com.abchina.llmalf.agentgate.domain.model.metric.MetricSummary metric
                        : report.metrics()) {
                    if ("overall".equals(metric.level())
                            && metric.errors() == 0) {
                        score = metric.score();
                        break;
                    }
                }
                outcome = report.releaseGate().outcome().wireValue();
                if (score != null) {
                    scores.add(score);
                }
            }
            if (!"completed".equals(progress.get("status"))
                    && !"failed".equals(progress.get("status"))
                    && !"cancelled".equals(progress.get("status"))) {
                complete = false;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("progress", progress);
            row.put("score", score);
            row.put("outcome", outcome);
            rows.add(row);
        }
        Map<String, Object> summaryPayload = new LinkedHashMap<>();
        summaryPayload.put("experiment", task.toPayload());
        summaryPayload.put("runs", rows);
        summaryPayload.put("measured_runs", scores.size());
        summaryPayload.put("mean", meanOrNull(scores));
        summaryPayload.put("sample_variance", sampleVarianceOrNull(scores));
        summaryPayload.put("sample_stddev", sampleStddevOrNull(scores));
        summaryPayload.put("min", minOrNull(scores));
        summaryPayload.put("max", maxOrNull(scores));
        summaryPayload.put("complete", complete);
        return ResponseBase.success(summaryPayload);
    }

    private static Double meanOrNull(List<Double> values) {
        return values.isEmpty() ? null
                : values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static Double sampleVarianceOrNull(List<Double> values) {
        if (values.size() < 2) {
            return null;
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double total = 0;
        for (double value : values) {
            total += (value - mean) * (value - mean);
        }
        return total / (values.size() - 1);
    }

    private static Double sampleStddevOrNull(List<Double> values) {
        Double variance = sampleVarianceOrNull(values);
        return variance == null ? null : Math.sqrt(variance);
    }

    private static Double minOrNull(List<Double> values) {
        return values.isEmpty() ? null
                : values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
    }

    private static Double maxOrNull(List<Double> values) {
        return values.isEmpty() ? null
                : values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
    }
}
