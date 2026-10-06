package com.abchina.llmalf.agentgate.controller;

import com.abchina.llmalf.agentgate.common.AgentException;
import com.abchina.llmalf.agentgate.common.PydanticErrors;
import com.abchina.llmalf.agentgate.common.ApiErrors;
import com.abchina.llmalf.agentgate.common.UserContextHolder;
import com.abchina.llmalf.agentgate.common.ResponseBase;
import com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun;
import com.abchina.llmalf.agentgate.domain.model.run.RunStatus;
import com.abchina.llmalf.agentgate.service.impl.RunReaderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Run 读取端点.
 *
 * <p>对齐 Python server/routes/runs.py 的只读面:
 * 列表/活动/状态/清单;提交与取消归 BJS 适配切片。</p>
 */
@RestController
public class RunController {

    private final RunReaderService runReaderService;
    private final com.abchina.llmalf.agentgate.service.impl.RunLaunchService runLaunchService;
    private final com.abchina.llmalf.agentgate.logic.RunLogic runLogicProxy;
    private final com.abchina.llmalf.agentgate.logic.TaskLogic taskLogicProxy;
    private final long staleGraceSeconds;

    public RunController(RunReaderService runReaderService,
            com.abchina.llmalf.agentgate.service.impl.RunLaunchService runLaunchService,
            com.abchina.llmalf.agentgate.logic.RunLogic runLogicProxy,
            com.abchina.llmalf.agentgate.logic.TaskLogic taskLogicProxy,
            @org.springframework.beans.factory.annotation.Value(
                    "${agentgate.scheduling.stale-grace-seconds:30}") long staleGraceSeconds) {
        this.runReaderService = runReaderService;
        this.runLaunchService = runLaunchService;
        this.runLogicProxy = runLogicProxy;
        this.taskLogicProxy = taskLogicProxy;
        this.staleGraceSeconds = staleGraceSeconds;
    }

    /**
     * Run 列表.
     *
     * @param status 状态过滤(可空)
     * @param limit 上限(1-200,默认 50)
     * @return Run payload 列表
     */
    @GetMapping("/api/runs")
    public ResponseBase<List<Object>> listRuns(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", required = false) String limit) {
        PydanticErrors errors = new PydanticErrors();
        RunStatus statusFilter = null;
        if (status != null) {
            try {
                statusFilter = RunStatus.fromWireValue(status);
            } catch (IllegalArgumentException e) {
                errors.custom("enum", "query", "status",
                        "Input should be 'scheduled', 'pending', 'waiting', 'running', "
                                + "'completed', 'failed' or 'cancelled'",
                        status, java.util.Collections.singletonMap("expected",
                                "'scheduled', 'pending', 'waiting', 'running', "
                                        + "'completed', 'failed' or 'cancelled'"));
            }
        }
        int limitValue = 50;
        if (limit != null) {
            Integer parsed = parseIntParam(errors, "limit", limit);
            if (parsed != null) {
                if (parsed < 1) {
                    errors.greaterEqual("query", "limit", limit, 1);
                } else if (parsed > 200) {
                    errors.lessEqual("query", "limit", limit, 200);
                }
                limitValue = parsed;
            }
        }
        errors.throwIfAny();
        List<Object> payloads = new ArrayList<>();
        for (EvaluationRun run : runReaderService.listRuns(limitValue, statusFilter)) {
            payloads.add(run.toPayload());
        }
        return ResponseBase.success(payloads);
    }

    /**
     * Run 活动视图(先执行超时恢复).
     *
     * @param recentLimit 最近终态上限(1-100,默认 20)
     * @return 活动投影
     */
    @GetMapping("/api/runs/activity")
    public ResponseBase<Map<String, Object>> runActivity(
            @RequestParam(value = "recent_limit", required = false) String recentLimit) {
        PydanticErrors errors = new PydanticErrors();
        int recentValue = 20;
        if (recentLimit != null) {
            Integer parsed = parseIntParam(errors, "recent_limit", recentLimit);
            if (parsed != null) {
                if (parsed < 1) {
                    errors.greaterEqual("query", "recent_limit", recentLimit, 1);
                } else if (parsed > 100) {
                    errors.lessEqual("query", "recent_limit", recentLimit, 100);
                }
                recentValue = parsed;
            }
        }
        errors.throwIfAny();
        runReaderService.failStaleRuns(staleGraceSeconds);
        return ResponseBase.success(runReaderService.activity(recentValue));
    }

