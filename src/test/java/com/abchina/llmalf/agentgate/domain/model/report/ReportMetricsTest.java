package com.abchina.llmalf.agentgate.domain.model.report;

import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorKind;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSeverity;
import com.abchina.llmalf.agentgate.domain.model.metric.MetricSummary;
import com.abchina.llmalf.agentgate.domain.model.result.EvaluationResult;
import com.abchina.llmalf.agentgate.domain.model.result.Outcome;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 报告指标聚合纯单测(维度名去重回归,对齐 Python metrics.py).
 */
class ReportMetricsTest {

    private static EvaluationResult result(String metric, String dimension, double score) {
        com.abchina.llmalf.agentgate.domain.model.result.CheckResult check =
                com.abchina.llmalf.agentgate.domain.model.result.CheckResult.of(
                        "chk-1", "校验", "turn-1", "exp-1", Outcome.PASS, score, "ok",
                        null, null, false, java.util.Collections.emptyList(),
                        java.util.Collections.emptyList(), null, null, null);
        return EvaluationResult.of("res-" + metric, "run-1", "case-1",
                repeat('a', 32), "ev-1", "评测", "1", repeat('b', 64),
                EvaluatorKind.RULE, dimension, metric, EvaluatorSeverity.STANDARD,
                Outcome.PASS, score, "ok",
                java.util.Collections.singletonList(check), null, null, null);
    }

    @Test
    void sharedDimensionMetricsAreDeduplicatedNotRejected() {
        // 同一 dimension 下两个 metric:修复前会因 (level=dimension,key) 重复抛异常
        List<MetricSummary> summaries = assertDoesNotThrow(() -> ReportMetrics.calculate(
                Arrays.asList(result("metric-1", "dim-1", 1.0),
                        result("metric-2", "dim-1", 0.0)),
                java.util.Collections.singletonList("ev-1"), "p1-equal-mean", "1"));
        // overall + kind(rule) + dimension(dim-1) + metric-1 + metric-2
        assertEquals(5, summaries.size());
        long dimensionCount = summaries.stream()
                .filter(item -> "dimension".equals(item.level())).count();
        assertEquals(1, dimensionCount);
        MetricSummary overall = summaries.get(0);
        assertEquals("overall", overall.key());
        assertEquals(0.5, overall.score(), 1e-9);
        assertEquals(2, overall.passed());
    }

    @Test
    void unsupportedPlanIsRejected() {
        IllegalArgumentException error = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> ReportMetrics.calculate(java.util.Collections.emptyList(),
                        java.util.Collections.singletonList("ev-1"),
                        "other-plan", "1"));
        assertEquals("unsupported MetricPlan: other-plan@1", error.getMessage());
    }

    private static String repeat(char c, int n) {
        StringBuilder b = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            b.append(c);
        }
        return b.toString();
    }
}
