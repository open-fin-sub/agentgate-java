package com.abchina.llmalf.agentgate.controller;

import com.abchina.llmalf.agentgate.common.ApiErrors;
import com.abchina.llmalf.agentgate.common.PydanticErrors;
import com.abchina.llmalf.agentgate.common.ResponseBase;
import com.abchina.llmalf.agentgate.domain.model.cases.Case;
import com.abchina.llmalf.agentgate.service.impl.ComparisonService;
import com.abchina.llmalf.agentgate.service.impl.ResultAnalyticsService;
import com.abchina.llmalf.agentgate.service.impl.ResultService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 结果读取端点.
 *
 * <p>对齐 Python server/routes/results.py 的 7 端点
 * (overview/report/samples/trace/cases/writeback)。</p>
 */
@RestController
public class ResultController {

    private final ResultService resultService;
    private final ResultAnalyticsService resultAnalyticsService;
    private final ComparisonService comparisonService;

    public ResultController(ResultService resultService,
            ResultAnalyticsService resultAnalyticsService,
            ComparisonService comparisonService) {
        this.resultService = resultService;
        this.resultAnalyticsService = resultAnalyticsService;
        this.comparisonService = comparisonService;
    }

    /**
     * 团队概览.
     *
     * @return 概览投影
     */
    @GetMapping("/api/overview")
    public ResponseBase<Map<String, Object>> overview() {
        return ResponseBase.success(resultService.overview());
    }

    /**
     * Run 报告(限已完成).
     *
     * @param runId Run id
     * @return 报告 payload
     */
    @GetMapping("/api/runs/{runId}")
    public ResponseBase<Object> runReport(@PathVariable("runId") String runId) {
        Object result = ApiErrors.notFound(() ->
                (Object) resultService.getReport(runId).toPayload());
        return ResponseBase.success(result);
    }

    /**
     * 持久化证据样本.
     *
     * @param runId Run id
     * @return 样本投影
     */
    @GetMapping("/api/runs/{runId}/samples")
    public ResponseBase<Map<String, Object>> availableSamples(
            @PathVariable("runId") String runId) {
        Map<String, Object> result = ApiErrors.notFound(() ->
                resultService.samples(runId));
        return ResponseBase.success(result);
    }

    /**
     * 用例轨迹脱敏视图.
     *
     * @param runId Run id
     * @param caseId 用例 id
     * @return 脱敏轨迹
     */
    @GetMapping("/api/runs/{runId}/traces/{caseId}")
    public ResponseBase<Object> traceDetail(@PathVariable("runId") String runId,
            @PathVariable("caseId") String caseId) {
        Object result = ApiErrors.notFound(() ->
                (Object) resultService.getTrace(runId, caseId).toPayload());
        return ResponseBase.success(result);
    }

    /**
     * 历史用例详情.
     *
     * @param runId Run id
     * @param caseId 用例 id
     * @return 历史用例投影
     */
    @GetMapping("/api/runs/{runId}/cases/{caseId}")
    public ResponseBase<Map<String, Object>> historicalCaseDetail(
            @PathVariable("runId") String runId, @PathVariable("caseId") String caseId) {
        Map<String, Object> result = ApiErrors.notFound(() ->
                resultService.getHistoricalCase(runId, caseId));
        return ResponseBase.success(result);
    }

    /**
     * 失败用例回写草稿.
     *
     * @param runId Run id
     * @param caseId 用例 id
     * @param body 请求体(case 字段)
     * @return 回写结果
     */
    @PostMapping("/api/runs/{runId}/cases/{caseId}/writeback")
    public ResponseBase<Map<String, Object>> writebackResultCase(
            @PathVariable("runId") String runId, @PathVariable("caseId") String caseId,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> casePayload = (Map<String, Object>) body.get("case");
        Map<String, Object> result = ApiErrors.unprocessable(() -> {
            Case editedCase = Case.fromPayload(casePayload);
            return resultService.writeback(runId, caseId, editedCase);
        });
        return ResponseBase.success(result);
    }

    /**
     * Run 分析(7 维分桶,限已完成).
     *
     * @param runId Run id
     * @return 分析投影
     */
    @GetMapping("/api/runs/{runId}/analytics")
    public ResponseBase<Map<String, Object>> resultAnalytics(
            @PathVariable("runId") String runId) {
        Map<String, Object> result = ApiErrors.notFound(() ->
                resultAnalyticsService.analytics(runId));
        return ResponseBase.success(result);
    }

    /**
     * 对比两个已完成 Run.
     *
     * @param baselineRunId 基线 Run id
     * @param candidateRunId 候选 Run id
     * @return 对比投影
     */
    @GetMapping("/api/run-comparisons")
    public ResponseBase<Map<String, Object>> compareRuns(
            @RequestParam(value = "baseline_run_id", required = false) String baselineRunId,
            @RequestParam(value = "candidate_run_id", required = false) String candidateRunId) {
        PydanticErrors errors = new PydanticErrors();
        if (baselineRunId == null) {
            errors.missing("query", "baseline_run_id", null);
        }
        if (candidateRunId == null) {
            errors.missing("query", "candidate_run_id", null);
        }
        errors.throwIfAny();
        com.abchina.llmalf.agentgate.domain.model.report.EvaluationReport baseline =
                resultService.getReport(baselineRunId);
        com.abchina.llmalf.agentgate.domain.model.report.EvaluationReport candidate =
                resultService.getReport(candidateRunId);
        return ResponseBase.success(comparisonService.compare(baseline, candidate));
    }
}