    /**
     * Run 状态进度.
     *
     * @param runId Run id
     * @return 进度投影
     */
    @GetMapping("/api/runs/{runId}/status")
    public ResponseBase<Map<String, Object>> runStatus(@PathVariable("runId") String runId) {
        runReaderService.failStaleRuns(staleGraceSeconds);
        Map<String, Object> result = ApiErrors.notFound(
                () -> runReaderService.getRunProgress(runId));
        return ResponseBase.success(result);
    }

    /**
     * Run 清单.
     *
     * @param runId Run id
     * @return 清单 payload
     */
    @GetMapping("/api/runs/{runId}/manifest")
    public ResponseBase<Object> runManifest(@PathVariable("runId") String runId) {
        Object result = ApiErrors.notFound(() -> runReaderService.getRunManifest(runId));
        return ResponseBase.success(result);
    }

    /**
     * 启动 demo 评测(202).
     *
     * @param request 启动请求
     * @return Run 进度投影
     */
    @org.springframework.web.bind.annotation.PostMapping("/api/evaluations")
    public ResponseEntity<ResponseBase<Map<String, Object>>> launchEvaluation(
            @RequestBody Map<String, Object> request) {
        PydanticErrors errors = new PydanticErrors();
        String version = requireString(errors, request, "version");
        String datasetId = requireString(errors, request, "dataset_id");
        Integer datasetVersion = requireInt(errors, request, "dataset_version");
        if (datasetVersion != null && datasetVersion < 1) {
            errors.greaterEqual("body", "dataset_version",
                    request.get("dataset_version"), 1);
        }
        Double timeoutSeconds = optionalDouble(errors, request, "timeout_seconds");
        if (timeoutSeconds != null) {
            if (timeoutSeconds <= 0) {
                errors.greaterThan("body", "timeout_seconds",
                        request.get("timeout_seconds"), 0);
            } else if (timeoutSeconds > 3600) {
                errors.lessEqual("body", "timeout_seconds",
                        request.get("timeout_seconds"), 3600);
            }
        }
        Integer maxParallelCases = optionalInt(errors, request, "max_parallel_cases");
        if (maxParallelCases != null) {
            if (maxParallelCases < 1) {
                errors.greaterEqual("body", "max_parallel_cases",
                        request.get("max_parallel_cases"), 1);
            } else if (maxParallelCases > 32) {
                errors.lessEqual("body", "max_parallel_cases",
                        request.get("max_parallel_cases"), 32);
            }
        }
        Integer maxRetries = optionalInt(errors, request, "max_retries");
        if (maxRetries != null && (maxRetries < 0 || maxRetries > 5)) {
            if (maxRetries < 0) {
                errors.greaterEqual("body", "max_retries",
                        request.get("max_retries"), 0);
            } else {
                errors.lessEqual("body", "max_retries",
                        request.get("max_retries"), 5);
            }
        }
        errors.throwIfAny();
        com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun run;
        try {
            run = runLaunchService.submitDemoRun(
                    version,
                    datasetId,
                    datasetVersion,
                    stringListOrNull(request.get("case_ids")),
                    stringListOrNull(request.get("evaluator_ids")),
                    timeoutSeconds == null ? 300 : timeoutSeconds,
                    maxParallelCases == null ? 1 : maxParallelCases,
                    maxRetries == null ? 0 : maxRetries,
                    timeOrNull(request.get("scheduled_for")),
                    textOrNull(request.get("api_key")));
        } catch (AgentException e) {
            throw e;
        } catch (IllegalStateException e) {
            throw new AgentException(503, "Evaluation dispatch service is unavailable");
        } catch (IllegalArgumentException e) {
            throw new AgentException(422, e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ResponseBase.success(
                        ApiErrors.unprocessable(() -> runReaderService.getRunProgress(run.id()))));
    }

    /**
     * 技能分析(LLM 通道不可用,按决策返回 503 + TODO).
     *
     * @param request 请求体
     * @return 503
     */
    @org.springframework.web.bind.annotation.PostMapping("/api/evaluations/skill-analysis")
    public ResponseEntity<ResponseBase<Object>> analyzeEvaluationTarget(
            @RequestBody Map<String, Object> request) {
        throw new AgentException(503, "Skill analysis is unavailable");
    }

    /**
     * 启动 A/B 对照(202).
     *
     * @param request 对照请求
     * @return 双 Run 变体
     */
    @org.springframework.web.bind.annotation.PostMapping("/api/run-comparisons")
    public ResponseEntity<ResponseBase<Map<String, Object>>> launchRunComparison(
            @RequestBody Map<String, Object> request) {
        PydanticErrors errors = new PydanticErrors();
        rejectExtraKeys(errors, request, "baseline_version", "candidate_version",
                "dataset_id", "dataset_version", "evaluators");
        String baselineVersion = requireNonBlankString(errors, request,
                "baseline_version");
        String candidateVersion = requireNonBlankString(errors, request,
                "candidate_version");
        String datasetId = requireNonBlankString(errors, request, "dataset_id");
        Integer datasetVersion = requireInt(errors, request, "dataset_version");
        if (datasetVersion != null && datasetVersion < 1) {
            errors.greaterEqual("body", "dataset_version",
                    request.get("dataset_version"), 1);
        }
        List<com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorRef> refs = null;
        Object rawRefs = request.get("evaluators");
        if (rawRefs instanceof List) {
            refs = new ArrayList<>();
            int index = 0;
            for (Object item : (List<?>) rawRefs) {
                if (!(item instanceof Map)) {
                    errors.custom("model_type", "body", "evaluators",
                            "Input should be a valid dictionary or object to extract "
                                    + "fields from", item, null);
                    index++;
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> ref = (Map<String, Object>) item;
                rejectExtraKeys(errors, ref, "id", "version");
                String id = requireNonBlankString(errors, ref, "id");
                String evalVersion = requireNonBlankString(errors, ref, "version");
                if (id != null && evalVersion != null) {
                    refs.add(com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorRef
                            .of(id, evalVersion, null));
                }
                index++;
            }
        } else if (rawRefs != null) {
            errors.custom("list_type", "body", "evaluators", "Input should be a valid list",
                    rawRefs, null);
        }
        errors.throwIfAny();
        com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun[] pair;
        try {
            pair = runLaunchService.submitAbRuns(baselineVersion, candidateVersion,
                    datasetId, datasetVersion, refs);
        } catch (AgentException e) {
            throw e;
        } catch (IllegalStateException e) {
            throw new AgentException(503, "Evaluation dispatch service is unavailable");
        } catch (IllegalArgumentException e) {
            throw new AgentException(422, e.getMessage());
        }
        Map<String, Object> submission = new LinkedHashMap<>();
        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("run_id", pair[0].id());
        baseline.put("status", pair[0].status().wireValue());
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("run_id", pair[1].id());
        candidate.put("status", pair[1].status().wireValue());
        submission.put("baseline", baseline);
        submission.put("candidate", candidate);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseBase.success(submission));
    }

    /**
     * 启动稳定性实验(202).
     *
     * @param request 稳定性请求
     * @return Run 列表
     */
    @org.springframework.web.bind.annotation.PostMapping("/api/stability-experiments")
    public ResponseEntity<ResponseBase<Object>> launchStability(
            @RequestBody Map<String, Object> request) {
        PydanticErrors errors = new PydanticErrors();
        rejectExtraKeys(errors, request, "version", "dataset_id", "dataset_version",
                "evaluator_ids", "timeout_seconds", "max_parallel_cases", "max_retries",
                "case_ids", "scheduled_for", "api_key", "repetitions");
        String version = requireString(errors, request, "version");
        String datasetId = requireString(errors, request, "dataset_id");
        Integer datasetVersion = requireInt(errors, request, "dataset_version");
        if (datasetVersion != null && datasetVersion < 1) {
            errors.greaterEqual("body", "dataset_version",
                    request.get("dataset_version"), 1);
        }
        Double timeoutSeconds = optionalDouble(errors, request, "timeout_seconds");
        if (timeoutSeconds != null) {
            if (timeoutSeconds <= 0) {
                errors.greaterThan("body", "timeout_seconds",
                        request.get("timeout_seconds"), 0);
            } else if (timeoutSeconds > 3600) {
                errors.lessEqual("body", "timeout_seconds",
                        request.get("timeout_seconds"), 3600);
            }
        }
        Integer maxParallelCases = optionalInt(errors, request, "max_parallel_cases");
        if (maxParallelCases != null) {
            if (maxParallelCases < 1) {
                errors.greaterEqual("body", "max_parallel_cases",
                        request.get("max_parallel_cases"), 1);
            } else if (maxParallelCases > 32) {
                errors.lessEqual("body", "max_parallel_cases",
                        request.get("max_parallel_cases"), 32);
            }
        }
        Integer maxRetries = optionalInt(errors, request, "max_retries");
        if (maxRetries != null && (maxRetries < 0 || maxRetries > 5)) {
            if (maxRetries < 0) {
                errors.greaterEqual("body", "max_retries",
                        request.get("max_retries"), 0);
            } else {
                errors.lessEqual("body", "max_retries",
                        request.get("max_retries"), 5);
            }
        }
        Object rawRepetitions = request.get("repetitions");
        Integer repetitions = null;
        if (rawRepetitions == null) {
            errors.missing("body", "repetitions", request);
        } else if (!(rawRepetitions instanceof Integer)
                || rawRepetitions instanceof Boolean) {
            errors.intType("body", "repetitions", rawRepetitions);
        } else {
            repetitions = (Integer) rawRepetitions;
            if (repetitions < 2) {
                errors.greaterEqual("body", "repetitions", rawRepetitions, 2);
            } else if (repetitions > 20) {
                errors.lessEqual("body", "repetitions", rawRepetitions, 20);
            }
        }
        errors.throwIfAny();
        if (request.get("scheduled_for") != null) {
            throw new AgentException(422, "stability does not support scheduling");
        }
        Object taskPayload;
        try {
            taskPayload = runLaunchService.submitStabilityRuns(
                    version, repetitions, datasetId, datasetVersion,
                    stringListOrNull(request.get("case_ids")),
                    stringListOrNull(request.get("evaluator_ids")),
                    timeoutSeconds == null ? 300 : timeoutSeconds,
                    maxParallelCases == null ? 1 : maxParallelCases,
                    maxRetries == null ? 0 : maxRetries,
                    textOrNull(request.get("api_key"))).toPayload();
        } catch (AgentException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new AgentException(422, e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ResponseBase.success(taskPayload));
    }

    /**
     * 取消 Run(非终态).
     *
     * @param runId Run id
     * @return 取消后进度
     */
    @org.springframework.web.bind.annotation.PostMapping("/api/runs/{runId}/cancel")
    public ResponseBase<Map<String, Object>> cancelRun(@PathVariable("runId") String runId) {
        runLaunchService.cancelRun(runId);
        return ResponseBase.success(runReaderService.getRunProgress(runId));
    }

    /**
     * 重跑 Run(以终态源清单创建新 Run 并派发,202).
     *
     * @param runId 源 Run id
     * @return 新 Run 进度
     */
    @org.springframework.web.bind.annotation.PostMapping("/api/runs/{runId}/rerun")
    public ResponseEntity<ResponseBase<Map<String, Object>>> rerunRun(
            @PathVariable("runId") String runId) {
        Map<String, Object> result = ApiErrors.notFound(() -> {
            com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun source =
                    runLogicProxy.getRun(runId, UserContextHolder.current().userTeamId());
            if (source == null) {
                throw new AgentException(404, "unknown EvaluationRun: " + runId);
            }
            if (source.status() == com.abchina.llmalf.agentgate.domain.model.run.RunStatus.SCHEDULED
                    || source.status() == com.abchina.llmalf.agentgate.domain.model.run.RunStatus.PENDING
                    || source.status() == com.abchina.llmalf.agentgate.domain.model.run.RunStatus.WAITING
                    || source.status() == com.abchina.llmalf.agentgate.domain.model.run.RunStatus.RUNNING) {
                throw new AgentException(409, "cannot rerun "
                        + source.status().wireValue() + " EvaluationRun");
            }
            String newRunId = java.util.UUID.randomUUID().toString();
            com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun rerun =
                    com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun.of(newRunId,
                            source.manifest(),
                            com.abchina.llmalf.agentgate.domain.model.run.RunLifecycle.of(
                                    com.abchina.llmalf.agentgate.domain.model.run.RunStatus.PENDING,
                                    com.abchina.llmalf.agentgate.domain.DomainValidations.utcNow(),
                                    null, null, null, null),
                            source.userTeamId(), source.userId(), source.userName(),
                            source.apiKey(), 0);
            taskLogicProxy.saveTaskRuns(
                    com.abchina.llmalf.agentgate.domain.model.evaluationtask.EvaluationTask.of(
                            newRunId,
                            com.abchina.llmalf.agentgate.domain.model.evaluationtask.EvaluationTaskKind.SINGLE,
                            null, Collections.singletonList(newRunId), null, null, null),
                    Collections.singletonList(rerun));
            runLaunchService.dispatchRun(newRunId);
            return runReaderService.getRunProgress(newRunId);
        });
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseBase.success(result));
    }

    private static Integer parseIntParam(PydanticErrors errors, String field, String raw) {
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            errors.intParsing("query", field, raw);
            return null;
        }
    }

