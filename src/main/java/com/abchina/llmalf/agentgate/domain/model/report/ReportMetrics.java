package com.abchina.llmalf.agentgate.domain.model.report;

import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorKind;
import com.abchina.llmalf.agentgate.domain.model.metric.MetricSummary;
import com.abchina.llmalf.agentgate.domain.model.result.EvaluationResult;
import com.abchina.llmalf.agentgate.domain.model.result.Outcome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报告指标聚合.
 *
 * <p>对齐 Python result/metrics.py::calculate_metrics:
 * overall/kind/dimension/metric 四级聚合,p1-equal-mean 计划。</p>
 */
public final class ReportMetrics {

    private ReportMetrics() {
    }

    /**
     * 按计划聚合主评测器结果.
     *
     * @param results 全部结果
     * @param primaryEvaluatorIds 主评测器 id
     * @param planId 计划 id
     * @param planVersion 计划版本
     * @return 指标摘要列表(overall 在前)
     */
    public static List<MetricSummary> calculate(List<EvaluationResult> results,
            List<String> primaryEvaluatorIds, String planId, String planVersion) {
        if (!"p1-equal-mean".equals(planId) || !"1".equals(planVersion)) {
            throw new IllegalArgumentException(
                    "unsupported MetricPlan: " + planId + "@" + planVersion);
        }
        Set<String> primaryIds = new HashSet<>(primaryEvaluatorIds);
        List<EvaluationResult> primary = new ArrayList<>();
        for (EvaluationResult item : results) {
            if (primaryIds.contains(item.evaluatorId())) {
                primary.add(item);
            }
        }

        Map<String, List<EvaluationResult>> byMetric = new LinkedHashMap<>();
        for (EvaluationResult item : primary) {
            byMetric.computeIfAbsent(item.metric(), key -> new ArrayList<>()).add(item);
        }
        Map<String, String> metricDimensions = metricDimensions(primary);
        List<MetricSummary> metricSummaries = new ArrayList<>();
        for (Map.Entry<String, List<EvaluationResult>> entry : byMetric.entrySet()) {
            metricSummaries.add(summaryOf(entry.getKey(), "metric",
                    score(entry.getValue()), counts(entry.getValue())));
        }

        List<MetricSummary> dimensions = new ArrayList<>();
        // 维度名去重(对齐 Python dict.fromkeys,保序)
        Set<String> dimensionNames = new LinkedHashSet<>(metricDimensions.values());
        for (String dimension : dimensionNames) {
            List<Double> scores = new ArrayList<>();
            for (MetricSummary summary : metricSummaries) {
                if (dimension.equals(metricDimensions.get(summary.key()))
                        && summary.score() != null) {
                    scores.add(summary.score());
                }
            }
            List<EvaluationResult> related = new ArrayList<>();
            for (EvaluationResult item : primary) {
                if (dimension.equals(item.dimension())) {
                    related.add(item);
                }
            }
            dimensions.add(summaryOf(dimension, "dimension",
                    meanOrNull(scores), counts(related)));
        }

        List<MetricSummary> kinds = new ArrayList<>();
        Set<String> seenKinds = new HashSet<>();
        for (EvaluationResult item : primary) {
            String kind = item.evaluatorKind().wireValue();
            if (seenKinds.add(kind)) {
                List<EvaluationResult> related = new ArrayList<>();
                for (EvaluationResult candidate : primary) {
                    if (kind.equals(candidate.evaluatorKind().wireValue())) {
                        related.add(candidate);
                    }
                }
                Map<String, List<EvaluationResult>> kindByMetric = new LinkedHashMap<>();
                for (EvaluationResult candidate : related) {
                    kindByMetric.computeIfAbsent(candidate.metric(),
                            key -> new ArrayList<>()).add(candidate);
                }
                List<Double> scores = new ArrayList<>();
                for (List<EvaluationResult> items : kindByMetric.values()) {
                    Double metricScore = score(items);
                    if (metricScore != null) {
                        scores.add(metricScore);
                    }
                }
                kinds.add(summaryOf(kind, "kind", meanOrNull(scores),
                        counts(related)));
            }
        }

        List<Double> dimensionScores = new ArrayList<>();
        for (MetricSummary summary : dimensions) {
            if (summary.score() != null) {
                dimensionScores.add(summary.score());
            }
        }
        List<MetricSummary> output = new ArrayList<>();
        output.add(summaryOf("overall", "overall", meanOrNull(dimensionScores),
                counts(primary)));
        output.addAll(kinds);
        output.addAll(dimensions);
        output.addAll(metricSummaries);
        return output;
    }

    static Map<String, String> metricDimensions(List<EvaluationResult> results) {
        Map<String, String> dimensions = new LinkedHashMap<>();
        for (EvaluationResult result : results) {
            String previous = dimensions.putIfAbsent(result.metric(),
                    result.dimension());
            if (previous != null && !previous.equals(result.dimension())) {
                throw new IllegalArgumentException(
                        "metric '" + result.metric() + "' belongs to multiple dimensions: '"
                                + previous + "', '" + result.dimension() + "'");
            }
        }
        return dimensions;
    }

    static Double score(List<EvaluationResult> results) {
        Map<String, List<Double>> byCase = new LinkedHashMap<>();
        for (EvaluationResult result : results) {
            if (result.score() != null) {
                byCase.computeIfAbsent(result.caseId(), key -> new ArrayList<>())
                        .add(result.score());
            }
        }
        List<Double> caseScores = new ArrayList<>();
        for (List<Double> scores : byCase.values()) {
            caseScores.add(mean(scores));
        }
        return caseScores.isEmpty() ? null : mean(caseScores);
    }

    static int[] counts(List<EvaluationResult> results) {
        int passed = 0;
        int failed = 0;
        int reviewed = 0;
        int notApplicable = 0;
        int errors = 0;
        for (EvaluationResult item : results) {
            if (item.outcome() == Outcome.PASS) {
                passed++;
            } else if (item.outcome() == Outcome.FAIL) {
                failed++;
            } else if (item.outcome() == Outcome.REVIEW) {
                reviewed++;
            } else if (item.outcome() == Outcome.NOT_APPLICABLE) {
                notApplicable++;
            } else if (item.outcome() == Outcome.ERROR) {
                errors++;
            }
        }
        return new int[] {passed, failed, reviewed, notApplicable, errors,
                passed + failed + reviewed, results.size()};
    }

    static Double meanOrNull(List<Double> values) {
        return values.isEmpty() ? null : mean(values);
    }

    static double mean(List<Double> values) {
        double total = 0;
        for (double value : values) {
            total += value;
        }
        return total / values.size();
    }

    static MetricSummary summaryOf(String key, String level, Double score, int[] counts) {
        return MetricSummary.of(key, level, score, counts[0], counts[1], counts[2],
                counts[3], counts[4], counts[5], counts[6]);
    }
}
