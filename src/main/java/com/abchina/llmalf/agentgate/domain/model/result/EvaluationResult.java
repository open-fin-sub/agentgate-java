package com.abchina.llmalf.agentgate.domain.model.result;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.PayloadValues;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorKind;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSeverity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 评测器对单个用例执行的持久化结论.
 *
 * <p>对齐 Python domain/result.py::EvaluationResult:
 * 20 字段与 10 条跨 check 规则(唯一性/分数/check 构成/失败定位/
 * error_detail 与 judge_record 归属)。</p>
 */
public final class EvaluationResult {

    private static final String TRACE_ID_PATTERN = "[0-9a-f]{32}";

    private final String id;
    private final String runId;
    private final String caseId;
    private final String traceId;
    private final String evaluatorId;
    private final String evaluatorName;
    private final String evaluatorVersion;
    private final String evaluatorContentSha256;
    private final EvaluatorKind evaluatorKind;
    private final String dimension;
    private final String metric;
    private final EvaluatorSeverity severity;
    private final Outcome outcome;
    private final Double score;
    private final String reason;
    private final List<CheckResult> checks;
    private final JudgeRecord judgeRecord;
    private final EvaluatorErrorDetail errorDetail;
    private final FailureStage primaryFailureStage;

    private EvaluationResult(String id, String runId, String caseId, String traceId,
            String evaluatorId, String evaluatorName, String evaluatorVersion,
            String evaluatorContentSha256, EvaluatorKind evaluatorKind, String dimension,
            String metric, EvaluatorSeverity severity, Outcome outcome, Double score,
            String reason, List<CheckResult> checks, JudgeRecord judgeRecord,
            EvaluatorErrorDetail errorDetail, FailureStage primaryFailureStage) {
        this.id = id;
        this.runId = runId;
        this.caseId = caseId;
        this.traceId = traceId;
        this.evaluatorId = evaluatorId;
        this.evaluatorName = evaluatorName;
        this.evaluatorVersion = evaluatorVersion;
        this.evaluatorContentSha256 = evaluatorContentSha256;
        this.evaluatorKind = evaluatorKind;
        this.dimension = dimension;
        this.metric = metric;
        this.severity = severity;
        this.outcome = outcome;
        this.score = score;
        this.reason = reason;
        this.checks = checks;
        this.judgeRecord = judgeRecord;
        this.errorDetail = errorDetail;
        this.primaryFailureStage = primaryFailureStage;
    }

    /**
     * 构造评测结论.
     *
     * @param id 结论 id
     * @param runId Run id
     * @param caseId 用例 id
     * @param traceId Trace id(32 位小写十六进制)
     * @param evaluatorId 评测器 id
     * @param evaluatorName 评测器名
     * @param evaluatorVersion 评测器版本
     * @param evaluatorContentSha256 评测器定义摘要
     * @param evaluatorKind 评测器类型
     * @param dimension 评估维度
     * @param metric 指标名
     * @param severity 严重级
     * @param outcome 结论
     * @param score 分数 [0,1]
     * @param reason 原因
     * @param checks 检查列表
     * @param judgeRecord LLM 审判记录(仅 LLM_JUDGE)
     * @param errorDetail 错误详情(仅 ERROR)
     * @param primaryFailureStage 主失败阶段(仅 FAIL)
     * @return 结论实例
     */
    public static EvaluationResult of(String id, String runId, String caseId, String traceId,
            String evaluatorId, String evaluatorName, String evaluatorVersion,
            String evaluatorContentSha256, EvaluatorKind evaluatorKind, String dimension,
            String metric, EvaluatorSeverity severity, Outcome outcome, Double score,
            String reason, List<CheckResult> checks, JudgeRecord judgeRecord,
            EvaluatorErrorDetail errorDetail, FailureStage primaryFailureStage) {
        String validId = DomainValidations.requireNonBlank(id, "EvaluationResult id");
        String validRunId = DomainValidations.requireNonBlank(runId, "EvaluationResult run_id");
        String validCaseId = DomainValidations.requireNonBlank(caseId, "EvaluationResult case_id");
        if (traceId == null || !traceId.matches(TRACE_ID_PATTERN)) {
            throw new IllegalArgumentException(
                    "trace_id must be exactly 32 lowercase hexadecimal characters");
        }
        String validEvaluatorId = DomainValidations.requireNonBlank(evaluatorId,
                "EvaluationResult evaluator_id");
        String validEvaluatorName = DomainValidations.requireNonBlank(evaluatorName,
                "EvaluationResult evaluator_name");
        String validEvaluatorVersion = DomainValidations.requireNonBlank(evaluatorVersion,
                "EvaluationResult evaluator_version");
        DomainValidations.requireSha256(evaluatorContentSha256, "evaluator_content_sha256");
        if (evaluatorKind == null) {
            throw new IllegalArgumentException("Field required");
        }
        String validDimension = DomainValidations.requireNonBlank(dimension,
                "EvaluationResult dimension");
        String validMetric = DomainValidations.requireNonBlank(metric, "EvaluationResult metric");
        if (severity == null) {
            throw new IllegalArgumentException("Field required");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("Field required");
        }
        if (score != null) {
            if (score < 0) {
                throw new IllegalArgumentException("Input should be greater than or equal to 0");
            }
            if (score > 1) {
                throw new IllegalArgumentException("Input should be less than or equal to 1");
            }
        }
        String validReason = DomainValidations.requireNonBlank(reason, "EvaluationResult reason");
        List<CheckResult> frozenChecks = checks == null
                ? Collections.<CheckResult>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(checks));

