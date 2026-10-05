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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.abchina.llmalf.agentgate.domain.model.target.TargetSnapshot;
import com.abchina.llmalf.agentgate.domain.model.target.TargetType;
import com.abchina.llmalf.agentgate.domain.model.trace.SpanStatus;
import com.abchina.llmalf.agentgate.domain.model.trace.Trace;
import com.abchina.llmalf.agentgate.domain.model.trace.TraceSpan;
import com.abchina.llmalf.agentgate.logic.ResultLogic;
import com.abchina.llmalf.agentgate.logic.RunLogic;
import com.abchina.llmalf.agentgate.logic.TraceLogic;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class ResultControllerTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T00:00:00+00:00");
    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-01-02T00:00:00+00:00");
    private static final OffsetDateTime T2 = OffsetDateTime.parse("2026-01-03T00:00:00+00:00");
    private static final String DESCRIPTOR_SHA = repeat('a', 64);
    private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";
    private static final String SPAN = "0123456789abcdef";
    private static final String TEAM = "it-team-res";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RunLogic runLogic;

    @Autowired
    private TraceLogic traceLogic;

    @Autowired
    private ResultLogic resultLogic;

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

    private RunManifest buildManifest() {
        Map<String, Object> input = new HashMap<>();
        input.put("q", "c1");
        Case caseItem = Case.of("c1", "用例-c1",
                Collections.singletonList(CaseTurn.of("c1-t1", input)));
        DatasetVersion dataset = DatasetVersion.of("dv-res", "ds-res", "", "", 1,
                DatasetVersionStatus.PUBLISHED, null, Collections.singletonList(caseItem), "",
                T0, T0, T0, "", TEAM, "u", "测试");
        TargetSnapshot target = TargetSnapshot.of(
                TargetRef.of("demo", TargetType.AGENT, "loan", "v1"),
                "信贷助手", "python_function", "1", DESCRIPTOR_SHA, null, null, T0, "");
        EvaluatorSpec spec = EvaluatorSpec.of("ev-res", "状态", "1", null, "state",
                "state_metric", null, "final_state", null, null, null, null, "", TEAM, "u", "测试");
        return RunManifest.of(dataset, null, target, Collections.singletonList(spec),
                Collections.singletonList(spec.id()), MetricPlan.of(), ReleaseGateSpec.of());
    }

    private EvaluationRun createCompletedRun(String runId) {
        RunManifest manifest = buildManifest();
        EvaluationRun run = EvaluationRun.of(runId, manifest,
                RunLifecycle.of(RunStatus.COMPLETED, T0, null, T1, T2, null),
                TEAM, "u", "测试", null, 0);
        runLogic.saveRun(run);
        Map<String, Object> traceAttributes = new HashMap<>();
        traceAttributes.put("q", "c1");
        traceLogic.saveTrace(Trace.of(TRACE_ID, runId, "c1",
                Collections.singletonList(TraceSpan.of(TRACE_ID, SPAN, null, "操作", "turn", 0,
                        T0, T1, SpanStatus.OK, traceAttributes, null)), null, null, null));
        resultLogic.saveResults(Collections.singletonList(
                EvaluationResult.of("res-" + runId, runId, "c1", TRACE_ID, "ev-res", "状态",
                        "1", specHash(), EvaluatorKind.RULE, "state", "state_metric",
                        EvaluatorSeverity.STANDARD, Outcome.PASS, 1.0, "原因",
                        Collections.singletonList(CheckResult.of("chk-" + runId, "检查",
                                "c1-t1", null, Outcome.PASS, 1.0, "原因", null, null, false,
                                null, null, null, null, null)), null, null, null)));
        return run;
    }

    private String specHash() {
        EvaluatorSpec spec = EvaluatorSpec.of("ev-res", "状态", "1", null, "state",
                "state_metric", null, "final_state", null, null, null, null, "", TEAM, "u", "测试");
        return spec.contentSha256();
    }

    @Test
    void reportAssemblesMetricsAndGate() throws Exception {
        createCompletedRun("it-run-report");
        mockMvc.perform(get("/api/runs/it-run-report")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.run.id").value("it-run-report"))
                .andExpect(jsonPath("$.data.metrics[0].key").value("overall"))
                .andExpect(jsonPath("$.data.metrics[0].level").value("overall"))
                .andExpect(jsonPath("$.data.metrics[0].score").value(1.0))
                .andExpect(jsonPath("$.data.metrics[0].passed").value(1))
                .andExpect(jsonPath("$.data.metrics[0].total").value(1))
                .andExpect(jsonPath("$.data.release_gate.outcome").value("pass"))
                .andExpect(jsonPath("$.data.release_gate.reason_code").value("threshold_met"))
                .andExpect(jsonPath("$.data.release_gate.score").value(1.0));
    }

    @Test
    void reportRequiresCompletedRun() throws Exception {
        RunManifest manifest = buildManifest();
        runLogic.saveRun(EvaluationRun.of("it-run-pending", manifest,
                RunLifecycle.of(RunStatus.PENDING, T0, null, null, null, null),
                TEAM, "u", "测试", null, 0));
        mockMvc.perform(get("/api/runs/it-run-pending")
                        .header("user_team_id", TEAM))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("EvaluationReport requires a completed EvaluationRun"));
    }

    @Test
    void samplesReturnResultsRegardlessOfStatus() throws Exception {
        createCompletedRun("it-run-samples");
        mockMvc.perform(get("/api/runs/it-run-samples/samples")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.run.id").value("it-run-samples"))
                .andExpect(jsonPath("$.data.results[0].case_id").value("c1"))
                .andExpect(jsonPath("$.data.complete").value(true));
    }

    @Test
    void traceIsRedacted() throws Exception {
        createCompletedRun("it-run-trace");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("api_key", "sk-secret");
        attributes.put("trace_id", TRACE_ID);
        attributes.put("email", "user@example.com");
        attributes.put("note", "bearer abc.def.ghi");
        traceLogic.saveTrace(Trace.of(TRACE_ID, "it-run-trace", "c1",
                Collections.singletonList(TraceSpan.of(TRACE_ID, SPAN, null, "操作", "turn", 0,
                        T0, T1, SpanStatus.OK, attributes, null)), null, null, null));

        mockMvc.perform(get("/api/runs/it-run-trace/traces/c1")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trace_id").value(TRACE_ID))
                .andExpect(jsonPath("$.data.spans[0].attributes.api_key")
                        .value("[redacted]"))
                .andExpect(jsonPath("$.data.spans[0].attributes.trace_id").value(TRACE_ID))
                .andExpect(jsonPath("$.data.spans[0].attributes.email")
                        .value("[redacted]"))
                .andExpect(jsonPath("$.data.spans[0].attributes.note").value("[redacted]"));

        mockMvc.perform(get("/api/runs/it-run-trace/traces/c9")
                        .header("user_team_id", TEAM))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("unknown Trace: it-run-trace/c9"));
    }

    @Test
    void historicalCaseAndWriteback() throws Exception {
        createCompletedRun("it-run-wb");

        mockMvc.perform(get("/api/runs/it-run-wb/cases/c1")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.run_id").value("it-run-wb"))
                .andExpect(jsonPath("$.data.dataset_id").value("ds-res"))
                .andExpect(jsonPath("$.data.case.id").value("c1"))
                .andExpect(jsonPath("$.data.results.length()").value(1));

        mockMvc.perform(get("/api/runs/it-run-wb/cases/cX")
                        .header("user_team_id", TEAM))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("EvaluationRun did not execute Case: it-run-wb/cX"));

        mockMvc.perform(post("/api/runs/it-run-wb/cases/c1/writeback")
                        .header("user_team_id", TEAM)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"case\":{\"id\":\"c1\",\"name\":\"改名\",\"turns\":"
                                + "[{\"id\":\"c1-t1\",\"input\":{\"q\":\"p\"}}]}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Case has no failed Result: it-run-wb/c1"));
    }

    @Test
    void overviewCountsTeamData() throws Exception {
        createCompletedRun("it-run-ov");
        mockMvc.perform(get("/api/overview")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_runs").isNotEmpty())
                .andExpect(jsonPath("$.data.completed_runs").value(1))
                .andExpect(jsonPath("$.data.latest.run.id").value("it-run-ov"));
    }
}
