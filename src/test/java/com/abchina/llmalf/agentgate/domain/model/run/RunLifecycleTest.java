package com.abchina.llmalf.agentgate.domain.model.run;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RunLifecycle 值对象行为测试(golden 未覆盖部分).
 *
 * <p>覆盖:时区归一化、null 防御、equals/hashCode;
 * 状态规则与错误消息的跨语言一致性由 RunTransitionsGoldenTest 对拍。</p>
 */
class RunLifecycleTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T00:00:00+00:00");

    @Test
    void timestampsNormalizeToUtc() {
        OffsetDateTime beijing = OffsetDateTime.of(2026, 1, 1, 8, 0, 0, 0, ZoneOffset.ofHours(8));
        RunLifecycle lifecycle = RunLifecycle.of(RunStatus.PENDING, beijing, null, null, null, null);
        assertEquals(ZoneOffset.UTC, lifecycle.createdAt().getOffset());
        assertEquals(T0, lifecycle.createdAt());

        ZonedDateTime tokyo = ZonedDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.ofHours(9));
        RunLifecycle started = RunLifecycle.of(RunStatus.RUNNING, beijing, null,
                OffsetDateTime.from(tokyo), null, null);
        assertEquals(ZoneOffset.UTC, started.startedAt().getOffset());
        assertEquals(T0.plusHours(1), started.startedAt());
    }

    @Test
    void nullArgumentsAreRejected() {
        IllegalArgumentException nullStatus = assertThrows(IllegalArgumentException.class,
                () -> RunLifecycle.of(null, T0, null, null, null, null));
        assertEquals("status must not be null", nullStatus.getMessage());

        IllegalArgumentException nullCreated = assertThrows(IllegalArgumentException.class,
                () -> RunLifecycle.of(RunStatus.PENDING, null, null, null, null, null));
        assertEquals("EvaluationRun created_at must be timezone-aware", nullCreated.getMessage());
    }

    @Test
    void valueSemantics() {
        RunLifecycle first = RunLifecycle.of(RunStatus.RUNNING, T0, null, T0.plusHours(1), null, null);
        RunLifecycle same = RunLifecycle.of(RunStatus.RUNNING, T0, null, T0.plusHours(1), null, null);
        RunLifecycle other = RunLifecycle.of(RunStatus.PENDING, T0, null, null, null, null);

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, other);
        assertNotEquals(first, null);
        assertNotEquals(first, new Object());
    }

    @Test
    void toStringContainsStatusAndTimestamps() {
        RunLifecycle lifecycle = RunLifecycle.of(RunStatus.FAILED, T0, null,
                T0.plusHours(1), T0.plusHours(2), "boom");
        String text = lifecycle.toString();
        assertTrue(text.contains("failed"));
        assertTrue(text.contains("boom"));
        assertTrue(text.contains("2026-01-01T00:00"));
    }
}
