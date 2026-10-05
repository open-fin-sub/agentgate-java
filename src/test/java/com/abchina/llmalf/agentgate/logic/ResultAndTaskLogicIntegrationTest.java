package com.abchina.llmalf.agentgate.logic;

import com.abchina.llmalf.agentgate.dao.RunDAO;
import com.abchina.llmalf.agentgate.dao.entity.RunEntity;
import com.abchina.llmalf.agentgate.domain.CanonicalJson;
import com.abchina.llmalf.agentgate.domain.IdentityDigest;
import com.abchina.llmalf.agentgate.domain.model.cases.Case;
import com.abchina.llmalf.agentgate.domain.model.cases.CaseTurn;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersion;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersionStatus;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorKind;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSeverity;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSpec;
import com.abchina.llmalf.agentgate.domain.model.evaluationtask.EvaluationTask;
import com.abchina.llmalf.agentgate.domain.model.evaluationtask.EvaluationTaskKind;
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
import com.abchina.llmalf.agentgate.domain.model.trace.Trace;
import com.abchina.llmalf.agentgate.domain.model.trace.TraceSpan;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Result 域与 Task 域 Logic 共库集成测试(随机 id + 事务回滚).
 */
@SpringBootTest
@Transactional
class ResultAndTaskLogicIntegrationTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T00:00:00+00:00");
    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-01-02T00:00:00+00:00");
    private static final String DESCRIPTOR_SHA = repeat('a', 64);
    private static final String TRACE = "0123456789abcdef0123456789abcdef";
    private static final String SPAN = "0123456789abcdef";

    @Autowired
    private ResultLogic resultLogic;

    @Autowired
    private TaskLogic taskLogic;

    @Autowired
    private TraceLogic traceLogic;

    @Autowired
    private RunDAO runDAO;

    private static String repeat(char c, int length) {
        StringBuilder buffer = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            buffer.append(c);
        }
        return buffer.toString();
    }

    private static RunManifest manifestOf(String seed) {
        Map<String, Object> input = new HashMap<>();
        input.put("q", "c1");
        Case caseItem = Case.of("c1", "用例-c1",
                Collections.singletonList(CaseTurn.of("c1-t1", input)));
        DatasetVersion dataset = DatasetVersion.of("dv-" + seed, "ds-" + seed, "", "", 1,
                DatasetVersionStatus.PUBLISHED, null, Collections.singletonList(caseItem), "",
                T0, T0, T0, "", "it-team", "u", "测试");
        TargetSnapshot target = TargetDescriptor_targetSnapshot(seed);
        EvaluatorSpec spec = EvaluatorSpec.of("ev-" + seed, "状态", "1", null, "state",
                "state_metric", null, "final_state", null, null, null, null, "", "it-team",
                "u", "测试");
        return RunManifest.of(dataset, null, target, Collections.singletonList(spec),
                Collections.singletonList(spec.id()), MetricPlan.of(), ReleaseGateSpec.of());
    }

    private static TargetSnapshot TargetDescriptor_targetSnapshot(String seed) {
        return TargetSnapshot.of(TargetRef.of("demo", TargetType.AGENT, "loan-" + seed, "v1"),
                "信贷助手", "python_function", "1", DESCRIPTOR_SHA, null, null, T0, "");
    }

    private static EvaluationResult resultOf(String id, String runId, String caseId,
            String traceId, String evaluatorId, String name, String version, String contentSha,
            Double score) {
        return EvaluationResult.of(id, runId, caseId, traceId, evaluatorId, name, version,
                contentSha, EvaluatorKind.RULE, "state", "state_metric",
                EvaluatorSeverity.STANDARD, Outcome.PASS, score, "原因",
                Collections.singletonList(CheckResult.of("chk-" + id, "检查", null, null,
                        Outcome.PASS, score, "原因", null, null, false, null, null, null,
                        null, null)),
                null, null, null);
    }

    private void insertRunRow(EvaluationRun run) {
        RunEntity entity = new RunEntity();
        entity.setIdKey(IdentityDigest.of(run.id()));
        entity.setId(run.id());
        entity.setStatus(run.status().wireValue());
        entity.setCreatedAt(run.createdAt().toLocalDateTime());
        entity.setUserTeamKey(IdentityDigest.of("it-team"));
        entity.setUserTeamId("it-team");
        entity.setUserId("u");
        entity.setUserName("测试");
        entity.setPayload(CanonicalJson.serialize(run.toPayload()));
        runDAO.insert(entity);
    }

    @Test
    void saveResultsValidatesBatchAndReferences() {
        String runId = "it-run-" + UUID.randomUUID();
        String seed = "r" + runId.hashCode();
        RunManifest manifest = manifestOf(seed);
        EvaluationRun run = EvaluationRun.of(runId, manifest,
                RunLifecycle.of(RunStatus.RUNNING, T0, null, T1, null, null),
                "it-team", "u", "测试", null, 0);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("k", 1);
        traceLogic.saveTrace(Trace.of(TRACE, runId, "c1",
                Collections.singletonList(TraceSpan.of(TRACE, SPAN, null, "操作", "turn", 0,
                        T0, T1, null, attributes, null)), null, null, null));

        EvaluatorSpec spec = manifest.evaluatorSpecs().get(0);
        EvaluationResult first = resultOf("res-1-" + seed, runId, "c1", TRACE, spec.id(),
                spec.name(), spec.version(), spec.contentSha256(), 1.0);

        assertEquals("unknown EvaluationRun",
                assertThrows(IllegalArgumentException.class, () -> resultLogic.saveResults(
                        Collections.singletonList(first))).getMessage());

        insertRunRow(run);

        assertEquals("EvaluationResult requires a matching Run and Trace",
                assertThrows(IllegalArgumentException.class, () -> resultLogic.saveResults(
                        Collections.singletonList(resultOf("res-bad-" + seed, runId, "c1",
                                "ffffffffffffffffffffffffffffffff", spec.id(), spec.name(),
                                spec.version(), spec.contentSha256(), 1.0)))).getMessage());

        resultLogic.saveResults(Collections.singletonList(first));
        assertEquals(1, resultLogic.listResults(runId).size());
        resultLogic.saveResults(Collections.singletonList(first));

        assertEquals("EvaluationResult is immutable",
                assertThrows(IllegalArgumentException.class, () -> resultLogic.saveResults(
                        Collections.singletonList(resultOf(first.id(), runId, "c1", TRACE,
                                spec.id(), spec.name(), spec.version(), spec.contentSha256(),
                                0.5)))).getMessage());

        assertEquals("EvaluationResult ids must be unique within a batch",
                assertThrows(IllegalArgumentException.class, () -> resultLogic.saveResults(
                        Arrays.asList(first, first))).getMessage());

        assertEquals("EvaluationResults must be unique by Run, Case, and Evaluator",
                assertThrows(IllegalArgumentException.class, () -> resultLogic.saveResults(
                        Arrays.asList(first, resultOf("res-2-" + seed, runId, "c1", TRACE,
                                spec.id(), spec.name(), spec.version(), spec.contentSha256(),
                                1.0)))).getMessage());
    }

    @Test
    void taskRunAssociationsAndImmutability() {
        String runId = "it-run-" + UUID.randomUUID();
        String seed = "t" + runId.hashCode();
        EvaluationRun run = EvaluationRun.of(runId, manifestOf(seed),
                RunLifecycle.of(RunStatus.PENDING, T0, null, null, null, null),
                "it-team", "u", "测试", null, 0);
        EvaluationTask task = EvaluationTask.of("it-task-" + UUID.randomUUID(),
                EvaluationTaskKind.SINGLE, T0, Collections.singletonList(runId), null, null,
                null);

        assertEquals("task creation requires unstarted runs",
                assertThrows(IllegalArgumentException.class, () -> taskLogic.saveTaskRuns(task,
                        Collections.singletonList(EvaluationRun.of(runId, run.manifest(),
                                RunLifecycle.of(RunStatus.RUNNING, T0, null, T1, null, null),
                                "it-team", "u", "测试", null, 0)))).getMessage());

        taskLogic.saveTaskRuns(task, Collections.singletonList(run));
        EvaluationTask stored = taskLogic.getEvaluationTask(task.id());
        assertNotNull(stored);
        assertEquals(Collections.singletonList(runId), stored.runIds());
        assertTrue(taskLogic.listEvaluationTasks().stream()
                .anyMatch(item -> item.id().equals(task.id())));

        EvaluationTask renamed = EvaluationTask.of(task.id(), EvaluationTaskKind.SINGLE, T0,
                Collections.singletonList(runId), null, null, "cred-changed");
        assertEquals("task identity and run associations are immutable",
                assertThrows(IllegalArgumentException.class, () -> taskLogic.saveTask(renamed))
                        .getMessage());
    }

    @Test
    void taskRequiresExistingRuns() {
        EvaluationTask task = EvaluationTask.of("it-task-" + UUID.randomUUID(),
                EvaluationTaskKind.SINGLE, T0, Collections.singletonList("missing-run"), null,
                null, null);
        assertEquals("unknown EvaluationRun",
                assertThrows(IllegalArgumentException.class, () -> taskLogic.saveTask(task))
                        .getMessage());
    }
}
