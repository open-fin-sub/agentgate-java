package com.abchina.llmalf.agentgate.domain.model.result;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.FrozenJson;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单项检查结论.
 *
 * <p>对齐 Python domain/result.py::CheckResult:span id 格式/唯一性校验,
 * outcome 与失败定位字段、分数的联动规则;expected/actual 构造期冻结。</p>
 */
public final class CheckResult {

    private static final String SPAN_ID_PATTERN = "[0-9a-f]{16}";

    private final String id;
    private final String name;
    private final String turnId;
    private final String expectationId;
    private final Outcome outcome;
    private final Double score;
    private final String reason;
    private final Object expected;
    private final Object actual;
    private final boolean actualMissing;
    private final List<MethodRef> methods;
    private final List<String> spanIds;
    private final FailureStage failureStage;
    private final Integer failureSequence;
    private final String failureSpanId;

    private CheckResult(String id, String name, String turnId, String expectationId,
            Outcome outcome, Double score, String reason, Object expected, Object actual,
            boolean actualMissing, List<MethodRef> methods, List<String> spanIds,
            FailureStage failureStage, Integer failureSequence, String failureSpanId) {
        this.id = id;
        this.name = name;
        this.turnId = turnId;
        this.expectationId = expectationId;
        this.outcome = outcome;
        this.score = score;
        this.reason = reason;
        this.expected = expected;
        this.actual = actual;
        this.actualMissing = actualMissing;
        this.methods = methods;
        this.spanIds = spanIds;
        this.failureStage = failureStage;
        this.failureSequence = failureSequence;
        this.failureSpanId = failureSpanId;
    }

