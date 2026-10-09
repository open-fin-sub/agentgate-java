package com.abchina.llmalf.agentgate.service.impl;

import com.abchina.llmalf.agentgate.domain.model.evaluationtask.EvaluationTask;
import com.abchina.llmalf.agentgate.domain.model.evaluationtask.EvaluationTaskKind;
import com.abchina.llmalf.agentgate.domain.model.run.RunStatus;
import com.abchina.llmalf.agentgate.logic.RunLogic;
import com.abchina.llmalf.agentgate.logic.TaskLogic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StabilityServiceTest {

    @Mock private TaskLogic taskLogic;
    @Mock private RunLogic runLogic;
    @Mock private RunReaderService runReaderService;
    @Mock private ResultService resultService;

    @Test
    void listFiltersKindAndTeamVisibility() {
        EvaluationTask stability = stabilityTask();
        EvaluationTask single = EvaluationTask.of("single", EvaluationTaskKind.SINGLE, null,
                Collections.singletonList("run-1"), null, null, null);
        when(taskLogic.listEvaluationTasks()).thenReturn(Arrays.asList(stability, single));
        arrangeVisibleRuns();

        assertEquals(Collections.singletonList(stability),
                service().listExperiments(RunLaunchServiceTest.TEAM));
    }

    @Test
    void summaryExcludesUnfinishedRunsFromStatistics() {
        EvaluationTask task = stabilityTask();
        when(taskLogic.getEvaluationTask(task.id())).thenReturn(task);
        arrangeVisibleRuns();
        when(runReaderService.getRunProgress("run-1")).thenReturn(progress("pending"));
        when(runReaderService.getRunProgress("run-2")).thenReturn(progress("failed"));

        Map<String, Object> summary = service().getSummary(task.id(),
                RunLaunchServiceTest.TEAM);

        assertEquals(0, summary.get("measured_runs"));
        assertNull(summary.get("mean"));
        assertNull(summary.get("sample_variance"));
        assertFalse((Boolean) summary.get("complete"));
    }

    private StabilityService service() {
        return new StabilityService(taskLogic, runLogic, runReaderService, resultService);
    }

    private void arrangeVisibleRuns() {
        when(runLogic.getRun("run-1", RunLaunchServiceTest.TEAM))
                .thenReturn(RunLaunchServiceTest.run("run-1", RunStatus.PENDING, "v1"));
        when(runLogic.getRun("run-2", RunLaunchServiceTest.TEAM))
                .thenReturn(RunLaunchServiceTest.run("run-2", RunStatus.PENDING, "v1"));
    }

    private static EvaluationTask stabilityTask() {
        return EvaluationTask.of("stability", EvaluationTaskKind.STABILITY, null,
                Arrays.asList("run-1", "run-2"), null, null, null);
    }

    private static Map<String, Object> progress(String status) {
        Map<String, Object> progress = new HashMap<>();
        progress.put("status", status);
        return progress;
    }
}