    private static String requireString(PydanticErrors errors, Map<String, Object> body,
            String field) {
        if (!body.containsKey(field)) {
            errors.missing("body", field, body);
            return null;
        }
        Object raw = body.get(field);
        if (raw == null || raw instanceof Boolean || raw instanceof Number
                || raw instanceof List || raw instanceof Map) {
            errors.stringType("body", field, raw);
            return null;
        }
        return String.valueOf(raw);
    }

    private static String requireNonBlankString(PydanticErrors errors,
            Map<String, Object> body, String field) {
        String value = requireString(errors, body, field);
        if (value != null && value.isEmpty()) {
            errors.stringTooShort("body", field, value, 1);
            return null;
        }
        return value;
    }

    private static Integer requireInt(PydanticErrors errors, Map<String, Object> body,
            String field) {
        if (!body.containsKey(field)) {
            errors.missing("body", field, body);
            return null;
        }
        return optionalInt(errors, body, field);
    }

    /**
     * pydantic lax 整型转换:数字字符串可解析、bool→0/1、整值浮点收窄;
     * 小数浮点 → int_from_float 专用消息.
     */
    private static Integer optionalInt(PydanticErrors errors, Map<String, Object> body,
            String field) {
        if (!body.containsKey(field)) {
            return null;
        }
        Object raw = body.get(field);
        if (raw instanceof Integer || raw instanceof Long || raw instanceof Short
                || raw instanceof Byte) {
            return ((Number) raw).intValue();
        }
        if (raw instanceof Boolean) {
            return (Boolean) raw ? 1 : 0;
        }
        if (raw instanceof Double || raw instanceof Float) {
            double value = ((Number) raw).doubleValue();
            if (value != Math.floor(value) || Double.isInfinite(value)) {
                errors.custom("int_from_float", "body", field,
                        "Input should be a valid integer, "
                                + "got a number with a fractional part", raw, null);
                return null;
            }
            return (int) value;
        }
        if (raw instanceof String) {
            try {
                return Integer.valueOf(((String) raw).trim());
            } catch (NumberFormatException e) {
                errors.intParsing("body", field, raw);
                return null;
            }
        }
        errors.intType("body", field, raw);
        return null;
    }

