package com.abchina.llmalf.agentgate.service.impl;

import com.abchina.llmalf.agentgate.common.AgentException;
import com.abchina.llmalf.agentgate.domain.model.evaluationtask.EvaluationTask;
import com.abchina.llmalf.agentgate.domain.model.evaluationtask.EvaluationTaskKind;
import com.abchina.llmalf.agentgate.domain.model.metric.MetricSummary;
import com.abchina.llmalf.agentgate.domain.model.report.EvaluationReport;
import com.abchina.llmalf.agentgate.logic.RunLogic;
import com.abchina.llmalf.agentgate.logic.TaskLogic;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 稳定性实验查询与统计摘要编排.
 */
@Service
public class StabilityService {

    private final TaskLogic taskLogic;
    private final RunLogic runLogic;
    private final RunReaderService runReaderService;
    private final ResultService resultService;

    public StabilityService(TaskLogic taskLogic, RunLogic runLogic,
            RunReaderService runReaderService, ResultService resultService) {
        this.taskLogic = taskLogic;
        this.runLogic = runLogic;
        this.runReaderService = runReaderService;
        this.resultService = resultService;
    }

    public List<EvaluationTask> listExperiments(String teamId) {
        List<EvaluationTask> experiments = new ArrayList<>();
        for (EvaluationTask task : taskLogic.listEvaluationTasks()) {
            if (task.kind() == EvaluationTaskKind.STABILITY
                    && allRunsVisible(task, teamId)) {
                experiments.add(task);
            }
        }
        return experiments;
    }

    public Map<String, Object> getSummary(String taskId, String teamId) {
        EvaluationTask task = taskLogic.getEvaluationTask(taskId);
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
                for (MetricSummary metric : report.metrics()) {
                    if ("overall".equals(metric.level()) && metric.errors() == 0) {
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
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("experiment", task.toPayload());
        payload.put("runs", rows);
        payload.put("measured_runs", scores.size());
        payload.put("mean", meanOrNull(scores));
        payload.put("sample_variance", sampleVarianceOrNull(scores));
        payload.put("sample_stddev", sampleStddevOrNull(scores));
        payload.put("min", minOrNull(scores));
        payload.put("max", maxOrNull(scores));
        payload.put("complete", complete);
        return payload;
    }

    private boolean allRunsVisible(EvaluationTask task, String teamId) {
        for (String runId : task.runIds()) {
            if (runLogic.getRun(runId, teamId) == null) {
                return false;
            }
        }
        return true;
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
