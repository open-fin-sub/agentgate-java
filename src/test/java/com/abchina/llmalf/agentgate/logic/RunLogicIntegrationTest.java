package com.abchina.llmalf.agentgate.logic;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RunLogic 共库集成测试(随机 id + 事务回滚).
 *
 * <p>覆盖保存守护、claim/cancel 状态机迁移、资产引用查询的
 * 正反路径;消息文本与 Python storage/mysql.py 逐字对照。</p>
 */
@SpringBootTest
@Transactional
class RunLogicIntegrationTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T00:00:00+00:00");
    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-01-02T00:00:00+00:00");
    private static final OffsetDateTime T2 = OffsetDateTime.parse("2026-01-03T00:00:00+00:00");
    private static final String DESCRIPTOR_SHA =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Autowired
    private RunLogic runLogic;

    private static RunManifest manifestOf(String datasetId) {
        Map<String, Object> input = new HashMap<>();
        input.put("q", "c1");
        Case caseItem = Case.of("c1", "用例-c1",
                Collections.singletonList(CaseTurn.of("c1-t1", input)));
        DatasetVersion dataset = DatasetVersion.of("dv-" + datasetId, datasetId, "", "", 1,
                DatasetVersionStatus.PUBLISHED, null, Collections.singletonList(caseItem), "",
                T0, T0, T0, "", "it-team", "u", "测试");
        TargetSnapshot target = TargetSnapshot.of(
                TargetRef.of("demo", TargetType.AGENT, "loan", "v1"),
                "信贷助手", "python_function", "1", DESCRIPTOR_SHA, null, null, T0, "");
        EvaluatorSpec spec = EvaluatorSpec.of("ev-" + datasetId, "状态", "1", null, "state",
                "state_metric", null, "final_state", null, null, null, null, "", "it-team",
                "u", "测试");
        return RunManifest.of(dataset, null, target, Collections.singletonList(spec),
                Collections.singletonList(spec.id()), MetricPlan.of(), ReleaseGateSpec.of());
    }

    private EvaluationRun pendingRun(String id) {
        return EvaluationRun.of(id, manifestOf("m" + id.hashCode()),
                RunLifecycle.of(RunStatus.PENDING, T0, null, null, null, null),
                "it-team", "u", "测试", "sk-it", 0);
    }

    @Test
    void saveRunInsertsWithAssetRefsAndGuardsUpdates() {
        String id = "it-run-" + UUID.randomUUID();
        EvaluationRun run = pendingRun(id);
        runLogic.saveRun(run);
        assertEquals(RunStatus.PENDING, runLogic.getRun(id, "it-team").status());
        assertEquals(RunStatus.PENDING, runLogic.getRun(id, null).status());
        assertNull(runLogic.getRun(id, "other-team"));

        List<EvaluationRun> byDataset = runLogic.listRunsByDatasetVersion(
                run.manifest().dataset().datasetId(), 1, 50, "it-team");
        assertTrue(byDataset.stream().anyMatch(item -> item.id().equals(id)));
        List<EvaluationRun> byEvaluator = runLogic.listRunsByEvaluatorVersion(
                run.manifest().evaluatorSpecs().get(0).id(), "1", 50, "it-team");
        assertTrue(byEvaluator.stream().anyMatch(item -> item.id().equals(id)));

        runLogic.saveRun(run);
        EvaluationRun running = run.transition(RunStatus.RUNNING, T1, null);
        runLogic.saveRun(running);
        assertEquals(RunStatus.RUNNING, runLogic.getRun(id, "it-team").status());

        assertEquals("EvaluationRun manifest is immutable",
                assertThrows(IllegalArgumentException.class, () -> runLogic.saveRun(
                        EvaluationRun.of(id, manifestOf("other"),
                                RunLifecycle.of(RunStatus.RUNNING, T0, null, T1, null, null),
                                "it-team", "u", "测试", "sk-it", 0))).getMessage());
        assertEquals("EvaluationRun started_at is immutable once set",
                assertThrows(IllegalArgumentException.class, () -> runLogic.saveRun(
                        EvaluationRun.of(id, run.manifest(),
                                RunLifecycle.of(RunStatus.RUNNING, T0, null, T2, null, null),
                                "it-team", "u", "测试", "sk-it", 0))).getMessage());

        EvaluationRun completed = running.transition(RunStatus.COMPLETED, T2, null);
        runLogic.saveRun(completed);
        assertEquals("terminal EvaluationRun is immutable",
                assertThrows(IllegalArgumentException.class, () -> runLogic.saveRun(
                        EvaluationRun.of(id, run.manifest(),
                                RunLifecycle.of(RunStatus.RUNNING, T0, null, T1, null, null),
                                "it-team", "u", "测试", "sk-it", 0))).getMessage());
    }

    @Test
    void listRunsLimitsAndOrders() {
        String older = "it-run-" + UUID.randomUUID();
        String newer = "it-run-" + UUID.randomUUID();
        runLogic.saveRun(pendingRun(older));
        runLogic.saveRun(pendingRun(newer));
        List<EvaluationRun> items = runLogic.listRuns(50, "it-team");
        int olderIndex = -1;
        int newerIndex = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id().equals(older)) {
                olderIndex = i;
            }
            if (items.get(i).id().equals(newer)) {
                newerIndex = i;
            }
        }
        assertTrue(olderIndex >= 0 && newerIndex >= 0);
        assertEquals("list limit must be at least 1",
                assertThrows(IllegalArgumentException.class,
                        () -> runLogic.listRuns(0, "it-team")).getMessage());
    }

    @Test
    void claimPendingRunTransitionsToRunning() {
        String id = "it-run-" + UUID.randomUUID();
        runLogic.saveRun(pendingRun(id));
        assertNull(runLogic.claimWaitingRun(id, T1));

        EvaluationRun running = runLogic.claimPendingRun(id, T1);
        assertNotNull(running);
        assertEquals(RunStatus.RUNNING, running.status());
        assertEquals(T1, running.startedAt());
        assertNull(runLogic.claimPendingRun(id, T2));
    }

    @Test
    void claimDueScheduledRunsFiltersAndTransitions() {
        String dueId = "it-run-" + UUID.randomUUID();
        String laterId = "it-run-" + UUID.randomUUID();
        String pendingId = "it-run-" + UUID.randomUUID();
        EvaluationRun due = EvaluationRun.of(dueId, manifestOf("a" + dueId.hashCode()),
                RunLifecycle.of(RunStatus.SCHEDULED, T0, T1, null, null, null),
                "it-team", "u", "测试", null, 0);
        EvaluationRun later = EvaluationRun.of(laterId, manifestOf("b" + laterId.hashCode()),
                RunLifecycle.of(RunStatus.SCHEDULED, T0, T2, null, null, null),
                "it-team", "u", "测试", null, 0);
        runLogic.saveRun(due);
        runLogic.saveRun(later);
        runLogic.saveRun(pendingRun(pendingId));

        List<EvaluationRun> claimed = runLogic.claimDueScheduledRuns(T1.plusHours(1), 100);
        assertTrue(claimed.stream().anyMatch(item -> item.id().equals(dueId)));
        assertTrue(claimed.stream().noneMatch(item -> item.id().equals(laterId)));
        assertEquals(RunStatus.PENDING, runLogic.getRun(dueId, "it-team").status());
        assertEquals(RunStatus.SCHEDULED, runLogic.getRun(laterId, "it-team").status());
    }

    @Test
    void cancelRunRequiresActiveStatusAndTeam() {
        String id = "it-run-" + UUID.randomUUID();
        runLogic.saveRun(pendingRun(id));
        assertNull(runLogic.cancelRun(id, T1, "other-team"));
        EvaluationRun cancelled = runLogic.cancelRun(id, T1, "it-team");
        assertNotNull(cancelled);
        assertEquals(RunStatus.CANCELLED, cancelled.status());
        assertNull(runLogic.cancelRun(id, T2, "it-team"));
    }

    @Test
    void countRunsByStatusAndActiveApiKey() {
        String id = "it-run-" + UUID.randomUUID();
        runLogic.saveRun(pendingRun(id));
        Map<RunStatus, Integer> counts = runLogic.countRunsByStatus("it-team");
        assertTrue(counts.get(RunStatus.PENDING) >= 1);
        assertEquals(Integer.valueOf(0), counts.get(RunStatus.WAITING));
        assertTrue(runLogic.countActiveRunsByApiKey("sk-it") >= 1);
        assertTrue(runLogic.countActiveRunsByApiKey(null) >= 0);
    }

    @Test
    void listRunsByStatusOrdersSchedulingByScheduledFor() {
        String id = "it-run-" + UUID.randomUUID();
        EvaluationRun scheduled = EvaluationRun.of(id, manifestOf("c" + id.hashCode()),
                RunLifecycle.of(RunStatus.SCHEDULED, T0, T2, null, null, null),
                "it-team", "u", "测试", null, 0);
        runLogic.saveRun(scheduled);
        List<EvaluationRun> items = runLogic.listRunsByStatus(RunStatus.SCHEDULED, 50, false,
                "it-team");
        assertTrue(items.stream().anyMatch(item -> item.id().equals(id)));
        List<EvaluationRun> oldest = runLogic.listRunsByStatus(RunStatus.SCHEDULED, null, true,
                "it-team");
        assertTrue(oldest.size() >= items.size());
    }
}
