package com.abchina.llmalf.agentgate.service.impl;

import com.abchina.llmalf.agentgate.common.AgentException;
import com.abchina.llmalf.agentgate.common.UserContext;
import com.abchina.llmalf.agentgate.common.UserContextHolder;
import com.abchina.llmalf.agentgate.domain.model.cases.Case;
import com.abchina.llmalf.agentgate.domain.model.cases.CaseTurn;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersion;
import com.abchina.llmalf.agentgate.domain.model.dataset.DatasetVersionStatus;
import com.abchina.llmalf.agentgate.domain.model.evaluationtask.EvaluationTask;
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
import com.abchina.llmalf.agentgate.integration.BjsJobDispatcher;
import com.abchina.llmalf.agentgate.logic.DatasetLogic;
import com.abchina.llmalf.agentgate.logic.RunLogic;
import com.abchina.llmalf.agentgate.logic.TargetLogic;
import com.abchina.llmalf.agentgate.logic.TaskLogic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RunLaunchServiceTest {

    public static final String TEAM = "service-team";
    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-01-01T00:01:00Z");
    private static final OffsetDateTime T2 = OffsetDateTime.parse("2026-01-01T00:02:00Z");

    @Mock private RunLogic runLogic;
    @Mock private DatasetLogic datasetLogic;
    @Mock private TargetLogic targetLogic;
    @Mock private TaskLogic taskLogic;
    @Mock private BjsJobDispatcher dispatcher;
    @Mock private EvaluatorSelection evaluatorSelection;

    private RunLaunchService service;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(new UserContext(TEAM, "user-1", "User"));
        service = new RunLaunchService(runLogic, datasetLogic, targetLogic, taskLogic,
                dispatcher, evaluatorSelection, 10, 10);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @ParameterizedTest
    @EnumSource(value = RunStatus.class, names = {"COMPLETED", "FAILED", "CANCELLED"})
    void rerunPersistsExactManifestBeforeDispatch(RunStatus sourceStatus) {
        EvaluationRun source = run("source-" + sourceStatus.wireValue(), sourceStatus, "v1");
        AtomicReference<EvaluationRun> persisted = arrangePersistence(source);

        EvaluationRun result = service.rerunRun(source.id());

        assertNotEquals(source.id(), result.id());
        assertSame(source.manifest(), result.manifest());
        assertEquals(RunStatus.PENDING, result.status());
        assertEquals(source.userTeamId(), result.userTeamId());
        assertEquals(source.apiKey(), result.apiKey());
        assertEquals(0, result.dispatchAttempts());
        InOrder order = inOrder(taskLogic, dispatcher);
        order.verify(taskLogic).saveTaskRuns(any(EvaluationTask.class), any());
        order.verify(dispatcher).submit(result.id());
        assertSame(result, persisted.get());
    }

    @ParameterizedTest
    @EnumSource(value = RunStatus.class,
            names = {"SCHEDULED", "PENDING", "WAITING", "RUNNING"})
    void rerunRejectsActiveSource(RunStatus status) {
        EvaluationRun source = run("active-" + status.wireValue(), status, "v1");
        when(runLogic.getRun(source.id(), TEAM)).thenReturn(source);

        AgentException error = assertThrows(AgentException.class,
                () -> service.rerunRun(source.id()));

        assertEquals(409, error.getHttpStatus());
        assertEquals("cannot rerun " + status.wireValue() + " EvaluationRun",
                error.getMessage());
        verify(taskLogic, never()).saveTaskRuns(any(), any());
    }

    @Test
    void rerunHidesUnknownOrOtherTeamSource() {
        when(runLogic.getRun("missing", TEAM)).thenReturn(null);

        AgentException error = assertThrows(AgentException.class,
                () -> service.rerunRun("missing"));

        assertEquals(404, error.getHttpStatus());
        assertEquals("unknown EvaluationRun: missing", error.getMessage());
    }

    private AtomicReference<EvaluationRun> arrangePersistence(EvaluationRun source) {
        AtomicReference<EvaluationRun> persisted = new AtomicReference<>();
        when(runLogic.getRun(anyString(), eq(TEAM))).thenAnswer(invocation -> {
            String runId = invocation.getArgument(0);
            if (source.id().equals(runId)) {
                return source;
            }
            EvaluationRun current = persisted.get();
            return current != null && current.id().equals(runId) ? current : null;
        });
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<EvaluationRun> runs = invocation.getArgument(1);
            persisted.set(runs.get(0));
            return null;
        }).when(taskLogic).saveTaskRuns(any(EvaluationTask.class), any());
        return persisted;
    }

    public static EvaluationRun run(String id, RunStatus status, String targetVersion) {
        HashMap<String, Object> input = new HashMap<>();
        input.put("q", "test");
        Case item = Case.of("case-1", "Case",
                Collections.singletonList(CaseTurn.of("turn-1", input)));
        DatasetVersion dataset = DatasetVersion.of("dataset-version-" + id, "dataset-1",
                "Dataset", "", 1, DatasetVersionStatus.PUBLISHED, null,
                Collections.singletonList(item), "", T0, T0, T0, "", TEAM,
                "user-1", "User");
        TargetSnapshot target = TargetSnapshot.of(
                TargetRef.of("demo", TargetType.AGENT, "agent-1", targetVersion),
                "Agent", "python_function", "1", repeat('a', 64), null, null, T0, "");
        EvaluatorSpec evaluator = EvaluatorSpec.of("eval-1", "Evaluator", "1", null,
                "state", "metric", null, "final_state", null, null, null, null,
                "", TEAM, "user-1", "User");
        RunManifest manifest = RunManifest.of(dataset, null, target,
                Collections.singletonList(evaluator), Collections.singletonList(evaluator.id()),
                MetricPlan.of(), ReleaseGateSpec.of());
        RunLifecycle lifecycle;
        if (status == RunStatus.SCHEDULED) {
            lifecycle = RunLifecycle.of(status, T0, T1, null, null, null);
        } else if (status == RunStatus.RUNNING) {
            lifecycle = RunLifecycle.of(status, T0, null, T1, null, null);
        } else if (status == RunStatus.COMPLETED) {
            lifecycle = RunLifecycle.of(status, T0, null, T1, T2, null);
        } else if (status == RunStatus.FAILED) {
            lifecycle = RunLifecycle.of(status, T0, null, T1, T2, "failed");
        } else if (status == RunStatus.CANCELLED) {
            lifecycle = RunLifecycle.of(status, T0, null, null, T1, null);
        } else {
            lifecycle = RunLifecycle.of(status, T0, null, null, null, null);
        }
        return EvaluationRun.of(id, manifest, lifecycle, TEAM, "user-1", "User",
                "api-key-id", 0);
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }
}
