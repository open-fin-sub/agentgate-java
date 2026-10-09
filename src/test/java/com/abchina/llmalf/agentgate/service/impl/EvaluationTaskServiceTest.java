package com.abchina.llmalf.agentgate.service.impl;

import com.abchina.llmalf.agentgate.common.AgentException;
import com.abchina.llmalf.agentgate.domain.model.evaluationtask.EvaluationTask;
import com.abchina.llmalf.agentgate.domain.model.evaluationtask.EvaluationTaskKind;
import com.abchina.llmalf.agentgate.domain.model.run.EvaluationRun;
import com.abchina.llmalf.agentgate.domain.model.run.RunStatus;
import com.abchina.llmalf.agentgate.logic.RunLogic;
import com.abchina.llmalf.agentgate.logic.TaskLogic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationTaskServiceTest {

    @Mock private TaskLogic taskLogic;
    @Mock private RunLogic runLogic;

    @Test
    void listAndGetApplyTeamVisibility() {
        EvaluationTask visible = task("visible", "run-visible");
        EvaluationTask hidden = task("hidden", "run-hidden");
        when(taskLogic.listEvaluationTasks()).thenReturn(Arrays.asList(visible, hidden));
        when(taskLogic.getEvaluationTask("visible")).thenReturn(visible);
        when(taskLogic.getEvaluationTask("hidden")).thenReturn(hidden);
        when(runLogic.getRun("run-visible", RunLaunchServiceTest.TEAM))
                .thenReturn(RunLaunchServiceTest.run("run-visible", RunStatus.PENDING, "v1"));

        EvaluationTaskService service = new EvaluationTaskService(taskLogic, runLogic);

        assertEquals(Collections.singletonList(visible),
                service.listVisibleTasks(RunLaunchServiceTest.TEAM));
        assertSame(visible, service.getVisibleTask("visible", RunLaunchServiceTest.TEAM));
        assertEquals(404, assertThrows(AgentException.class,
                () -> service.getVisibleTask("hidden", RunLaunchServiceTest.TEAM))
                .getHttpStatus());
    }

    @Test
    void saveRejectsRunOutsideTeam() {
        EvaluationTask task = task("task-1", "hidden-run");
        when(runLogic.getRun("hidden-run", RunLaunchServiceTest.TEAM)).thenReturn(null);

        AgentException error = assertThrows(AgentException.class,
                () -> new EvaluationTaskService(taskLogic, runLogic)
                        .saveTask(task, RunLaunchServiceTest.TEAM));

        assertEquals(404, error.getHttpStatus());
        assertEquals("task references an unknown run", error.getMessage());
    }

    @Test
    void saveDelegatesValidatedTask() {
        EvaluationTask task = task("task-1", "run-1");
        EvaluationRun run = RunLaunchServiceTest.run("run-1", RunStatus.PENDING, "v1");
        when(runLogic.getRun("run-1", RunLaunchServiceTest.TEAM)).thenReturn(run);
        when(taskLogic.saveTask(task)).thenReturn(task);

        EvaluationTask saved = new EvaluationTaskService(taskLogic, runLogic)
                .saveTask(task, RunLaunchServiceTest.TEAM);

        assertSame(task, saved);
    }

    private static EvaluationTask task(String id, String runId) {
        return EvaluationTask.of(id, EvaluationTaskKind.SINGLE, null,
                Collections.singletonList(runId), null, null, null);
    }
}
