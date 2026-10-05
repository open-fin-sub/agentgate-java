package com.abchina.llmalf.agentgate.controller;

import com.abchina.llmalf.agentgate.domain.model.cases.Case;
import com.abchina.llmalf.agentgate.domain.model.cases.CaseTurn;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersion;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersionStatus;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorKind;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSeverity;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSpec;
import com.abchina.llmalf.agentgate.domain.model.gate.ReleaseGateSpec;
import com.abchina.llmalf.agentgate.domain.model.metric.MetricPlan;
import com.abchina.llmalf.agentgate.domain.model.result.CheckResult;
import com.abchina.llmalf.agentgate.domain.model.result.EvaluationResult;
import com.abchina.llmalf.agentgate.domain.model.result.Outcome;
import com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun;
import com.abchina.llmalf.agentgate.domain.model.run.RunLifecycle;
import com.abchina.llmalf.agentgate.domain.model.run.RunManifest;
import com.abchina.llmalf.agentgate.domain.model.run.RunStatus;
import com.abchina.llmalf.agentgate.domain.model.target.TargetRef;
import com.abchina.llmalf.agentgate.domain.model.target.TargetSnapshot;
import com.abchina.llmalf.agentgate.domain.model.target.TargetType;
import com.abchina.llmalf.agentgate.domain.model.trace.SpanStatus;
import com.abchina.llmalf.agentgate.domain.model.trace.Trace;
import com.abchina.llmalf.agentgate.domain.model.trace.TraceSpan;
import com.abchina.llmalf.agentgate.logic.ResultLogic;
import com.abchina.llmalf.agentgate.logic.RunLogic;
import com.abchina.llmalf.agentgate.logic.TargetLogic;
import com.abchina.llmalf.agentgate.logic.TraceLogic;
import com.abchina.llmalf.agentgate.service.impl.DemoCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * analytics + comparison + lineage + stability summary 集成测试(共库回滚).
 */