    private static Double optionalDouble(PydanticErrors errors, Map<String, Object> body,
            String field) {
        if (!body.containsKey(field)) {
            return null;
        }
        Object raw = body.get(field);
        if (raw instanceof Number) {
            return ((Number) raw).doubleValue();
        }
        if (raw instanceof Boolean) {
            return (Boolean) raw ? 1.0 : 0.0;
        }
        if (raw instanceof String) {
            try {
                return Double.valueOf(((String) raw).trim());
            } catch (NumberFormatException e) {
                errors.custom("float_parsing", "body", field,
                        "Input should be a valid number, "
                                + "unable to parse string as a number", raw, null);
                return null;
            }
        }
        errors.custom("float_type", "body", field, "Input should be a valid number",
                raw, null);
        return null;
    }

    private static void rejectExtraKeys(PydanticErrors errors, Map<String, Object> body,
            String... allowed) {
        Set<String> allowedKeys = new HashSet<>(Arrays.asList(allowed));
        for (String key : body.keySet()) {
            if (!allowedKeys.contains(key)) {
                errors.extraForbidden("body", key, body.get(key));
            }
        }
    }

    private static String textOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static java.time.OffsetDateTime timeOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        try {
            return java.time.OffsetDateTime.parse(text);
        } catch (java.time.format.DateTimeParseException offsetError) {
            try {
                // date-only → UTC 午夜(pydantic datetime lax 语义)
                return java.time.LocalDate.parse(text)
                        .atStartOfDay(java.time.ZoneOffset.UTC)
                        .toOffsetDateTime();
            } catch (java.time.format.DateTimeParseException dateError) {
                throw new AgentException(422, "Input should be a valid datetime");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringListOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof List)) {
            throw new AgentException(422, "Input should be a valid list");
        }
        List<?> items = (List<?>) value;
        for (Object item : items) {
            if (item != null && !(item instanceof String)) {
                throw new AgentException(422, "Input should be a valid string");
            }
        }
        return (List<String>) items;
    }
}