    /**
     * 构造检查结论.
     *
     * @param id 检查 id
     * @param name 名称
     * @param turnId 轮 id(可空,空串归一 null)
     * @param expectationId 期望 id(可空,空串归一 null)
     * @param outcome 结论
     * @param score 分数 [0,1](PASS/FAIL/REVIEW 必填)
     * @param reason 原因
     * @param expected 期望值(冻结)
     * @param actual 实际值(冻结)
     * @param actualMissing 实际值是否缺失
     * @param methods 实现引用列表
     * @param spanIds Span id 列表(16 位小写十六进制,唯一)
     * @param failureStage 失败阶段(仅 FAIL)
     * @param failureSequence 失败序号(仅 FAIL,非负)
     * @param failureSpanId 失败 Span id(仅 FAIL,须含于 spanIds)
     * @return 检查实例
     */
    public static CheckResult of(String id, String name, String turnId, String expectationId,
            Outcome outcome, Double score, String reason, Object expected, Object actual,
            boolean actualMissing, List<MethodRef> methods, List<String> spanIds,
            FailureStage failureStage, Integer failureSequence, String failureSpanId) {
        String validId = DomainValidations.requireNonBlank(id, "CheckResult id");
        String validName = DomainValidations.requireNonBlank(name, "CheckResult name");
        String normalizedTurnId = PayloadValues.normalizeOptionalText(turnId, "CheckResult turn_id");
        String normalizedExpectationId = PayloadValues.normalizeOptionalText(expectationId,
                "CheckResult expectation_id");
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
        String validReason = DomainValidations.requireNonBlank(reason, "CheckResult reason");
        Object frozenExpected = expected == null ? null : FrozenJson.freeze(expected);
        Object frozenActual = actual == null ? null : FrozenJson.freeze(actual);
        List<MethodRef> frozenMethods = methods == null
                ? Collections.<MethodRef>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(methods));
        List<String> frozenSpanIds = spanIds == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(spanIds));
        for (String spanId : frozenSpanIds) {
            if (spanId == null || !spanId.matches(SPAN_ID_PATTERN)) {
                throw new IllegalArgumentException("span_ids must contain lowercase OTel Span IDs");
            }
        }
        if (new HashSet<>(frozenSpanIds).size() != frozenSpanIds.size()) {
            throw new IllegalArgumentException("span_ids must be unique");
        }
        if (failureSequence != null && failureSequence < 0) {
            throw new IllegalArgumentException("Input should be greater than or equal to 0");
        }
        if (failureSpanId != null && !failureSpanId.matches(SPAN_ID_PATTERN)) {
            throw new IllegalArgumentException("failure_span_id must be a lowercase OTel Span ID");
        }

        if (outcome == Outcome.ERROR) {
            throw new IllegalArgumentException("Evaluator execution errors belong on EvaluationResult");
        }
        if (outcome == Outcome.FAIL) {
            if (failureStage == null || failureSequence == null) {
                throw new IllegalArgumentException(
                        "failed checks require failure_stage and failure_sequence");
            }
            if (failureSpanId != null && !frozenSpanIds.contains(failureSpanId)) {
                throw new IllegalArgumentException("failure_span_id must be included in span_ids");
            }
        } else if (failureStage != null || failureSequence != null || failureSpanId != null) {
            throw new IllegalArgumentException("only failed checks may contain failure location fields");
        }
        if (outcome == Outcome.NOT_APPLICABLE && score != null) {
            throw new IllegalArgumentException("not-applicable checks cannot have a score");
        }
        if ((outcome == Outcome.PASS || outcome == Outcome.FAIL || outcome == Outcome.REVIEW)
                && score == null) {
            throw new IllegalArgumentException("measured checks require a score");
        }

        return new CheckResult(validId, validName, normalizedTurnId, normalizedExpectationId,
                outcome, score, validReason, frozenExpected, frozenActual, actualMissing,
                frozenMethods, frozenSpanIds, failureStage, failureSequence, failureSpanId);
    }

    /**
     * 从 payload 解析检查结论.
     *
     * @param payload payload 对象
     * @return 检查实例
     */
    public static CheckResult fromPayload(Map<String, Object> payload) {
        List<MethodRef> methods = new ArrayList<>();
        Object rawMethods = payload.get("methods");
        if (rawMethods instanceof List) {
            for (Object item : (List<?>) rawMethods) {
                if (!(item instanceof Map)) {
                    throw new IllegalArgumentException("Input should be a valid dictionary");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> methodPayload = (Map<String, Object>) item;
                methods.add(MethodRef.fromPayload(methodPayload));
            }
        } else if (rawMethods != null) {
            throw new IllegalArgumentException("Input should be a valid list");
        }
        return of(PayloadValues.idOrDefault(payload, "id"),
                PayloadValues.requiredString(payload, "name"),
                PayloadValues.optionalString(payload, "turn_id"),
                PayloadValues.optionalString(payload, "expectation_id"),
                Outcome.fromWireValue(PayloadValues.requiredString(payload, "outcome")),
                PayloadValues.optionalDouble(payload, "score"),
                PayloadValues.requiredString(payload, "reason"),
                payload.get("expected"),
                payload.get("actual"),
                PayloadValues.boolOrDefault(payload, "actual_missing", false),
                methods,
                PayloadValues.optionalStringList(payload, "span_ids"),
                optionalFailureStage(payload),
                optionalInteger(payload, "failure_sequence"),
                PayloadValues.optionalString(payload, "failure_span_id"));
    }

    private static FailureStage optionalFailureStage(Map<String, Object> payload) {
        String value = PayloadValues.optionalString(payload, "failure_stage");
        return value == null ? null : FailureStage.fromWireValue(value);
    }

    private static Integer optionalInteger(Map<String, Object> payload, String field) {
        return PayloadValues.optionalInteger(payload, field);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String turnId() {
        return turnId;
    }

    public String expectationId() {
        return expectationId;
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

    public Object expected() {
        return expected;
    }

    public Object actual() {
        return actual;
    }

    public boolean actualMissing() {
        return actualMissing;
    }

    public List<MethodRef> methods() {
        return methods;
    }

    public List<String> spanIds() {
        return spanIds;
    }

    public FailureStage failureStage() {
        return failureStage;
    }

    public Integer failureSequence() {
        return failureSequence;
    }

    public String failureSpanId() {
        return failureSpanId;
    }

    /**
     * 组装 payload 表示(字段顺序对齐 pydantic 声明序).
     *
     * @return JSON 兼容树
     */
    public Object toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("name", name);
        payload.put("turn_id", turnId);
        payload.put("expectation_id", expectationId);
        payload.put("outcome", outcome.wireValue());
        payload.put("score", score);
        payload.put("reason", reason);
        payload.put("expected", expected);
        payload.put("actual", actual);
        payload.put("actual_missing", actualMissing);
        List<Object> methodPayloads = new ArrayList<>(methods.size());
        for (MethodRef method : methods) {
            methodPayloads.add(method.toPayload());
        }
        payload.put("methods", methodPayloads);
        payload.put("span_ids", spanIds);
        payload.put("failure_stage", failureStage == null ? null : failureStage.wireValue());
        payload.put("failure_sequence", failureSequence);
        payload.put("failure_span_id", failureSpanId);
        return payload;
    }
}
