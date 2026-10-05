package com.abchina.llmalf.agentgate.domain.model.report;

import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSeverity;
import com.abchina.llmalf.agentgate.domain.model.gate.ReleaseGateDecision;
import com.abchina.llmalf.agentgate.domain.model.gate.ReleaseGateSpec;
import com.abchina.llmalf.agentgate.domain.model.metric.MetricSummary;
import com.abchina.llmalf.agentgate.domain.model.result.EvaluationResult;
import com.abchina.llmalf.agentgate.domain.model.result.Outcome;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 报告门禁决策.
 *
 * <p>对齐 Python result/gate.py::decide_release_gate:
 * 主结果唯一性 → 缺失对 → overall 摘要 → fail-closed 分类。</p>
 */
public final class ReportGate {

    private ReportGate() {
    }

    /**
     * 计算发布门禁决策.
     *
     * @param results 全部结果
     * @param metrics 指标摘要
     * @param expectedCaseIds 期望用例 id
     * @param primaryEvaluatorIds 主评测器 id
     * @param spec 门禁配置
     * @return 决策
     */
    public static ReleaseGateDecision decide(List<EvaluationResult> results,
            List<MetricSummary> metrics, List<String> expectedCaseIds,
            List<String> primaryEvaluatorIds, ReleaseGateSpec spec) {
        Set<String> primaryIds = new HashSet<>(primaryEvaluatorIds);
        List<EvaluationResult> primary = new ArrayList<>();
        for (EvaluationResult item : results) {
            if (primaryIds.contains(item.evaluatorId())) {
                primary.add(item);
            }
        }
        Set<String> resultKeys = new HashSet<>();
        for (EvaluationResult item : primary) {
            if (!resultKeys.add(item.caseId() + "\n" + item.evaluatorId())) {
                throw new IllegalArgumentException(
                        "primary Evaluation Results must be unique by Case and Evaluator");
            }
        }
        List<String[]> missingResults = new ArrayList<>();
        for (String caseId : expectedCaseIds) {
            for (String evaluatorId : primaryEvaluatorIds) {
                if (!resultKeys.contains(caseId + "\n" + evaluatorId)) {
                    missingResults.add(new String[] {caseId, evaluatorId});
                }
            }
        }
        List<MetricSummary> overallMetrics = new ArrayList<>();
        for (MetricSummary item : metrics) {
            if ("overall".equals(item.level())) {
                overallMetrics.add(item);
            }
        }
        if (overallMetrics.size() != 1) {
            throw new IllegalArgumentException(
                    "release-gate evaluation requires exactly one overall MetricSummary");
        }
        Double score = overallMetrics.get(0).score();

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
        ReleaseGateDecision.Classification classification = ReleaseGateDecision.classify(
                !missingResults.isEmpty(), hasEvaluatorErrors, hasBlockingFailures,
                hasReviews, score, spec.minimumScore());
        return ReleaseGateDecision.of(classification.outcome(), missingResults, score,
                spec.minimumScore(), classification.reasonCode());
    }
}
