package com.abchina.llmalf.agentgate.service.impl;

import com.abchina.llmalf.agentgate.common.AgentException;
import com.abchina.llmalf.agentgate.domain.model.cases.Case;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersion;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSpec;
import com.abchina.llmalf.agentgate.domain.model.metric.MetricSummary;
import com.abchina.llmalf.agentgate.domain.model.report.EvaluationReport;
import com.abchina.llmalf.agentgate.domain.model.result.EvaluationResult;
import com.abchina.llmalf.agentgate.domain.model.result.Outcome;
import com.abchina.llmalf.agentgate.domain.model.target.TargetRef;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报告对比服务.
 *
 * <p>对齐 Python result/comparison.py::compare_reports:
 * 兼容性校验(同目标/同数据集内容/同用例序/同主评测器/同计划与门禁)
 * + metric delta + case delta(unchanged/improvement/regression/changed)。</p>
 */
@Service
public class ComparisonService {

    public ComparisonService() {
    }

    /**
     * 对比两个已完成报告.
     *
     * @param baseline 基线报告
     * @param candidate 候选报告
     * @return 对比投影
     */
    public Map<String, Object> compare(EvaluationReport baseline, EvaluationReport candidate) {
        validateCompatible(baseline, candidate);
        List<Map<String, Object>> metricDeltas = compareMetrics(baseline, candidate);
        Double overall = null;
        for (Map<String, Object> delta : metricDeltas) {
            if ("overall".equals(delta.get("level")) && "overall".equals(delta.get("key"))) {
                overall = (Double) delta.get("score_delta");
                break;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("baseline_run_id", baseline.run().id());
        result.put("candidate_run_id", candidate.run().id());
        result.put("baseline_target_version",
                baseline.run().manifest().target().ref().externalVersionId());
        result.put("candidate_target_version",
                candidate.run().manifest().target().ref().externalVersionId());
        result.put("baseline_gate", baseline.releaseGate().toPayload());
        result.put("candidate_gate", candidate.releaseGate().toPayload());
        result.put("overall_score_delta", overall);
        result.put("metric_deltas", metricDeltas);
        result.put("case_deltas", compareCases(baseline, candidate));
        return result;
    }

    private static void validateCompatible(EvaluationReport baseline, EvaluationReport candidate) {
        TargetRef baselineRef = baseline.run().manifest().target().ref();
        TargetRef candidateRef = candidate.run().manifest().target().ref();
        if (!baselineRef.sourceId().equals(candidateRef.sourceId())
                || baselineRef.targetType() != candidateRef.targetType()
                || !baselineRef.externalTargetId().equals(candidateRef.externalTargetId())) {
            throw new AgentException(409,
                    "reports reference different Agent or Skill targets");
        }
        DatasetVersion baselineDataset = baseline.run().manifest().dataset();
        DatasetVersion candidateDataset = candidate.run().manifest().dataset();
        if (!baselineDataset.datasetId().equals(candidateDataset.datasetId())) {
            throw new AgentException(409, "reports reference different Datasets");
        }
        if (!baselineDataset.contentSha256().equals(candidateDataset.contentSha256())) {
            throw new AgentException(409, "reports use different Dataset content");
        }
        List<String> baselineCases = caseIds(baseline);
        List<String> candidateCases = caseIds(candidate);
        if (!baselineCases.equals(candidateCases)) {
            throw new AgentException(409, "reports use different ordered Case identities");
        }
        if (!primarySignature(baseline).equals(primarySignature(candidate))) {
            throw new AgentException(409, "reports use different primary Evaluators");
        }
        if (!baseline.run().manifest().metricPlan().id()
                .equals(candidate.run().manifest().metricPlan().id())
                || !baseline.run().manifest().metricPlan().version()
                        .equals(candidate.run().manifest().metricPlan().version())) {
            throw new AgentException(409, "reports use different Metric plans");
        }
        if (!baseline.run().manifest().gateSpec().id()
                .equals(candidate.run().manifest().gateSpec().id())
                || baseline.run().manifest().gateSpec().minimumScore() != candidate
                        .run().manifest().gateSpec().minimumScore()) {
            throw new AgentException(409,
                    "reports use different release-gate specifications");
        }
    }

    private static List<String> caseIds(EvaluationReport report) {
        List<String> ids = new ArrayList<>();
        for (Case caseItem : report.run().manifest().executionCases()) {
            ids.add(caseItem.id());
        }
        return ids;
    }

    private static List<List<String>> primarySignature(EvaluationReport report) {
        Map<String, EvaluatorSpec> specs = new HashMap<>();
        for (EvaluatorSpec spec : report.run().manifest().evaluatorSpecs()) {
            specs.put(spec.id(), spec);
        }
        List<List<String>> signature = new ArrayList<>();
        for (String evaluatorId : report.run().manifest().primaryEvaluatorIds()) {
            EvaluatorSpec spec = specs.get(evaluatorId);
            signature.add(Arrays.asList(evaluatorId, spec.version(),
                    spec.contentSha256()));
        }
        return signature;
    }

    private static List<Map<String, Object>> compareMetrics(EvaluationReport baseline,
            EvaluationReport candidate) {
        Map<String, MetricSummary> candidateMetrics = new LinkedHashMap<>();
        for (MetricSummary item : candidate.metrics()) {
            candidateMetrics.put(item.level() + "\n" + item.key(), item);
        }
        Set<String> baselineKeys = new HashSet<>();
        for (MetricSummary item : baseline.metrics()) {
            baselineKeys.add(item.level() + "\n" + item.key());
        }
        if (!baselineKeys.equals(candidateMetrics.keySet())) {
            throw new AgentException(409, "reports contain different metric summaries");
        }
        List<Map<String, Object>> deltas = new ArrayList<>(baseline.metrics().size());
        for (MetricSummary item : baseline.metrics()) {
            MetricSummary after = candidateMetrics.get(item.level() + "\n" + item.key());
            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put("level", item.level());
            delta.put("key", item.key());
            delta.put("baseline", item.toPayload());
            delta.put("candidate", after.toPayload());
            delta.put("score_delta", scoreDelta(item.score(), after.score()));
            deltas.add(delta);
        }
        return deltas;
    }

    private static List<Map<String, Object>> compareCases(EvaluationReport baseline,
            EvaluationReport candidate) {
        Map<String, EvaluationResult> baselineResults = new HashMap<>();
        for (EvaluationResult item : baseline.results()) {
            baselineResults.put(item.caseId() + "\n" + item.evaluatorId(), item);
        }
        Map<String, EvaluationResult> candidateResults = new HashMap<>();
        for (EvaluationResult item : candidate.results()) {
            candidateResults.put(item.caseId() + "\n" + item.evaluatorId(), item);
        }
        List<Map<String, Object>> deltas = new ArrayList<>();
        for (Case caseItem : baseline.run().manifest().executionCases()) {
            for (String evaluatorId : baseline.run().manifest().primaryEvaluatorIds()) {
                String key = caseItem.id() + "\n" + evaluatorId;
                EvaluationResult before = baselineResults.get(key);
                EvaluationResult after = candidateResults.get(key);
                Outcome beforeOutcome = before == null ? null : before.outcome();
                Outcome afterOutcome = after == null ? null : after.outcome();
                Double beforeScore = before == null ? null : before.score();
                Double afterScore = after == null ? null : after.score();
                Double delta = scoreDelta(beforeScore, afterScore);
                Map<String, Object> caseDelta = new LinkedHashMap<>();
                caseDelta.put("case_id", caseItem.id());
                caseDelta.put("evaluator_id", evaluatorId);
                caseDelta.put("baseline_outcome", beforeOutcome == null ? null
                        : beforeOutcome.wireValue());
                caseDelta.put("candidate_outcome", afterOutcome == null ? null
                        : afterOutcome.wireValue());
                caseDelta.put("baseline_score", beforeScore);
                caseDelta.put("candidate_score", afterScore);
                caseDelta.put("score_delta", delta);
                caseDelta.put("change", classifyChange(beforeOutcome, afterOutcome, delta));
                deltas.add(caseDelta);
            }
        }
        return deltas;
    }

    private static Double scoreDelta(Double baseline, Double candidate) {
        if (baseline == null || candidate == null) {
            return null;
        }
        return candidate - baseline;
    }

    private static String classifyChange(Outcome before, Outcome after, Double delta) {
        boolean sameOutcome = before == after;
        if (sameOutcome && (delta == null || delta == 0)) {
            return "unchanged";
        }
        if (before == Outcome.PASS && after != Outcome.PASS) {
            return "regression";
        }
        if (before != Outcome.PASS && after == Outcome.PASS) {
            return "improvement";
        }
        if (delta != null) {
            if (delta > 0) {
                return "improvement";
            }
            if (delta < 0) {
                return "regression";
            }
        }
        return "changed";
    }
}
