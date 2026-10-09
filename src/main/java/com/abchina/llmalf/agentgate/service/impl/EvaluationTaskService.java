package com.abchina.llmalf.agentgate.service.impl;

import com.abchina.llmalf.agentgate.common.AgentException;
import com.abchina.llmalf.agentgate.domain.model.evaluationtask.EvaluationTask;
import com.abchina.llmalf.agentgate.domain.model.evaluationtask.EvaluationTaskKind;
import com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun;
import com.abchina.llmalf.agentgate.domain.model.target.TargetRef;
import com.abchina.llmalf.agentgate.logic.RunLogic;
import com.abchina.llmalf.agentgate.logic.StorageModels;
import com.abchina.llmalf.agentgate.logic.TaskLogic;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * EvaluationTask 查询、团队可见性与保存规则编排.
 */
@Service
public class EvaluationTaskService {

    private final TaskLogic taskLogic;
    private final RunLogic runLogic;

    public EvaluationTaskService(TaskLogic taskLogic, RunLogic runLogic) {
        this.taskLogic = taskLogic;
        this.runLogic = runLogic;
    }

    public List<EvaluationTask> listVisibleTasks(String teamId) {
        List<EvaluationTask> visible = new ArrayList<>();
        for (EvaluationTask task : taskLogic.listEvaluationTasks()) {
            if (allRunsVisible(task, teamId)) {
                visible.add(task);
            }
        }
        return visible;
    }

    public EvaluationTask getVisibleTask(String taskId, String teamId) {
        EvaluationTask task = taskLogic.getEvaluationTask(taskId);
        if (task == null || !allRunsVisible(task, teamId)) {
            throw new AgentException(404, "unknown evaluation task");
        }
        return task;
    }

    public EvaluationTask saveTask(EvaluationTask task, String teamId) {
        List<EvaluationRun> runs = new ArrayList<>();
        for (String runId : task.runIds()) {
            EvaluationRun run = runLogic.getRun(runId, teamId);
            if (run == null) {
                throw new AgentException(404, "task references an unknown run");
            }
            runs.add(run);
        }
        if (task.kind() == EvaluationTaskKind.STABILITY) {
            validateStabilityRuns(runs);
        }
        if (task.kind() == EvaluationTaskKind.AB && runs.size() == 2) {
            validateAbPair(runs.get(0), runs.get(1));
        }
        try {
            return taskLogic.saveTask(task);
        } catch (IllegalArgumentException e) {
            throw mapStorageError(e);
        }
    }

    private static void validateStabilityRuns(List<EvaluationRun> runs) {
        for (EvaluationRun run : runs) {
            if (!StorageModels.samePayload(runs.get(0).manifest().toPayload(),
                    run.manifest().toPayload())) {
                throw new AgentException(409,
                        "stability requires identical run manifests");
            }
        }
    }

    private static void validateAbPair(EvaluationRun left, EvaluationRun right) {
        TargetRef a = left.manifest().target().ref();
        TargetRef b = right.manifest().target().ref();
        if (!a.sourceId().equals(b.sourceId()) || a.targetType() != b.targetType()
                || !a.externalTargetId().equals(b.externalTargetId())) {
            throw new AgentException(409, "A/B must evaluate the same target");
        }
        if (a.externalVersionId().equals(b.externalVersionId())) {
            throw new AgentException(409, "A/B requires distinct target versions");
        }
        String[] fields = {"dataset", "selected_case_ids", "evaluator_specs",
                "primary_evaluator_ids", "metric_plan", "gate_spec", "timeout_seconds",
                "max_retries", "max_parallel_cases"};
        for (String field : fields) {
            if (!StorageModels.samePayload(manifestField(left, field),
                    manifestField(right, field))) {
                throw new AgentException(409, "A/B conditions differ: " + field);
            }
        }
    }

    private static Object manifestField(EvaluationRun run, String field) {
        Map<?, ?> payload = (Map<?, ?>) run.manifest().toPayload();
        return payload.get(field);
    }

    private boolean allRunsVisible(EvaluationTask task, String teamId) {
        for (String runId : task.runIds()) {
            if (runLogic.getRun(runId, teamId) == null) {
                return false;
            }
        }
        return true;
    }

    private static AgentException mapStorageError(IllegalArgumentException error) {
        String message = error.getMessage() == null ? "" : error.getMessage();
        if (message.contains("unknown static report")) {
            return new AgentException(404, "task references an unknown static report");
        }
        if (message.contains("unknown EvaluationRun")) {
            return new AgentException(404, "task references an unknown run");
        }
        return new AgentException(409, message);
    }
}
