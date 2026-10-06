package com.abchina.llmalf.agentgate.controller;

import com.abchina.llmalf.agentgate.common.AgentException;
import com.abchina.llmalf.agentgate.common.PydanticErrors;
import com.abchina.llmalf.agentgate.common.ResponseBase;
import com.abchina.llmalf.agentgate.domain.model.evaluationtask.EvaluationTask;
import com.abchina.llmalf.agentgate.domain.model.evaluationtask.EvaluationTaskKind;
import com.abchina.llmalf.agentgate.logic.RunLogic;
import com.abchina.llmalf.agentgate.logic.TaskLogic;
import com.abchina.llmalf.agentgate.common.UserContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 评测任务关联端点.
 *
 * <p>对齐 Python server/routes/evaluation_tasks.py:
 * 列表/查询(404)/保存(409);A/B 与 stability 的清单约束。</p>
 */
@RestController
@RequestMapping("/api/evaluation-tasks")
public class EvaluationTaskController {

    private final TaskLogic taskLogic;
    private final RunLogic runLogic;

    public EvaluationTaskController(TaskLogic taskLogic, RunLogic runLogic) {
        this.taskLogic = taskLogic;
        this.runLogic = runLogic;
    }

    /**
     * 任务列表(团队可见过滤).
     *
     * @return 任务 payload 列表
     */
    @GetMapping
    public ResponseBase<List<Object>> listTasks() {
        String teamId = UserContextHolder.current().userTeamId();
        List<Object> payloads = new ArrayList<>();
        for (EvaluationTask task : taskLogic.listEvaluationTasks()) {
            if (allRunsVisible(task, teamId)) {
                payloads.add(task.toPayload());
            }
        }
        return ResponseBase.success(payloads);
    }

    /**
     * 任务详情.
     *
     * @param taskId 任务 id
     * @return 任务 payload
     */
    @GetMapping("/{taskId}")
    public ResponseBase<Object> getTask(@PathVariable("taskId") String taskId) {
        EvaluationTask task = taskLogic.getEvaluationTask(taskId);
        if (task == null || !allRunsVisible(task, UserContextHolder.current().userTeamId())) {
            throw new AgentException(404, "unknown evaluation task");
        }
        return ResponseBase.success(task.toPayload());
    }

    /**
     * 保存任务关联(409 冲突语义).
     *
     * @param taskId 任务 id
     * @param body 请求体(kind/run_ids/static_report_ids)
     * @return 任务 payload
     */
    @PutMapping("/{taskId}")
    public ResponseBase<Object> saveTask(@PathVariable("taskId") String taskId,
            @RequestBody Map<String, Object> body) {
        String teamId = UserContextHolder.current().userTeamId();
        PydanticErrors errors = new PydanticErrors();
        for (String key : body.keySet()) {
            if (!"kind".equals(key) && !"run_ids".equals(key)
                    && !"static_report_ids".equals(key)) {
                errors.extraForbidden("body", key, body.get(key));
            }
        }
        if (!body.containsKey("kind")) {
            errors.missing("body", "kind", body);
        } else {
            Object rawKind = body.get("kind");
            if (rawKind == null || !(rawKind instanceof String)) {
                errors.stringType("body", "kind", rawKind);
            } else {
                try {
                    EvaluationTaskKind.fromWireValue(String.valueOf(rawKind));
                } catch (IllegalArgumentException e) {
                    errors.custom("enum", "body", "kind",
                            "Input should be 'single', 'ab' or 'stability'",
                            rawKind, java.util.Collections.singletonMap("expected",
                                    "'single', 'ab' or 'stability'"));
                }
            }
        }
        List<String> runIds = stringListField(errors, body, "run_ids");
        List<String> staticReportIds = stringListField(errors, body,
                "static_report_ids");
        if (runIds == null) {
            runIds = new ArrayList<>();
        }
        if (staticReportIds == null) {
            staticReportIds = new ArrayList<>();
        }
        errors.throwIfAny();
        EvaluationTaskKind taskKind = EvaluationTaskKind.fromWireValue(
                String.valueOf(body.get("kind")));
        // 域基数校验优先于 run 存在性(对齐 Python 模型校验先行的 409 语义)
        EvaluationTask task;
        try {
            task = EvaluationTask.of(taskId, taskKind, null, runIds,
                    staticReportIds, null, null);
        } catch (IllegalArgumentException e) {
            throw new AgentException(409,
                    pydanticTaskError(e.getMessage(), taskId, taskKind, runIds,
                            staticReportIds));
        }
        List<com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun> runs =
                new ArrayList<>();
        for (String runId : runIds) {
            com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun run =
                    runLogic.getRun(runId, teamId);
            if (run == null) {
                throw new AgentException(404, "task references an unknown run");
            }
            runs.add(run);
        }
        if (taskKind == EvaluationTaskKind.STABILITY) {
            for (com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun run : runs) {
                if (!com.abchina.llmalf.agentgate.logic.StorageModels.samePayload(
                        runs.get(0).manifest().toPayload(),
                        run.manifest().toPayload())) {
                    throw new AgentException(409,
                            "stability requires identical run manifests");
                }
            }
        }
        if (taskKind == EvaluationTaskKind.AB && runs.size() == 2) {
            validateAbPair(runs.get(0), runs.get(1));
        }
        EvaluationTask saved;
        try {
            saved = taskLogic.saveTask(task);
        } catch (IllegalArgumentException e) {
            String m = e.getMessage() == null ? "" : e.getMessage();
            if (m.contains("immutable")) {
                throw new AgentException(409, m);
            }
            if (m.contains("unknown static report")) {
                throw new AgentException(404,
                        "task references an unknown static report");
            }
            if (m.contains("unknown EvaluationRun")) {
                throw new AgentException(404, "task references an unknown run");
            }
            // 其余域校验冲突统一 409(对齐 Python ValueError 分支)
            throw new AgentException(409, m);
        }
        return ResponseBase.success(saved.toPayload());
    }

