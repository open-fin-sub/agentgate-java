package com.abchina.llmalf.agentgate.domain.model.report;

import com.abchina.llmalf.agentgate.domain.model.cases.Case;
import com.abchina.llmalf.agentgate.domain.model.gate.ReleaseGateDecision;
import com.abchina.llmalf.agentgate.domain.model.metric.MetricSummary;
import com.abchina.llmalf.agentgate.domain.model.result.EvaluationResult;
import com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun;

import java.util.ArrayList;
import java.util.List;

/**
 * 评测报告组装.
 *
 * <p>对齐 Python result/report.py::build_evaluation_report:
 * 指标聚合 + 门禁决策 → EvaluationReport(构造期全量校验)。</p>
 */
public final class ReportAssembler {

    private ReportAssembler() {
    }

    /**
     * 组装报告.
     *
     * @param run 已完成 Run
     * @param results 结果列表
     * @return 报告
     */
    public static EvaluationReport build(EvaluationRun run, List<EvaluationResult> results) {
        List<MetricSummary> metrics = ReportMetrics.calculate(results,
                run.manifest().primaryEvaluatorIds(),
                run.manifest().metricPlan().id(),
                run.manifest().metricPlan().version());
        List<String> expectedCaseIds = new ArrayList<>();
        for (Case caseItem
                : run.manifest().executionCases()) {
            expectedCaseIds.add(caseItem.id());
        }
        ReleaseGateDecision releaseGate = ReportGate.decide(results, metrics,
                expectedCaseIds, run.manifest().primaryEvaluatorIds(),
                run.manifest().gateSpec());
        return EvaluationReport.of(run, results, metrics, releaseGate);
    }
}
