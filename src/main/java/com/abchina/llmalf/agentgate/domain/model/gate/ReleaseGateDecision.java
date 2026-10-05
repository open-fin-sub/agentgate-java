package com.abchina.llmalf.agentgate.domain.model.gate;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.PayloadValues;
import com.abchina.llmalf.agentgate.domain.model.result.Outcome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 发布门禁不可变决策.
 *
 * <p>对齐 Python domain/gate.py::ReleaseGateDecision/classify_release_gate:
 * fail-closed 固定优先级分类;missing_results 为 (case_id, evaluator_id) 对
 * (payload 形态为二元字符串数组列表)。</p>
 */
public final class ReleaseGateDecision {

    /** reason wire 值:达标 */
    public static final String REASON_THRESHOLD_MET = "threshold_met";
    /** reason wire 值:分数低于阈值 */
    public static final String REASON_SCORE_BELOW_THRESHOLD = "score_below_threshold";
    /** reason wire 值:存在缺失结果 */
    public static final String REASON_MISSING_RESULTS = "missing_results";
    /** reason wire 值:评测器错误 */
    public static final String REASON_EVALUATOR_ERROR = "evaluator_error";
    /** reason wire 值:阻断性失败 */
    public static final String REASON_BLOCKING_FAILURE = "blocking_failure";
    /** reason wire 值:需要人工复核 */
    public static final String REASON_REVIEW_REQUIRED = "review_required";
    /** reason wire 值:无可适用结果 */
    public static final String REASON_NO_APPLICABLE_RESULTS = "no_applicable_results";

    private final Outcome outcome;
    private final List<String[]> missingResults;
    private final Double score;
    private final double minimumScore;
    private final String reasonCode;

    private ReleaseGateDecision(Outcome outcome, List<String[]> missingResults, Double score,
            double minimumScore, String reasonCode) {
        this.outcome = outcome;
        this.missingResults = missingResults;
        this.score = score;
        this.minimumScore = minimumScore;
        this.reasonCode = reasonCode;
    }

    /**
     * 按固定 fail-closed 优先级分类发布事实.
     *
     * @param hasMissingResults 是否存在缺失结果
     * @param hasEvaluatorErrors 是否存在评测器错误
     * @param hasBlockingFailures 是否存在阻断性失败
     * @param hasReviews 是否存在待复核
     * @param score 总分(可空)
     * @param minimumScore 阈值
     * @return 分类结果(outcome + reason)
     */
    public static Classification classify(boolean hasMissingResults, boolean hasEvaluatorErrors,
            boolean hasBlockingFailures, boolean hasReviews, Double score, double minimumScore) {
        if (hasMissingResults) {
            return new Classification(Outcome.FAIL, REASON_MISSING_RESULTS);
        }
        if (hasEvaluatorErrors) {
            return new Classification(Outcome.FAIL, REASON_EVALUATOR_ERROR);
        }
        if (hasBlockingFailures) {
            return new Classification(Outcome.FAIL, REASON_BLOCKING_FAILURE);
        }
        if (hasReviews) {
            return new Classification(Outcome.FAIL, REASON_REVIEW_REQUIRED);
        }
        if (score == null) {
            return new Classification(Outcome.FAIL, REASON_NO_APPLICABLE_RESULTS);
        }
        if (score >= minimumScore) {
            return new Classification(Outcome.PASS, REASON_THRESHOLD_MET);
        }
        return new Classification(Outcome.FAIL, REASON_SCORE_BELOW_THRESHOLD);
    }

    /**
     * 构造门禁决策.
     *
     * @param outcome 结论(pass/fail)
     * @param missingResults 缺失结果引用对 [(caseId, evaluatorId)]
     * @param score 总分(可空 [0,1])
     * @param minimumScore 阈值 [0,1]
     * @param reasonCode 原因码(7 值)
     * @return 决策实例
     */
    public static ReleaseGateDecision of(Outcome outcome, List<String[]> missingResults,
            Double score, double minimumScore, String reasonCode) {
        if (outcome != Outcome.PASS && outcome != Outcome.FAIL) {
            throw new IllegalArgumentException(
                    "Input should be 'pass' or 'fail'");
        }
        List<String[]> frozenMissing = missingResults == null
                ? Collections.<String[]>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(missingResults));
        for (String[] pair : frozenMissing) {
            if (pair == null || pair.length != 2
                    || DomainValidations.isBlank(pair[0]) || DomainValidations.isBlank(pair[1])) {
                throw new IllegalArgumentException(
                        "missing_results cannot contain blank identifiers");
            }
        }
        Set<String> seen = new HashSet<>();
        for (String[] pair : frozenMissing) {
            if (!seen.add(pair[0] + "\n" + pair[1])) {
                throw new IllegalArgumentException("missing_results must be unique");
            }
        }
        if (score != null) {
            if (score < 0) {
                throw new IllegalArgumentException("Input should be greater than or equal to 0");
            }
            if (score > 1) {
                throw new IllegalArgumentException("Input should be less than or equal to 1");
            }
        }
        if (minimumScore < 0) {
            throw new IllegalArgumentException("Input should be greater than or equal to 0");
        }
        if (minimumScore > 1) {
            throw new IllegalArgumentException("Input should be less than or equal to 1");
        }
        if (!isValidReason(reasonCode)) {
            throw new IllegalArgumentException(
                    "Input should be 'threshold_met', 'score_below_threshold', 'missing_results', "
                            + "'evaluator_error', 'blocking_failure', 'review_required' or "
                            + "'no_applicable_results'");
        }