    /**
     * 复刻 pydantic ValidationError 文本(model_validator 基数错误与
     * field_validator 引用错误两种形态).
     */
    private static String pydanticTaskError(String message, String taskId,
            EvaluationTaskKind kind, List<String> runIds, List<String> staticReportIds) {
        String suffix = "\n    For further information visit "
                + "https://errors.pydantic.dev/2.13/v/value_error";
        if ("references must not be blank".equals(message)
                || "references must be unique".equals(message)) {
            String field = staticReportIds != null && staticReportIds.isEmpty()
                    && runIds != null && runIds.isEmpty() ? "static_report_ids" : "run_ids";
            List<String> values = "run_ids".equals(field) ? runIds : staticReportIds;
            return "1 validation error for EvaluationTask\n  " + field
                    + "\n    Value error, " + message + " [type=value_error, input_value="
                    + pythonListRepr(values) + ", input_type=list]" + suffix;
        }
        String inputRepr = "{'id': '" + taskId + "', 'kind': "
                + "<EvaluationTaskKind." + kind.name() + ": '" + kind.wireValue()
                + "'>, 'run_ids': " + pythonTupleRepr(runIds)
                + ", 'static_report_ids': " + pythonTupleRepr(staticReportIds)
                + "}";
        return "1 validation error for EvaluationTask\n  Value error, " + message
                + " [type=value_error, input_value=" + truncateRepr(inputRepr)
                + ", input_type=dict]" + suffix;
    }

    /**
     * 复刻 pydantic-core 展示截断:repr 长度 >= 60 时取头 25 + "..." + 尾 24.
     */
    private static String truncateRepr(String repr) {
        if (repr.length() < 60) {
            return repr;
        }
        return repr.substring(0, 25) + "..." + repr.substring(repr.length() - 24);
    }

    private static String pythonListRepr(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        StringBuilder repr = new StringBuilder("[");
        for (String value : values) {
            if (repr.length() > 1) {
                repr.append(", ");
            }
            repr.append('\'').append(value).append('\'');
        }
        return repr.append(']').toString();
    }

    private static String pythonTupleRepr(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "()";
        }
        StringBuilder repr = new StringBuilder("(");
        for (String value : values) {
            if (repr.length() > 1) {
                repr.append(", ");
            }
            repr.append('\'').append(value).append('\'');
        }
        return repr.append(values.size() == 1 ? ",)" : ")").toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringListField(PydanticErrors errors,
            Map<String, Object> body, String field) {
        Object raw = body.get(field);
        if (raw == null) {
            if ("run_ids".equals(field)) {
                errors.missing("body", field, body);
            }
            return null;
        }
        if (!(raw instanceof List)) {
            errors.custom("list_type", "body", field, "Input should be a valid list",
                    raw, null);
            return null;
        }
        List<String> values = new ArrayList<>();
        for (Object item : (List<?>) raw) {
            if (item != null && !(item instanceof String)) {
                errors.stringType("body", field, item);
            } else {
                values.add((String) item);
            }
        }
        return values;
    }

    private static void validateAbPair(
            com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun left,
            com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun right) {
        com.abchina.llmalf.agentgate.domain.model.target.TargetRef a = left.manifest()
                .target().ref();
        com.abchina.llmalf.agentgate.domain.model.target.TargetRef b = right.manifest()
                .target().ref();
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
            Object leftValue = manifestField(left, field);
            Object rightValue = manifestField(right, field);
            if (!com.abchina.llmalf.agentgate.logic.StorageModels.samePayload(leftValue,
                    rightValue)) {
                throw new AgentException(409, "A/B conditions differ: " + field);
            }
        }
    }

    private static Object manifestField(
            com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun run, String field) {
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
}
