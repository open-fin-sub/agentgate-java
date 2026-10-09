package com.abchina.llmalf.agentgate.domain.model.report;

import com.abchina.llmalf.agentgate.domain.PayloadValues;
import com.abchina.llmalf.agentgate.domain.model.cases.Case;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSeverity;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSpec;
import com.abchina.llmalf.agentgate.domain.model.gate.ReleaseGateDecision;
import com.abchina.llmalf.agentgate.domain.model.metric.MetricSummary;
import com.abchina.llmalf.agentgate.domain.model.result.EvaluationResult;
import com.abchina.llmalf.agentgate.domain.model.result.Outcome;
import com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun;
import com.abchina.llmalf.agentgate.domain.model.run.RunManifest;
import com.abchina.llmalf.agentgate.domain.model.run.RunStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 评测报告(单个已完成 Run 的终态读模型).
 *
 * <p>对齐 Python domain/report.py::EvaluationReport:五段聚合校验——
 * 结果归属/评测器七元组匹配、overall 指标唯一与计数核对、
 * release_gate 与重分类一致(missing_results 顺序敏感)。</p>
 */
public final class EvaluationReport {

    private final EvaluationRun run;
    private final List<EvaluationResult> results;
    private final List<MetricSummary> metrics;
    private final ReleaseGateDecision releaseGate;

    private EvaluationReport(EvaluationRun run, List<EvaluationResult> results,
            List<MetricSummary> metrics, ReleaseGateDecision releaseGate) {
        this.run = run;
        this.results = results;
        this.metrics = metrics;
        this.releaseGate = releaseGate;
    }

    /**
     * 构造评测报告.
     *
     * @param run 已完成的 Run
     * @param results 评测结论列表(按 Case+Evaluator 唯一,归属与七元组须匹配清单)
     * @param metrics 指标摘要列表(至少一条,含唯一 overall)
     * @param releaseGate 发布门禁决策(与重分类一致)
     * @return 报告实例
     */
    public static EvaluationReport of(EvaluationRun run, List<EvaluationResult> results,
            List<MetricSummary> metrics, ReleaseGateDecision releaseGate) {
        if (run == null) {
            throw new IllegalArgumentException("Field required");
        }
        if (releaseGate == null) {
            throw new IllegalArgumentException("Field required");
        }
        List<EvaluationResult> frozenResults = results == null
                ? Collections.<EvaluationResult>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(results));
        if (metrics == null || metrics.isEmpty()) {
            throw new IllegalArgumentException("Tuple should have at least 1 item after validation, not 0");
        }
        List<MetricSummary> frozenMetrics =
                Collections.unmodifiableList(new ArrayList<>(metrics));

        if (run.status() != RunStatus.COMPLETED) {
            throw new IllegalArgumentException("EvaluationReport requires a completed EvaluationRun");
        }
        Set<String> resultKeys = validateResults(run, frozenResults);
        MetricSummary overall = overallMetric(frozenMetrics);
        Set<String> primaryIds = new HashSet<>(run.manifest().primaryEvaluatorIds());
        List<EvaluationResult> primary = new ArrayList<>();
        for (EvaluationResult item : frozenResults) {
            if (primaryIds.contains(item.evaluatorId())) {
                primary.add(item);
            }
        }
        validateOverallCounts(overall, primary);
        List<String[]> missing = missingResults(run, resultKeys);
        validateReleaseGate(run.manifest(), primary, overall, missing, releaseGate);

