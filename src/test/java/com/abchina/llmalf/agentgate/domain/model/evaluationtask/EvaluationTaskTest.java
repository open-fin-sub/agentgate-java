package com.abchina.llmalf.agentgate.domain.model.evaluationtask;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EvaluationTask 聚焦行为测试.
 */
class EvaluationTaskTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T00:00:00+00:00");

    private static String hashOf(int length) {
        StringBuilder buffer = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            buffer.append('a');
        }
        return buffer.toString();
    }

    @Test
    void singleTaskDefaults() {
        EvaluationTask task = EvaluationTask.of("task-1", EvaluationTaskKind.SINGLE, T0,
                Collections.singletonList("run-1"), null, null, null);
        assertTrue(task.staticReportIds().isEmpty());
        assertTrue(task.gitCommitRefs().isEmpty());
        assertEquals(null, task.credentialId());
        assertEquals("2026-01-01T00:00:00Z",
                ((java.util.Map<?, ?>) task.toPayload()).get("created_at"));
    }

    @Test
    void stabilityBounds() {
        EvaluationTask.of("task-1", EvaluationTaskKind.STABILITY, T0,
                Arrays.asList("r1", "r2"), null, null, null);
        List<String> twenty = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            twenty.add("run-" + i);
        }
        EvaluationTask.of("task-1", EvaluationTaskKind.STABILITY, T0, twenty, null, null, null);
        List<String> twentyOne = new ArrayList<>(twenty);
        twentyOne.add("run-x");
        assertEquals("stability requires 2 to 20 runs",
                assertThrows(IllegalArgumentException.class, () -> EvaluationTask.of(
                        "task-1", EvaluationTaskKind.STABILITY, T0, twentyOne,
                        null, null, null)).getMessage());
        assertEquals("stability uses one target snapshot",
                assertThrows(IllegalArgumentException.class, () -> EvaluationTask.of(
                        "task-1", EvaluationTaskKind.STABILITY, T0, Arrays.asList("r1", "r2"),
                        Arrays.asList("s1", "s2"), null, null)).getMessage());
    }

    @Test
    void singleAbCardinality() {
        assertEquals("single requires exactly 1 runs",
                assertThrows(IllegalArgumentException.class, () -> EvaluationTask.of(
                        "task-1", EvaluationTaskKind.SINGLE, T0,
                        Arrays.asList("r1", "r2"), null, null, null)).getMessage());
        assertEquals("ab requires exactly 2 runs",
                assertThrows(IllegalArgumentException.class, () -> EvaluationTask.of(
                        "task-1", EvaluationTaskKind.AB, T0,
                        Collections.singletonList("r1"), null, null, null)).getMessage());
        assertEquals("too many static reports",
                assertThrows(IllegalArgumentException.class, () -> EvaluationTask.of(
                        "task-1", EvaluationTaskKind.SINGLE, T0,
                        Collections.singletonList("r1"),
                        Arrays.asList("s1", "s2"), null, null)).getMessage());
        assertEquals("Git references must match the number of runs",
                assertThrows(IllegalArgumentException.class, () -> EvaluationTask.of(
                        "task-1", EvaluationTaskKind.AB, T0, Arrays.asList("r1", "r2"),
                        null, Collections.singletonList(hashOf(40)), null)).getMessage());
    }

    @Test
    void gitHashFormats() {
        EvaluationTask.of("task-1", EvaluationTaskKind.SINGLE, T0,
                Collections.singletonList("r1"), null,
                Collections.singletonList(hashOf(64)), null);
        assertEquals("Git references must be full commit hashes",
                assertThrows(IllegalArgumentException.class, () -> EvaluationTask.of(
                        "task-1", EvaluationTaskKind.SINGLE, T0,
                        Collections.singletonList("r1"), null,
                        Collections.singletonList("abc123"), null)).getMessage());
    }

    @Test
    void referenceListsAreImmutableAndCopied() {
        List<String> source = new ArrayList<>(Collections.singletonList("run-1"));
        EvaluationTask task = EvaluationTask.of("task-1", EvaluationTaskKind.SINGLE, T0,
                source, null, null, null);
        assertThrows(UnsupportedOperationException.class, () -> task.runIds().clear());
        source.clear();
        assertEquals(1, task.runIds().size(), "of must copy the list");
    }

    @Test
    void identifierMessageHasNoClassPrefix() {
        assertEquals("identifier must not be blank",
                assertThrows(IllegalArgumentException.class, () -> EvaluationTask.of(
                        " ", EvaluationTaskKind.SINGLE, T0,
                        Collections.singletonList("r1"), null, null, null)).getMessage());
        assertEquals("identifier must not be blank",
                assertThrows(IllegalArgumentException.class, () -> EvaluationTask.of(
                        "task-1", EvaluationTaskKind.SINGLE, T0,
                        Collections.singletonList("r1"), null, null, " ")).getMessage());
    }

    @Test
    void kindRoundTrip() {
        assertEquals(EvaluationTaskKind.AB, EvaluationTaskKind.fromWireValue("ab"));
        assertEquals("Input should be 'single', 'ab' or 'stability'",
                assertThrows(IllegalArgumentException.class,
                        () -> EvaluationTaskKind.fromWireValue("batch")).getMessage());
    }
}