@SpringBootTest
@Transactional
class AnalyticsComparisonLineageControllerTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-01-02T00:00:00Z");
    private static final OffsetDateTime T2 = OffsetDateTime.parse("2026-01-03T00:00:00Z");
    private static final String DESCRIPTOR_SHA = repeat('a', 64);
    private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";
    private static final String SPAN = "0123456789abcdef";
    private static final String TEAM = "it-team-af";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RunLogic runLogic;

    @Autowired
    private TargetLogic targetLogic;

    @Autowired
    private ResultLogic resultLogic;

    @Autowired
    private TraceLogic traceLogic;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static String repeat(char c, int n) {
        StringBuilder b = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            b.append(c);
        }
        return b.toString();
    }

    private RunManifest buildManifest(String evaluatorId, String evaluatorName) {
        Map<String, Object> input = new HashMap<>();
        input.put("q", "c1");
        Case caseItem = Case.of("c1", "用例-c1",
                Collections.singletonList(CaseTurn.of("c1-t1", input)));
        DatasetVersion dataset = DatasetVersion.of("dv-af", "ds-af", "数据集名", "", 1,
                DatasetVersionStatus.PUBLISHED, null, Collections.singletonList(caseItem), "",
                T0, T0, T0, "", TEAM, "u", "测试");
        TargetSnapshot target = TargetSnapshot.of(
                TargetRef.of("agentgate-demo", TargetType.AGENT, "loan-agent",
                        "loan-agent-v1-risky"),
                "Loan Agent", "demo_loan", "1",
                DemoCatalog.descriptor("loan-agent-v1-risky").contentSha256(), null, null,
                T0, "");
        EvaluatorSpec spec = EvaluatorSpec.of(evaluatorId, evaluatorName, "1", null, "state",
                evaluatorId + "_metric", null, "final_state", null, null, null, null, "",
                TEAM, "u", "测试");
        return RunManifest.of(dataset, null, target, Collections.singletonList(spec),
                Collections.singletonList(spec.id()), MetricPlan.of(), ReleaseGateSpec.of());
    }

    private EvaluationRun createCompletedRun(String runId, double score) {
        RunManifest manifest = buildManifest("ev-af", "状态评测");
        EvaluationRun run = EvaluationRun.of(runId, manifest,
                RunLifecycle.of(RunStatus.COMPLETED, T0, null, T1, T2, null),
                TEAM, "u", "测试", null, 0);
        runLogic.saveRun(run);
        String traceId = traceIdFor(runId);
        Map<String, Object> traceAttributes = new HashMap<>();
        traceAttributes.put("q", "c1");
        traceLogic.saveTrace(Trace.of(traceId, runId, "c1",
                Collections.singletonList(TraceSpan.of(traceId, SPAN, null, "操作", "turn", 0,
                        T0, T1, SpanStatus.OK, traceAttributes, null)), null, null, null));
        resultLogic.saveResults(Collections.singletonList(
                EvaluationResult.of("res-" + runId, runId, "c1", traceId, "ev-af",
                        "状态评测", "1", specHash(), EvaluatorKind.RULE, "state",
                        "ev-af_metric", EvaluatorSeverity.STANDARD,
                        score >= 0.95 ? Outcome.PASS : Outcome.FAIL, score, "原因",
                        Collections.singletonList(CheckResult.of("chk-" + runId, "检查",
                                "c1-t1", null,
                                score >= 0.95 ? Outcome.PASS : Outcome.FAIL, score, "原因",
                                null, null, false, null, null,
                                score >= 0.95 ? null
                                        : com.abchina.llmalf.agentgate.domain.model.result.FailureStage.TOOL_SELECTION,
                                score >= 0.95 ? null : 1, null)),
                        null, null,
                        score >= 0.95 ? null
                                : com.abchina.llmalf.agentgate.domain.model.result.FailureStage.TOOL_SELECTION)));
        return run;
    }

    private static String traceIdFor(String runId) {
        String hex = String.format("%032x",
                new java.math.BigInteger(1, runId.getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0, 32);
        return hex;
    }

    private String specHash() {
        EvaluatorSpec spec = EvaluatorSpec.of("ev-af", "状态评测", "1", null, "state",
                "ev-af_metric", null, "final_state", null, null, null, null, "", TEAM, "u", "测试");
        return spec.contentSha256();
    }

    @Test
    void analyticsBreaksDownByEvaluatorAndDimensions() throws Exception {
        createCompletedRun("it-run-an", 1.0);
        mockMvc.perform(get("/api/runs/it-run-an/analytics")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.run_id").value("it-run-an"))
                .andExpect(jsonPath("$.data.by_evaluator.available").value(true))
                .andExpect(jsonPath("$.data.by_evaluator.buckets[0].key").value("ev-af"))
                .andExpect(jsonPath("$.data.by_evaluator.buckets[0].passed").value(1))
                .andExpect(jsonPath("$.data.by_evaluator.buckets[0].pass_rate").value(1.0))
                .andExpect(jsonPath("$.data.by_category.available").value(true))
                .andExpect(jsonPath("$.data.by_difficulty.available").value(true))
                .andExpect(jsonPath("$.data.by_failure_type.available").value(false));

        mockMvc.perform(get("/api/runs/missing/analytics")
                        .header("user_team_id", TEAM))
                .andExpect(status().isNotFound());
    }

    @Test
    void comparisonDeltasAndCompatibilityGuard() throws Exception {
        createCompletedRun("it-run-base", 0.9);
        createCompletedRun("it-run-cand", 1.0);
        mockMvc.perform(get("/api/run-comparisons")
                        .param("baseline_run_id", "it-run-base")
                        .param("candidate_run_id", "it-run-cand")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baseline_run_id").value("it-run-base"))
                .andExpect(jsonPath("$.data.overall_score_delta").value(new java.math.BigDecimal("0.09999999999999998")))
                .andExpect(jsonPath("$.data.metric_deltas[0].level").value("overall"))
                .andExpect(jsonPath("$.data.case_deltas[0].change").value("improvement"));

        // 不同数据集内容 → 409
        EvaluationRun other = EvaluationRun.of("it-run-other", buildManifest("ev-af", "状态评测"),
                RunLifecycle.of(RunStatus.COMPLETED, T0, null, T1, T2, null),
                TEAM, "u", "测试", null, 0);
        runLogic.saveRun(other);
        mockMvc.perform(get("/api/run-comparisons")
                        .param("baseline_run_id", "it-run-base")
                        .param("candidate_run_id", "it-run-other")
                        .header("user_team_id", TEAM))
                .andExpect(status().isConflict());
    }

    @Test
    void runLineageGraphAndDatasetLineage() throws Exception {
        targetLogic.saveTargetDescriptor(DemoCatalog.descriptor("loan-agent-v1-risky"));
        // 存 dataset + published version 供 dataset lineage 查询
        Map<String, Object> input = new HashMap<>();
        input.put("q", "c1");
        Case caseItem = Case.of("c1", "用例-c1",
                Collections.singletonList(CaseTurn.of("c1-t1", input)));
        DatasetVersion dataset = DatasetVersion.of("dv-af", "ds-af", "数据集名", "", 1,
                DatasetVersionStatus.PUBLISHED, null, Collections.singletonList(caseItem), "",
                T0, T0, T0, "", TEAM, "u", "测试");
        com.abchina.llmalf.agentgate.logic.DatasetLogic datasetLogic =
                context.getBean(com.abchina.llmalf.agentgate.logic.DatasetLogic.class);
        datasetLogic.saveDataset(com.abchina.llmalf.agentgate.domain.model.dataset.Dataset.of(
                "ds-af", "数据集名", "", false, T0, T0, TEAM, "u", "测试"));
        datasetLogic.saveDatasetVersion(dataset);
        createCompletedRun("it-run-lg", 1.0);

        mockMvc.perform(get("/api/runs/it-run-lg/lineage")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.root_node_id").value("run:it-run-lg"))
                .andExpect(jsonPath("$.data.nodes[?(@.kind=='run')].external_id")
                        .value("it-run-lg"))
                .andExpect(jsonPath("$.data.nodes[?(@.kind=='dataset')].external_id")
                        .value("ds-af"))
                .andExpect(jsonPath("$.data.nodes[?(@.kind=='agent')].external_id")
                        .value("loan-agent"))
                .andExpect(jsonPath(
                        "$.data.nodes[?(@.kind=='skill' && @.external_id=='loan_approval')]")
                        .isArray())
                .andExpect(jsonPath("$.data.nodes[?(@.kind=='evaluator')].external_id")
                        .value("ev-af"))
                .andExpect(jsonPath("$.data.edges[?(@.relation=='uses_dataset')]").isArray())
                .andExpect(jsonPath("$.data.edges[?(@.relation=='evaluates_agent')]").isArray())
                .andExpect(jsonPath("$.data.edges[?(@.relation=='includes_skill')]").isArray())
                .andExpect(jsonPath("$.data.edges[?(@.relation=='uses_evaluator')]").isArray());

        mockMvc.perform(get("/api/datasets/ds-af/versions/1/lineage")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes[?(@.kind=='dataset')].external_id")
                        .value("ds-af"))
                .andExpect(jsonPath("$.data.edges[?(@.relation=='uses_dataset')]").isArray());

        mockMvc.perform(get("/api/runs/missing/lineage")
                        .header("user_team_id", TEAM))
                .andExpect(status().isNotFound());
    }

    @Test
    void stabilitySummaryAggregatesScores() throws Exception {
        RunManifest manifest = buildManifest("ev-af", "状态评测");
        for (String runId : new String[] {"it-run-st1", "it-run-st2"}) {
            EvaluationRun run = EvaluationRun.of(runId, manifest,
                    RunLifecycle.of(RunStatus.COMPLETED, T0, null, T1, T2, null),
                    TEAM, "u", "测试", null, 0);
            runLogic.saveRun(run);
            String traceId = traceIdFor(runId);
            Map<String, Object> traceAttributes = new HashMap<>();
            traceAttributes.put("q", "c1");
            traceLogic.saveTrace(Trace.of(traceId, runId, "c1",
                    Collections.singletonList(TraceSpan.of(traceId, SPAN, null, "操作",
                            "turn", 0, T0, T1, SpanStatus.OK, traceAttributes, null)),
                    null, null, null));
            resultLogic.saveResults(Collections.singletonList(
                    EvaluationResult.of("res-" + runId, runId, "c1", traceId, "ev-af",
                            "状态评测", "1", specHash(), EvaluatorKind.RULE, "state",
                            "ev-af_metric", EvaluatorSeverity.STANDARD, Outcome.PASS,
                            runId.endsWith("2") ? 1.0 : 0.9, "原因",
                            Collections.singletonList(CheckResult.of("chk-" + runId, "检查",
                                    "c1-t1", null, Outcome.PASS,
                                    runId.endsWith("2") ? 1.0 : 0.9, "原因", null, null,
                                    false, null, null, null, null, null)),
                            null, null, null)));
        }

        mockMvc.perform(put("/api/evaluation-tasks/task-st")
                        .header("user_team_id", TEAM)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"stability\",\"run_ids\":"
                                + "[\"it-run-st1\",\"it-run-st2\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/evaluation-tasks/task-st")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("stability"));

        com.abchina.llmalf.agentgate.logic.TaskLogic dbgTaskLogic = context.getBean(com.abchina.llmalf.agentgate.logic.TaskLogic.class);
        com.abchina.llmalf.agentgate.domain.model.evaluationtask.EvaluationTask dbgTask = dbgTaskLogic.getEvaluationTask("task-st");
        mockMvc.perform(get("/api/stability-experiments/task-st")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.experiment.kind").value("stability"))
                .andExpect(jsonPath("$.data.measured_runs").value(2))
                .andExpect(jsonPath("$.data.mean").value(0.95))
                .andExpect(jsonPath("$.data.complete").value(true));

        mockMvc.perform(get("/api/stability-experiments/missing")
                        .header("user_team_id", TEAM))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("unknown stability experiment"));
    }
}