        return new EvaluationReport(run, frozenResults, frozenMetrics, releaseGate);
    }

    /**
     * 从 payload 解析报告.
     *
     * @param payload payload 对象
     * @return 报告实例
     */
    public static EvaluationReport fromPayload(Map<String, Object> payload) {
        EvaluationRun run = EvaluationRun.fromPayload(PayloadValues.requiredMap(payload, "run"));
        List<EvaluationResult> results = new ArrayList<>();
        Object rawResults = payload.get("results");
        if (rawResults instanceof List) {
            for (Object item : (List<?>) rawResults) {
                if (!(item instanceof Map)) {
                    throw new IllegalArgumentException("Input should be a valid dictionary");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> resultPayload = (Map<String, Object>) item;
                results.add(EvaluationResult.fromPayload(resultPayload));
            }
        } else if (rawResults != null) {
            throw new IllegalArgumentException("Input should be a valid list");
        }
        List<Object> rawMetrics = PayloadValues.requiredList(payload, "metrics");
        List<MetricSummary> metrics = new ArrayList<>(rawMetrics.size());
        for (Object item : rawMetrics) {
            if (!(item instanceof Map)) {
                throw new IllegalArgumentException("Input should be a valid dictionary");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> metricPayload = (Map<String, Object>) item;
            metrics.add(MetricSummary.fromPayload(metricPayload));
        }
        ReleaseGateDecision releaseGate =
                ReleaseGateDecision.fromPayload(PayloadValues.requiredMap(payload, "release_gate"));
        return of(run, results, metrics, releaseGate);
    }

    private static Set<String> validateResults(EvaluationRun run, List<EvaluationResult> results) {
        Set<String> caseIds = new HashSet<>();
        for (Case item : run.manifest().executionCases()) {
            caseIds.add(item.id());
        }
        Map<String, EvaluatorSpec> specs = new HashMap<>();
        for (EvaluatorSpec spec : run.manifest().evaluatorSpecs()) {
            specs.put(spec.id(), spec);
        }
        Set<String> resultKeys = new HashSet<>();
        List<String> orderedKeys = new ArrayList<>(results.size());
        for (EvaluationResult result : results) {
            if (!result.runId().equals(run.id())) {
                throw new IllegalArgumentException("EvaluationResult belongs to a different Run");
            }
            if (!caseIds.contains(result.caseId())) {
                throw new IllegalArgumentException("EvaluationResult references an unknown Case");
            }
            EvaluatorSpec spec = specs.get(result.evaluatorId());
            if (spec == null) {
                throw new IllegalArgumentException("EvaluationResult references an unknown Evaluator");
            }
            if (!matchesSpec(result, spec)) {
                throw new IllegalArgumentException(
                        "EvaluationResult does not match its RunManifest Evaluator");
            }
            String key = result.caseId() + "\n" + result.evaluatorId();
            orderedKeys.add(key);
            resultKeys.add(key);
        }
        if (resultKeys.size() != orderedKeys.size()) {
            throw new IllegalArgumentException(
                    "Evaluation Results must be unique by Case and Evaluator");
        }
        return resultKeys;
    }

    private static boolean matchesSpec(EvaluationResult result, EvaluatorSpec spec) {
        return equalsNullable(spec.name(), result.evaluatorName())
                && equalsNullable(spec.version(), result.evaluatorVersion())
                && equalsNullable(spec.contentSha256(), result.evaluatorContentSha256())
                && spec.kind() == result.evaluatorKind()
                && equalsNullable(spec.dimension(), result.dimension())
                && equalsNullable(spec.metric(), result.metric())
                && spec.severity() == result.severity();
    }

    private static boolean equalsNullable(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private static MetricSummary overallMetric(List<MetricSummary> metrics) {
        Set<String> keys = new HashSet<>();
        List<String> ordered = new ArrayList<>(metrics.size());
        for (MetricSummary item : metrics) {
            String key = item.level() + "\n" + item.key();
            ordered.add(key);
            keys.add(key);
        }
        if (keys.size() != ordered.size()) {
            throw new IllegalArgumentException("Metric summaries must be unique by level and key");
        }
        List<MetricSummary> overall = new ArrayList<>();
        for (MetricSummary item : metrics) {
            if (MetricSummary.LEVEL_OVERALL.equals(item.level())) {
                overall.add(item);
            }
        }
        if (overall.size() != 1 || !MetricSummary.LEVEL_OVERALL.equals(overall.get(0).key())) {
            throw new IllegalArgumentException(
                    "EvaluationReport requires one overall MetricSummary");
        }
        return overall.get(0);
    }

    private static void validateOverallCounts(MetricSummary overall,
            List<EvaluationResult> primary) {
        int passed = 0;
        int failed = 0;
        int reviewed = 0;
        int notApplicable = 0;
        int errors = 0;
        int applicable = 0;
        for (EvaluationResult item : primary) {
            if (item.outcome() == Outcome.PASS) {
                passed++;
                applicable++;
            } else if (item.outcome() == Outcome.FAIL) {
                failed++;
                applicable++;
            } else if (item.outcome() == Outcome.REVIEW) {
                reviewed++;
                applicable++;
            } else if (item.outcome() == Outcome.NOT_APPLICABLE) {
                notApplicable++;
            } else {
                errors++;
            }
        }
        if (overall.passed() != passed || overall.failed() != failed
                || overall.reviewed() != reviewed || overall.notApplicable() != notApplicable
                || overall.errors() != errors || overall.applicable() != applicable
                || overall.total() != primary.size()) {
            throw new IllegalArgumentException(
                    "overall Metric counts do not match primary Evaluation Results");
        }
    }

    private static List<String[]> missingResults(EvaluationRun run, Set<String> resultKeys) {
        List<String[]> missing = new ArrayList<>();
        for (Case item : run.manifest().executionCases()) {
            for (String evaluatorId : run.manifest().primaryEvaluatorIds()) {
                if (!resultKeys.contains(item.id() + "\n" + evaluatorId)) {
                    missing.add(new String[] {item.id(), evaluatorId});
                }
            }
        }
        return missing;
    }

    private static void validateReleaseGate(
            RunManifest manifest,
            List<EvaluationResult> primary, MetricSummary overall, List<String[]> missing,
            ReleaseGateDecision decision) {
        if (!equalsNullable(decision.score(), overall.score())) {
            throw new IllegalArgumentException(
                    "release-gate score must equal the overall Metric score");
        }
        if (decision.minimumScore() != manifest.gateSpec().minimumScore()) {
            throw new IllegalArgumentException(
                    "release-gate minimum_score must match the RunManifest");
        }
        if (!sameMissingPairs(decision.missingResults(), missing)) {
            throw new IllegalArgumentException(
                    "release-gate missing_results do not match the RunManifest");
        }
        boolean hasEvaluatorErrors = false;
        boolean hasBlockingFailures = false;
        boolean hasReviews = false;
        for (EvaluationResult item : primary) {
            if (item.outcome() == Outcome.ERROR) {
                hasEvaluatorErrors = true;
            }
            if (item.severity() == EvaluatorSeverity.BLOCKING
                    && item.outcome() == Outcome.FAIL) {
                hasBlockingFailures = true;
            }
            if (item.outcome() == Outcome.REVIEW) {
                hasReviews = true;
            }
        }
        ReleaseGateDecision.Classification expected = ReleaseGateDecision.classify(
                !missing.isEmpty(), hasEvaluatorErrors, hasBlockingFailures, hasReviews,
                overall.score(), manifest.gateSpec().minimumScore());
        if (decision.outcome() != expected.outcome()
                || !decision.reasonCode().equals(expected.reasonCode())) {
            throw new IllegalArgumentException(
                    "release-gate decision does not match Evaluation Results");
        }
    }

    private static boolean sameMissingPairs(List<String[]> left, List<String[]> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!Arrays.equals(left.get(i), right.get(i))) {
                return false;
            }
        }
        return true;
    }

    public EvaluationRun run() {
        return run;
    }

    public List<EvaluationResult> results() {
        return results;
    }

    public List<MetricSummary> metrics() {
        return metrics;
    }

    public ReleaseGateDecision releaseGate() {
        return releaseGate;
    }

    /**
     * 组装 payload 表示(字段顺序对齐 pydantic 声明序).
     *
     * @return JSON 兼容树
     */
    public Object toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run", run.toPayload());
        List<Object> resultPayloads = new ArrayList<>(results.size());
        for (EvaluationResult result : results) {
            resultPayloads.add(result.toPayload());
        }
        payload.put("results", resultPayloads);
        List<Object> metricPayloads = new ArrayList<>(metrics.size());
        for (MetricSummary metric : metrics) {
            metricPayloads.add(metric.toPayload());
        }
        payload.put("metrics", metricPayloads);
        payload.put("release_gate", releaseGate.toPayload());
        return payload;
    }
}
