package com.abchina.llmalf.agentgate.controller;

import com.abchina.llmalf.agentgate.domain.model.cases.Case;
import com.abchina.llmalf.agentgate.domain.model.cases.CaseTurn;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersion;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersionStatus;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSpec;
import com.abchina.llmalf.agentgate.domain.model.gate.ReleaseGateSpec;
import com.abchina.llmalf.agentgate.domain.model.metric.MetricPlan;
import com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun;
import com.abchina.llmalf.agentgate.domain.model.run.RunLifecycle;
import com.abchina.llmalf.agentgate.domain.model.run.RunManifest;
import com.abchina.llmalf.agentgate.domain.model.run.RunStatus;
import com.abchina.llmalf.agentgate.domain.model.target.TargetRef;
import com.abchina.llmalf.agentgate.domain.model.target.TargetSnapshot;
import com.abchina.llmalf.agentgate.domain.model.target.TargetType;
import com.abchina.llmalf.agentgate.logic.RunLogic;
import com.abchina.llmalf.agentgate.logic.ResultLogic;
import com.abchina.llmalf.agentgate.logic.TraceLogic;
import com.abchina.llmalf.agentgate.service.impl.RunReaderService;
import com.abchina.llmalf.agentgate.domain.model.trace.Trace;
import com.abchina.llmalf.agentgate.domain.model.trace.TraceSpan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Run 读取/任务关联/稳定性列表端点集成测试(共库回滚).
 *
 * <p>BJS 提交指向不可达地址,触发派发器 mock 降级分支(对齐 Python MOCK 语义),
 * 使 launch 流程测试不依赖 shell 环境变量与外部端点。</p>
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "AGENTGATE_BJS_SUBMIT_URL=http://127.0.0.1:59999/bjs/submit",
        "AGENTGATE_BJS_JOB_ID=agentgate-eval"})
class RunAndTaskControllerTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T00:00:00+00:00");
    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-01-02T00:00:00+00:00");
    private static final String DESCRIPTOR_SHA = repeat('a', 64);
    private static final String TEAM = "it-team-run";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RunLogic runLogic;

    @Autowired
    private TraceLogic traceLogic;

    @Autowired
    private com.abchina.llmalf.agentgate.logic.TargetLogic targetLogic;

    @Autowired
    private com.abchina.llmalf.agentgate.logic.EvaluatorLogic evaluatorLogic;

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

    private EvaluationRun createRun(String runId, RunStatus status) {
        return createRun(runId, status, runId);
    }

    private EvaluationRun createRun(String runId, RunStatus status, String seed) {
        Map<String, Object> input = new HashMap<>();
        input.put("q", "c1");
        Case caseItem = Case.of("c1", "用例-c1",
                Collections.singletonList(CaseTurn.of("c1-t1", input)));
        DatasetVersion dataset = DatasetVersion.of("dv-" + seed, "ds-" + seed, "", "", 1,
                DatasetVersionStatus.PUBLISHED, null, Collections.singletonList(caseItem), "",
                T0, T0, T0, "", TEAM, "u", "测试");
        TargetSnapshot target = TargetSnapshot.of(
                TargetRef.of("demo", TargetType.AGENT, "loan", "v1"),
                "信贷助手", "python_function", "1", DESCRIPTOR_SHA, null, null, T0, "");
        EvaluatorSpec spec = EvaluatorSpec.of("ev-" + seed, "状态", "1", null, "state",
                "state_metric", null, "final_state", null, null, null, null, "", TEAM, "u", "测试");
        RunManifest manifest = RunManifest.of(dataset, null, target,
                Collections.singletonList(spec), Collections.singletonList(spec.id()),
                MetricPlan.of(), ReleaseGateSpec.of());
        RunLifecycle lifecycle;
        if (status == RunStatus.RUNNING) {
            lifecycle = RunLifecycle.of(RunStatus.RUNNING, T0, null, T1, null, null);
        } else if (status == RunStatus.COMPLETED) {
            lifecycle = RunLifecycle.of(RunStatus.COMPLETED, T0, null, T1, T1, null);
        } else {
            lifecycle = RunLifecycle.of(status, T0, null, null, null, null);
        }
        EvaluationRun run = EvaluationRun.of(runId, manifest, lifecycle, TEAM, "u", "测试",
                null, 0);
        runLogic.saveRun(run);
        return run;
    }

    @Test
    void listRunsFiltersByStatusAndTeam() throws Exception {
        createRun("it-run-list-1", RunStatus.PENDING);
        createRun("it-run-list-2", RunStatus.RUNNING);

        mockMvc.perform(get("/api/runs")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='it-run-list-1')]").isArray())
                .andExpect(jsonPath("$.data[?(@.id=='it-run-list-2')]").isArray());

        mockMvc.perform(get("/api/runs")
                        .param("status", "running")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='it-run-list-1')]").doesNotExist())
                .andExpect(jsonPath("$.data[?(@.id=='it-run-list-2')]").isArray());

        mockMvc.perform(get("/api/runs")
                        .header("user_team_id", "other-team"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='it-run-list-1')]").doesNotExist());
    }

    @Test
    void runStatusProjectionAndManifest() throws Exception {
        createRun("it-run-proj", RunStatus.PENDING);
        mockMvc.perform(get("/api/runs/it-run-proj/status")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.run_id").value("it-run-proj"))
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.dataset_id").value("ds-it-run-proj"))
                .andExpect(jsonPath("$.data.dataset_name").value(""))
                .andExpect(jsonPath("$.data.target_name").value("信贷助手"))
                .andExpect(jsonPath("$.data.target_version").value("v1"))
                .andExpect(jsonPath("$.data.total_cases").value(1))
                .andExpect(jsonPath("$.data.completed_cases").value(0))
                .andExpect(jsonPath("$.data.progress").value(0.0))
                .andExpect(jsonPath("$.data.queue_position").isNotEmpty());

        mockMvc.perform(get("/api/runs/it-run-proj/manifest")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataset.dataset_id").value("ds-it-run-proj"))
                .andExpect(jsonPath("$.data.evaluator_specs[0].id").value("ev-it-run-proj"));

        mockMvc.perform(get("/api/runs/missing-run/status")
                        .header("user_team_id", TEAM))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("unknown EvaluationRun: missing-run"));
    }

    @Test
    void activityAggregatesQueuedAndFailsStaleRunning() throws Exception {
        createRun("it-run-act-p", RunStatus.PENDING);
        createRun("it-run-act-r", RunStatus.RUNNING);

        mockMvc.perform(get("/api/runs/activity")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status_counts.pending").value(1))
                .andExpect(jsonPath("$.data.queued[?(@.run_id=='it-run-act-p')]").isArray())
                .andExpect(jsonPath("$.data.running.length()").value(0))
                .andExpect(jsonPath(
                        "$.data.recent[?(@.run_id=='it-run-act-r')]").isArray());
    }

    private void createRunsSharingManifest(String id1, String id2) {
        Map<String, Object> input = new HashMap<>();
        input.put("q", "c1");
        Case caseItem = Case.of("c1", "用例-c1",
                Collections.singletonList(CaseTurn.of("c1-t1", input)));
        DatasetVersion dataset = DatasetVersion.of("dv-share", "ds-share", "", "", 1,
                DatasetVersionStatus.PUBLISHED, null, Collections.singletonList(caseItem), "",
                T0, T0, T0, "", TEAM, "u", "测试");
        TargetSnapshot target = TargetSnapshot.of(
                TargetRef.of("demo", TargetType.AGENT, "loan", "v1"),
                "信贷助手", "python_function", "1", DESCRIPTOR_SHA, null, null, T0, "");
        EvaluatorSpec spec = EvaluatorSpec.of("ev-share", "状态", "1", null, "state",
                "state_metric", null, "final_state", null, null, null, null, "", TEAM, "u", "测试");
        RunManifest manifest = RunManifest.of(dataset, null, target,
                Collections.singletonList(spec), Collections.singletonList(spec.id()),
                MetricPlan.of(), ReleaseGateSpec.of());
        for (String runId : new String[] {id1, id2}) {
            runLogic.saveRun(EvaluationRun.of(runId, manifest,
                    RunLifecycle.of(RunStatus.PENDING, T0, null, null, null, null),
                    TEAM, "u", "测试", null, 0));
        }
    }

    @Test
    void taskLifecycleAndStabilityList() throws Exception {
        createRunsSharingManifest("it-run-task-1", "it-run-task-2");

        mockMvc.perform(put("/api/evaluation-tasks/task-1")
                        .header("user_team_id", TEAM)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"stability\",\"run_ids\":"
                                + "[\"it-run-task-1\",\"it-run-task-2\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("stability"))
                .andExpect(jsonPath("$.data.run_ids.length()").value(2));

        mockMvc.perform(get("/api/evaluation-tasks")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='task-1')]").isArray());

        mockMvc.perform(get("/api/stability-experiments")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='task-1')]").isArray());

        mockMvc.perform(get("/api/evaluation-tasks/missing")
                        .header("user_team_id", TEAM))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("unknown evaluation task"));

        // 稳定性任务基数不足 → 409(pydantic ValidationError 文本逐字复刻)
        mockMvc.perform(put("/api/evaluation-tasks/task-2")
                        .header("user_team_id", TEAM)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"stability\",\"run_ids\":[\"missing-run\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "1 validation error for EvaluationTask\n"
                        + "  Value error, stability requires 2 to 20 runs "
                        + "[type=value_error, input_value={'id': 'task-2', "
                        + "'kind': ...'static_report_ids': ()}, input_type=dict]\n"
                        + "    For further information visit "
                        + "https://errors.pydantic.dev/2.13/v/value_error"));

        // 单 Run 任务引用不存在的 Run → 404(存在性校验)
        mockMvc.perform(put("/api/evaluation-tasks/task-3")
                        .header("user_team_id", TEAM)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"single\",\"run_ids\":[\"missing-run\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("task references an unknown run"));
    }


    @Test
    void otlpIngestNormalizesAndPersistsTraces() throws Exception {
        createRunsSharingManifest("it-run-otlp", "it-run-otlp-2");
        String payload = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get("src/test/resources/contract/otlp-sample.json")),
                java.nio.charset.StandardCharsets.UTF_8);
        mockMvc.perform(post("/v1/traces")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted_spans").value(2))
                .andExpect(jsonPath("$.code").doesNotExist());

        mockMvc.perform(get("/api/runs/it-run-otlp/traces/c1")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trace_id").value("0123456789abcdef0123456789abc001"))
                .andExpect(jsonPath("$.data.spans.length()").value(2))
                .andExpect(jsonPath("$.data.spans[0].attributes[\"agentgate.turn.id\"]")
                        .value("t1"))
                .andExpect(jsonPath("$.data.final_output.done").value(true));
    }

    @Test
    void otlpIngestRejectsMalformedPayloads() throws Exception {
        mockMvc.perform(post("/v1/traces")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"resourceSpans\":\"not-a-list\"}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/v1/traces")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"resourceSpans\":[{\"scopeSpans\":[{\"spans\":["
                                + "{\"traceId\":\"0123456789abcdef0123456789abc001\","
                                + "\"spanId\":\"0123456789abcdef\"}]}]}]}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void evaluationLaunchAndCancelLifecycle() throws Exception {
        // 建数据集(用 service 层已验证的 REST 链)
        String datasetBody = "{\"name\":\"launch数据集\",\"description\":\"\"}";
        String datasetId = com.fasterxml.jackson.databind.JsonNode.class.cast(
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                        mockMvc.perform(post("/api/datasets")
                                .header("user_team_id", TEAM)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(datasetBody))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()))
                .at("/data/dataset/id").asText();
        String caseBody = "{\"id\":\"c1\",\"name\":\"用例\",\"turns\":"
                + "[{\"id\":\"t1\",\"input\":{\"q\":\"p\"}}]}";
        mockMvc.perform(post("/api/datasets/" + datasetId + "/drafts/cases")
                        .header("user_team_id", TEAM)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(caseBody))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/datasets/" + datasetId + "/drafts/publish")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                targetLogic.saveTargetDescriptor(
                        com.abchina.llmalf.agentgate.service.impl.DemoCatalog.descriptor(
                                "loan-agent-v1-risky")));
        String launchBody = "{\"version\":\"loan-agent-v1-risky\","
                + "\"dataset_id\":\"" + datasetId + "\","
                + "\"dataset_version\":1}";
        String runId = com.fasterxml.jackson.databind.JsonNode.class.cast(
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                        mockMvc.perform(post("/api/evaluations")
                                .header("user_team_id", TEAM)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(launchBody))
                        .andExpect(status().isAccepted())
                        .andExpect(jsonPath("$.data.status").value("pending"))
                        .andReturn().getResponse().getContentAsString()))
                .at("/data/run_id").asText();
        org.junit.jupiter.api.Assertions.assertTrue(runId != null && !runId.isEmpty(),
                "launch must return run_id");

        mockMvc.perform(post("/api/runs/" + runId + "/cancel")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("cancelled"));
    }

    @Test
    void skillAnalysisReturns503ByDecision() throws Exception {
        mockMvc.perform(post("/api/evaluations/skill-analysis")
                        .header("user_team_id", TEAM)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"loan-agent-v1-risky\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Skill analysis is unavailable"));
    }

    @Test
    void cancelSemanticsFollowPython() throws Exception {
        // 未知 Run → 404
        mockMvc.perform(post("/api/runs/missing-run/cancel")
                        .header("user_team_id", TEAM))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("unknown EvaluationRun: missing-run"));

        // PENDING → 200 取消
        createRunsSharingManifest("it-run-cancel-1", "it-run-cancel-2");
        mockMvc.perform(post("/api/runs/it-run-cancel-1/cancel")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("cancelled"));

        // 已取消 → 幂等 200
        mockMvc.perform(post("/api/runs/it-run-cancel-1/cancel")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("cancelled"));

        // 终态(COMPLETED) → 409
        createRunWithStatus("it-run-cancel-done", RunStatus.COMPLETED);
        mockMvc.perform(post("/api/runs/it-run-cancel-done/cancel")
                        .header("user_team_id", TEAM))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("cannot cancel completed EvaluationRun"));
    }

    @Test
    void rerunRequiresTerminalSourceAndPersistsSingleTask() throws Exception {
        createRunWithStatus("it-run-rerun-src", RunStatus.COMPLETED);
        mockMvc.perform(post("/api/runs/it-run-rerun-src/rerun")
                        .header("user_team_id", TEAM))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("pending"));

        // 活跃源 → 409
        createRunWithStatus("it-run-rerun-live", RunStatus.RUNNING);
        mockMvc.perform(post("/api/runs/it-run-rerun-live/rerun")
                        .header("user_team_id", TEAM))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("cannot rerun running EvaluationRun"));

        // 未知源 → 404
        mockMvc.perform(post("/api/runs/missing-run/rerun")
                        .header("user_team_id", TEAM))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("unknown EvaluationRun: missing-run"));
    }

    @Test
    void listRunsValidatesPaginationWithPydanticShape() throws Exception {
        mockMvc.perform(get("/api/runs?limit=0")
                        .header("user_team_id", TEAM))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("Input should be greater than or equal to 1"))
                .andExpect(jsonPath("$.data[0].type").value("greater_than_equal"))
                .andExpect(jsonPath("$.data[0].loc[1]").value("limit"))
                .andExpect(jsonPath("$.data[0].ctx.ge").value(1));

        mockMvc.perform(get("/api/runs?limit=201")
                        .header("user_team_id", TEAM))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("Input should be less than or equal to 200"));

        mockMvc.perform(get("/api/runs?status=bogus")
                        .header("user_team_id", TEAM))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data[0].type").value("enum"))
                .andExpect(jsonPath("$.data[0].ctx.expected").value(
                        "'scheduled', 'pending', 'waiting', 'running', "
                                + "'completed', 'failed' or 'cancelled'"));

        mockMvc.perform(get("/api/runs?limit=abc")
                        .header("user_team_id", TEAM))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data[0].type").value("int_parsing"));

        mockMvc.perform(get("/api/runs/activity?recent_limit=101")
                        .header("user_team_id", TEAM))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("Input should be less than or equal to 100"));
    }

    @Test
    void abLaunchValidatesVariantsAndPersistsTask() throws Exception {
        String abDatasetId = publishDataset("ds-ab", "ds-ab");
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
            targetLogic.saveTargetDescriptor(
                    com.abchina.llmalf.agentgate.service.impl.DemoCatalog.descriptor(
                            "loan-agent-v1-risky"));
            targetLogic.saveTargetDescriptor(
                    com.abchina.llmalf.agentgate.service.impl.DemoCatalog.descriptor(
                            "loan-agent-v2-fixed"));
        });
        // 同版本 → 422(变体校验)
        mockMvc.perform(post("/api/run-comparisons")
                        .header("user_team_id", TEAM)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"baseline_version\":\"loan-agent-v1-risky\","
                                + "\"candidate_version\":\"loan-agent-v1-risky\","
                                + "\"dataset_id\":\"" + abDatasetId + "\","
                                + "\"dataset_version\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("A/B variants must use different Agent versions"));

        // 异版本 → 202 + 任务落库(kind=ab, 2 runs)
        String body = mockMvc.perform(post("/api/run-comparisons")
                        .header("user_team_id", TEAM)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"baseline_version\":\"loan-agent-v1-risky\","
                                + "\"candidate_version\":\"loan-agent-v2-fixed\","
                                + "\"dataset_id\":\"" + abDatasetId + "\","
                                + "\"dataset_version\":1}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.baseline.run_id").isNotEmpty())
                .andExpect(jsonPath("$.data.baseline.status").value("pending"))
                .andExpect(jsonPath("$.data.candidate.status").value("pending"))
                .andReturn().getResponse().getContentAsString();
        String baselineId = com.fasterxml.jackson.databind.JsonNode.class.cast(
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(body))
                .at("/data/baseline/run_id").asText();
        mockMvc.perform(get("/api/evaluation-tasks/" + baselineId)
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("ab"))
                .andExpect(jsonPath("$.data.run_ids.length()").value(2));
    }

    @Test
    void stabilityLaunchCreatesTaskWithSharedManifest() throws Exception {
        String stabDatasetId = publishDataset("ds-stab", "ds-stab");
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> targetLogic
                .saveTargetDescriptor(com.abchina.llmalf.agentgate.service.impl.DemoCatalog
                        .descriptor("loan-agent-v1-risky")));
        mockMvc.perform(post("/api/stability-experiments")
                        .header("user_team_id", TEAM)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"loan-agent-v1-risky\","
                                + "\"dataset_id\":\"" + stabDatasetId + "\"," 
                                + "\"dataset_version\":1,\"repetitions\":2}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.kind").value("stability"))
                .andExpect(jsonPath("$.data.run_ids.length()").value(2))
                .andExpect(jsonPath("$.data.run_ids[0]").isNotEmpty());
    }

    @Test
    void launchSelectsPublishedUserEvaluatorVersion() throws Exception {
        String userEvDatasetId = publishDataset("ds-userev", "ds-userev");
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> targetLogic
                .saveTargetDescriptor(com.abchina.llmalf.agentgate.service.impl.DemoCatalog
                        .descriptor("loan-agent-v1-risky")));
        // 发布一个用户评测器(最新版本回退路径)
        String evaluatorId = "it-ev-launch-" + UUID.randomUUID().toString().substring(0, 8);
        com.abchina.llmalf.agentgate.domain.model.evaluator.Evaluator evaluator =
                com.abchina.llmalf.agentgate.domain.model.evaluator.Evaluator.of(evaluatorId,
                        "评测器", "描述",
                        com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSource.USER,
                        true, T0, T0, TEAM, "u", "测试");
        com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorDraft draft =
                com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorDraft.of(
                        "draft-" + evaluatorId, evaluatorId, null,
                        com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorKind.RULE,
                        "state", "state_metric",
                        com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSeverity.STANDARD,
                        "final_state", "1", null, null, null, T0, T0, TEAM, "u", "测试");
        evaluatorLogic.saveEvaluatorWithDraft(evaluator, draft);
        evaluatorLogic.publishEvaluatorDraft(draft.id(),
                com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSpec.of(
                        evaluatorId, "评测器", "1", draft.kind(), draft.dimension(),
                        draft.metric(), draft.severity(), draft.implementationId(),
                        draft.implementationVersion(), null, null, null,
                        "", TEAM, "u", "测试"));

        mockMvc.perform(post("/api/evaluations")
                        .header("user_team_id", TEAM)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"loan-agent-v1-risky\","
                                + "\"dataset_id\":\"" + userEvDatasetId + "\"," 
                                + "\"dataset_version\":1,"
                                + "\"evaluator_ids\":[\"" + evaluatorId + "\"]}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andDo(result -> {
                    String runId = com.fasterxml.jackson.databind.JsonNode.class.cast(
                            new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                                    result.getResponse().getContentAsString()))
                            .at("/data/run_id").asText();
                    mockMvc.perform(get("/api/runs/" + runId + "/manifest")
                                    .header("user_team_id", TEAM))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.data.evaluator_specs[0].id")
                                    .value(evaluatorId));
                });

        // 未发布评测器 → 422
        mockMvc.perform(post("/api/evaluations")
                        .header("user_team_id", TEAM)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"loan-agent-v1-risky\","
                                + "\"dataset_id\":\"" + userEvDatasetId + "\"," 
                                + "\"dataset_version\":1,"
                                + "\"evaluator_ids\":[\"never-published\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("unknown Evaluator: never-published"));
    }

    @Test
    void lineageValidationBranchesFollowPython() throws Exception {
        createRunsSharingManifest("it-run-lin-1", "it-run-lin-2");
        mockMvc.perform(get("/api/skills/src-x/skill-y/versions/v1/lineage")
                        .header("user_team_id", TEAM))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("unknown Skill version: src-x/skill-y/v1"));

        mockMvc.perform(get("/api/targets/demo/agent/loan/versions/v99/lineage")
                        .header("user_team_id", TEAM))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("unknown TargetDescriptor version"));

        mockMvc.perform(get("/api/evaluators/no-such-ev/versions/v1/lineage")
                        .header("user_team_id", TEAM))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("unknown Evaluator version: no-such-ev/v1"));

        mockMvc.perform(get("/api/runs/missing-run/lineage")
                        .header("user_team_id", TEAM))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("unknown EvaluationRun: missing-run"));
    }

    private String publishDataset(String ignoredId, String name) throws Exception {
        String datasetId = com.fasterxml.jackson.databind.JsonNode.class.cast(
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                        mockMvc.perform(post("/api/datasets")
                                .header("user_team_id", TEAM)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"" + name + "\",\"description\":\"\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()))
                .at("/data/dataset/id").asText();
        String caseBody = "{\"id\":\"c1\",\"name\":\"用例\",\"turns\":"
                + "[{\"id\":\"t1\",\"input\":{\"q\":\"p\"}}]}";
        mockMvc.perform(post("/api/datasets/" + datasetId + "/drafts/cases")
                        .header("user_team_id", TEAM)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(caseBody))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/datasets/" + datasetId + "/drafts/publish")
                        .header("user_team_id", TEAM))
                .andExpect(status().isOk());
        return datasetId;
    }

    private void createRunWithStatus(String id, RunStatus status) {
        Map<String, Object> input = new HashMap<>();
        input.put("q", "c1");
        Case caseItem = Case.of("c1", "用例-c1",
                Collections.singletonList(CaseTurn.of("c1-t1", input)));
        DatasetVersion dataset = DatasetVersion.of("dv-" + id, "ds-" + id, "", "", 1,
                DatasetVersionStatus.PUBLISHED, null, Collections.singletonList(caseItem),
                "", T0, T0, T0, "", TEAM, "u", "测试");
        TargetSnapshot target = TargetSnapshot.of(
                TargetRef.of("demo", TargetType.AGENT, "loan", "v1"),
                "信贷助手", "python_function", "1", DESCRIPTOR_SHA, null, null, T0, "");
        EvaluatorSpec spec = EvaluatorSpec.of("ev-" + id, "状态", "1", null, "state",
                "state_metric", null, "final_state", null, null, null, null, "", TEAM, "u",
                "测试");
        RunManifest manifest = RunManifest.of(dataset, null, target,
                Collections.singletonList(spec), Collections.singletonList(spec.id()),
                MetricPlan.of(), ReleaseGateSpec.of());
        OffsetDateTime started = status == RunStatus.COMPLETED
                || status == RunStatus.RUNNING ? T0 : null;
        OffsetDateTime completed = status == RunStatus.COMPLETED ? T1 : null;
        runLogic.saveRun(EvaluationRun.of(id, manifest,
                RunLifecycle.of(status, T0, null, started, completed, null),
                TEAM, "u", "测试", null, 0));
    }

    @Test
    void stabilityRejectsSchedulingAndValidatesRepetitions() throws Exception {
        // 字段校验先于业务校验(pydantic 累积:缺 dataset_id/dataset_version + ge 违例)
        mockMvc.perform(post("/api/stability-experiments")
                        .header("user_team_id", TEAM)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"loan-agent-v1-risky\",\"repetitions\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("Field required; Field required; "
                                + "Input should be greater than or equal to 2"))
                .andExpect(jsonPath("$.data[0].type").value("missing"))
                .andExpect(jsonPath("$.data[0].loc[0]").value("body"))
                .andExpect(jsonPath("$.data[0].loc[1]").value("dataset_id"));

        // 完整合法字段 + 预约 → 业务校验拒绝
        mockMvc.perform(post("/api/stability-experiments")
                        .header("user_team_id", TEAM)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"loan-agent-v1-risky\","
                                + "\"dataset_id\":\"ds-x\",\"dataset_version\":1,"
                                + "\"repetitions\":3,"
                                + "\"scheduled_for\":\"2026-01-01T00:00:00Z\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("stability does not support scheduling"));

        // 越界(上界)
        mockMvc.perform(post("/api/stability-experiments")
                        .header("user_team_id", TEAM)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"loan-agent-v1-risky\","
                                + "\"dataset_id\":\"ds-x\",\"dataset_version\":1,"
                                + "\"repetitions\":21}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("Input should be less than or equal to 20"));
    }

}