        Set<String> checkIds = new HashSet<>();
        for (CheckResult check : frozenChecks) {
            if (!checkIds.add(check.id())) {
                throw new IllegalArgumentException(
                        "CheckResult ids must be unique within an EvaluationResult");
            }
        }
        if (outcome == Outcome.NOT_APPLICABLE || outcome == Outcome.ERROR) {
            if (score != null) {
                throw new IllegalArgumentException(
                        "not-applicable/error results cannot have a score");
            }
        } else if (score == null) {
            throw new IllegalArgumentException("measured results require a score");
        }

        CheckResult earliestFailed = null;
        int passedCount = 0;
        int reviewedCount = 0;
        int failedCount = 0;
        for (CheckResult check : frozenChecks) {
            if (check.outcome() == Outcome.FAIL) {
                failedCount++;
                if (earliestFailed == null
                        || Integer.valueOf(check.failureSequence() == null
                                ? 0 : check.failureSequence())
                                < Integer.valueOf(earliestFailed.failureSequence() == null
                                        ? 0 : earliestFailed.failureSequence())) {
                    earliestFailed = check;
                }
            } else if (check.outcome() == Outcome.REVIEW) {
                reviewedCount++;
            } else if (check.outcome() == Outcome.PASS) {
                passedCount++;
            }
        }
        if (outcome == Outcome.FAIL) {
            if (failedCount == 0) {
                throw new IllegalArgumentException(
                        "failed results require at least one failed CheckResult");
            }
            if (primaryFailureStage != earliestFailed.failureStage()) {
                throw new IllegalArgumentException(
                        "primary_failure_stage must match the earliest failed check");
            }
        } else if (primaryFailureStage != null) {
            throw new IllegalArgumentException("only failed results may have primary_failure_stage");
        }
        if (outcome == Outcome.PASS && (passedCount == 0 || failedCount > 0 || reviewedCount > 0)) {
            throw new IllegalArgumentException(
                    "passed results require passed checks and no failed/review checks");
        }
        if (outcome == Outcome.REVIEW && (reviewedCount == 0 || failedCount > 0)) {
            throw new IllegalArgumentException(
                    "review results require review checks and no failed checks");
        }
        if (outcome == Outcome.NOT_APPLICABLE) {
            for (CheckResult check : frozenChecks) {
                if (check.outcome() != Outcome.NOT_APPLICABLE) {
                    throw new IllegalArgumentException(
                            "not-applicable results may contain only not-applicable checks");
                }
            }
        }
        if (outcome == Outcome.ERROR) {
            if (errorDetail == null) {
                throw new IllegalArgumentException("error results require error_detail");
            }
        } else if (errorDetail != null) {
            throw new IllegalArgumentException("only error results may carry error_detail");
        }
        if (evaluatorKind != EvaluatorKind.LLM_JUDGE && judgeRecord != null) {
            throw new IllegalArgumentException("only LLM Judge results may carry judge_record");
        }
        if (evaluatorKind == EvaluatorKind.LLM_JUDGE
                && (outcome == Outcome.PASS || outcome == Outcome.FAIL || outcome == Outcome.REVIEW)
                && judgeRecord == null) {
            throw new IllegalArgumentException(
                    "measured LLM Judge results require judge_record");
        }