        Outcome expectedOutcome = REASON_THRESHOLD_MET.equals(reasonCode)
                ? Outcome.PASS : Outcome.FAIL;
        if (outcome != expectedOutcome) {
            throw new IllegalArgumentException("release-gate outcome does not match reason_code");
        }
        if (REASON_MISSING_RESULTS.equals(reasonCode) && frozenMissing.isEmpty()) {
            throw new IllegalArgumentException(
                    "missing_results reason requires missing result references");
        }
        if (!REASON_MISSING_RESULTS.equals(reasonCode) && !frozenMissing.isEmpty()) {
            throw new IllegalArgumentException(
                    "only missing_results reason may contain missing references");
        }
        if (REASON_THRESHOLD_MET.equals(reasonCode)) {
            if (score == null || score < minimumScore) {
                throw new IllegalArgumentException(
                        "threshold_met requires score to meet minimum_score");
            }
        } else if (REASON_SCORE_BELOW_THRESHOLD.equals(reasonCode)) {
            if (score == null || score >= minimumScore) {
                throw new IllegalArgumentException(
                        "score_below_threshold requires score below minimum_score");
            }
        } else if (REASON_NO_APPLICABLE_RESULTS.equals(reasonCode) && score != null) {
            throw new IllegalArgumentException("no_applicable_results requires no score");
        }

        return new ReleaseGateDecision(outcome, frozenMissing, score, minimumScore, reasonCode);
    }

    /**
     * 从 payload 解析决策.
     *
     * @param payload payload 对象
     * @return 决策实例
     */
    public static ReleaseGateDecision fromPayload(Map<String, Object> payload) {
        return of(Outcome.fromWireValue(PayloadValues.requiredString(payload, "outcome")),
                parseMissingResults(payload),
                PayloadValues.optionalDouble(payload, "score"),
                PayloadValues.requiredDouble(payload, "minimum_score"),
                PayloadValues.requiredString(payload, "reason_code"));
    }

    private static List<String[]> parseMissingResults(Map<String, Object> payload) {
        Object raw = payload == null ? null : payload.get("missing_results");
        if (raw == null) {
            return Collections.emptyList();
        }
        if (!(raw instanceof List)) {
            throw new IllegalArgumentException("Input should be a valid list");
        }
        List<String[]> parsed = new ArrayList<>(((List<?>) raw).size());
        for (Object item : (List<?>) raw) {
            if (!(item instanceof List) || ((List<?>) item).size() != 2) {
                throw new IllegalArgumentException("Input should be a valid list");
            }
            List<?> pair = (List<?>) item;
            String caseId = pair.get(0) instanceof String ? (String) pair.get(0) : null;
            String evaluatorId = pair.get(1) instanceof String ? (String) pair.get(1) : null;
            parsed.add(new String[] {caseId, evaluatorId});
        }
        return parsed;
    }

    private static boolean isValidReason(String reason) {
        return REASON_THRESHOLD_MET.equals(reason)
                || REASON_SCORE_BELOW_THRESHOLD.equals(reason)
                || REASON_MISSING_RESULTS.equals(reason)
                || REASON_EVALUATOR_ERROR.equals(reason)
                || REASON_BLOCKING_FAILURE.equals(reason)
                || REASON_REVIEW_REQUIRED.equals(reason)
                || REASON_NO_APPLICABLE_RESULTS.equals(reason);
    }

    public Outcome outcome() {
        return outcome;
    }

    public List<String[]> missingResults() {
        return missingResults;
    }

    public Double score() {
        return score;
    }

    public double minimumScore() {
        return minimumScore;
    }

    public String reasonCode() {
        return reasonCode;
    }

    /**
     * 组装 payload 表示.
     *
     * @return JSON 兼容树
     */
    public Object toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("outcome", outcome.wireValue());
        List<Object> missingPayloads = new ArrayList<>(missingResults.size());
        for (String[] pair : missingResults) {
            List<String> pairPayload = new ArrayList<>(2);
            pairPayload.add(pair[0]);
            pairPayload.add(pair[1]);
            missingPayloads.add(pairPayload);
        }
        payload.put("missing_results", missingPayloads);
        payload.put("score", score);
        payload.put("minimum_score", minimumScore);
        payload.put("reason_code", reasonCode);
        return payload;
    }

    /**
     * 发布事实分类结果(outcome + reason)。
     */
    public static final class Classification {

        private final Outcome outcome;
        private final String reasonCode;

        private Classification(Outcome outcome, String reasonCode) {
            this.outcome = outcome;
            this.reasonCode = reasonCode;
        }

        public Outcome outcome() {
            return outcome;
        }

        public String reasonCode() {
            return reasonCode;
        }
    }
}
