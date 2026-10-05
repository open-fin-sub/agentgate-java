package com.abchina.llmalf.agentgate.domain.model.run;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RunTransitions 状态机行为测试(golden 未覆盖部分).
 *
 * <p>覆盖:occurred_at 缺省取当前时间、null 防御与 isLegal 查询;
 * 完整迁移语义由 RunTransitionsGoldenTest 对拍。</p>
 */
class RunTransitionsTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T00:00:00+00:00");

    @Test
    void applyDefaultsOccurredAtToUtcNow() {
        RunLifecycle pending = RunLifecycle.of(RunStatus.PENDING, T0, null, null, null, null);
        RunLifecycle running = RunTransitions.apply(pending, RunStatus.RUNNING, null, null);
        assertEquals(RunStatus.RUNNING, running.status());
        assertNotNull(running.startedAt());
        assertTrue(!running.startedAt().isBefore(T0));
        assertTrue(!running.startedAt().isAfter(OffsetDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(1)));

        RunLifecycle failed = RunTransitions.apply(running, RunStatus.FAILED, null, "boom");
        assertEquals(RunStatus.FAILED, failed.status());
        assertNotNull(failed.completedAt());
        assertEquals("boom", failed.error());
    }

    @Test
    void nullArgumentsAreRejected() {
        RunLifecycle pending = RunLifecycle.of(RunStatus.PENDING, T0, null, null, null, null);
        assertEquals("run must not be null",
                assertThrows(IllegalArgumentException.class,
                        () -> RunTransitions.apply(null, RunStatus.RUNNING, T0, null)).getMessage());
        assertEquals("newStatus must not be null",
                assertThrows(IllegalArgumentException.class,
                        () -> RunTransitions.apply(pending, null, T0, null)).getMessage());
    }

    @Test
    void isLegalMirrorsTransitionTable() {
        assertTrue(RunTransitions.isLegal(RunStatus.SCHEDULED, RunStatus.PENDING));
        assertTrue(RunTransitions.isLegal(RunStatus.SCHEDULED, RunStatus.CANCELLED));
        assertTrue(RunTransitions.isLegal(RunStatus.PENDING, RunStatus.WAITING));
        assertTrue(RunTransitions.isLegal(RunStatus.PENDING, RunStatus.RUNNING));
        assertTrue(RunTransitions.isLegal(RunStatus.PENDING, RunStatus.FAILED));
        assertTrue(RunTransitions.isLegal(RunStatus.PENDING, RunStatus.CANCELLED));
        assertTrue(RunTransitions.isLegal(RunStatus.WAITING, RunStatus.PENDING));
        assertTrue(RunTransitions.isLegal(RunStatus.WAITING, RunStatus.FAILED));
        assertTrue(RunTransitions.isLegal(RunStatus.WAITING, RunStatus.CANCELLED));
        assertTrue(RunTransitions.isLegal(RunStatus.RUNNING, RunStatus.COMPLETED));
        assertTrue(RunTransitions.isLegal(RunStatus.RUNNING, RunStatus.FAILED));
        assertTrue(RunTransitions.isLegal(RunStatus.RUNNING, RunStatus.CANCELLED));

        assertTrue(!RunTransitions.isLegal(RunStatus.SCHEDULED, RunStatus.RUNNING));
        assertTrue(!RunTransitions.isLegal(RunStatus.COMPLETED, RunStatus.RUNNING));
        assertTrue(!RunTransitions.isLegal(RunStatus.FAILED, RunStatus.PENDING));
        assertTrue(!RunTransitions.isLegal(RunStatus.CANCELLED, RunStatus.CANCELLED));
        assertTrue(!RunTransitions.isLegal(RunStatus.PENDING, RunStatus.PENDING));
    }
}