        return new EvaluationResult(validId, validRunId, validCaseId, traceId, validEvaluatorId,
                validEvaluatorName, validEvaluatorVersion, evaluatorContentSha256, evaluatorKind,
                validDimension, validMetric, severity, outcome, score, validReason, frozenChecks,
                judgeRecord, errorDetail, primaryFailureStage);
    }

    /**
     * 从 payload 解析评测结论.
     *
     * @param payload payload 对象
     * @return 结论实例
     */
    public static EvaluationResult fromPayload(Map<String, Object> payload) {
        List<CheckResult> checks = new ArrayList<>();
        Object rawChecks = payload.get("checks");
        if (rawChecks instanceof List) {
            for (Object item : (List<?>) rawChecks) {
                if (!(item instanceof Map)) {
                    throw new IllegalArgumentException("Input should be a valid dictionary");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> checkPayload = (Map<String, Object>) item;
                checks.add(CheckResult.fromPayload(checkPayload));
            }
        } else if (rawChecks != null) {
            throw new IllegalArgumentException("Input should be a valid list");
        }
        return of(PayloadValues.idOrDefault(payload, "id"),
                PayloadValues.requiredString(payload, "run_id"),
                PayloadValues.requiredString(payload, "case_id"),
                PayloadValues.requiredString(payload, "trace_id"),
                PayloadValues.requiredString(payload, "evaluator_id"),
                PayloadValues.requiredString(payload, "evaluator_name"),
                PayloadValues.requiredString(payload, "evaluator_version"),
                PayloadValues.requiredString(payload, "evaluator_content_sha256"),
                EvaluatorKind.fromWireValue(
                        PayloadValues.requiredString(payload, "evaluator_kind")),
                PayloadValues.requiredString(payload, "dimension"),
                PayloadValues.requiredString(payload, "metric"),
                EvaluatorSeverity.fromWireValue(
                        PayloadValues.requiredString(payload, "severity")),
                Outcome.fromWireValue(PayloadValues.requiredString(payload, "outcome")),
                PayloadValues.optionalDouble(payload, "score"),
                PayloadValues.requiredString(payload, "reason"),
                checks,
                parseJudgeRecord(payload),
                parseErrorDetail(payload),
                parseFailureStage(payload));
    }

    @SuppressWarnings("unchecked")
    private static JudgeRecord parseJudgeRecord(Map<String, Object> payload) {
        Object raw = payload.get("judge_record");
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Map)) {
            throw new IllegalArgumentException("Input should be a valid dictionary");
        }
        return JudgeRecord.fromPayload((Map<String, Object>) raw);
    }

    @SuppressWarnings("unchecked")
    private static EvaluatorErrorDetail parseErrorDetail(Map<String, Object> payload) {
        Object raw = payload.get("error_detail");
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Map)) {
            throw new IllegalArgumentException("Input should be a valid dictionary");
        }
        return EvaluatorErrorDetail.fromPayload((Map<String, Object>) raw);
    }

    private static FailureStage parseFailureStage(Map<String, Object> payload) {
        String value = PayloadValues.optionalString(payload, "primary_failure_stage");
        return value == null ? null : FailureStage.fromWireValue(value);
    }

    public String id() {
        return id;
    }

    public String runId() {
        return runId;
    }

    public String caseId() {
        return caseId;
    }

    public String traceId() {
        return traceId;
    }

    public String evaluatorId() {
        return evaluatorId;
    }

    public String evaluatorName() {
        return evaluatorName;
    }

    public String evaluatorVersion() {
        return evaluatorVersion;
    }

    public String evaluatorContentSha256() {
        return evaluatorContentSha256;
    }

    public EvaluatorKind evaluatorKind() {
        return evaluatorKind;
    }

    public String dimension() {
        return dimension;
    }

    public String metric() {
        return metric;
    }

    public EvaluatorSeverity severity() {
        return severity;
    }

    public Outcome outcome() {
        return outcome;
    }

    public Double score() {
        return score;
    }

    public String reason() {
        return reason;
    }

    public List<CheckResult> checks() {
        return checks;
    }

    public JudgeRecord judgeRecord() {
        return judgeRecord;
    }

    public EvaluatorErrorDetail errorDetail() {
        return errorDetail;
    }

    public FailureStage primaryFailureStage() {
        return primaryFailureStage;
    }

    /**
     * 组装 payload 表示(字段顺序对齐 pydantic 声明序).
     *
     * @return JSON 兼容树
     */
    public Object toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("run_id", runId);
        payload.put("case_id", caseId);
        payload.put("trace_id", traceId);
        payload.put("evaluator_id", evaluatorId);
        payload.put("evaluator_name", evaluatorName);
        payload.put("evaluator_version", evaluatorVersion);
        payload.put("evaluator_content_sha256", evaluatorContentSha256);
        payload.put("evaluator_kind", evaluatorKind.wireValue());
        payload.put("dimension", dimension);
        payload.put("metric", metric);
        payload.put("severity", severity.wireValue());
        payload.put("outcome", outcome.wireValue());
        payload.put("score", score);
        payload.put("reason", reason);
        List<Object> checkPayloads = new ArrayList<>(checks.size());
        for (CheckResult check : checks) {
            checkPayloads.add(check.toPayload());
        }
        payload.put("checks", checkPayloads);
        payload.put("judge_record", judgeRecord == null ? null : judgeRecord.toPayload());
        payload.put("error_detail", errorDetail == null ? null : errorDetail.toPayload());
        payload.put("primary_failure_stage",
                primaryFailureStage == null ? null : primaryFailureStage.wireValue());
        return payload;
    }
}
