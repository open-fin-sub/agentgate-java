package com.abchina.llmalf.agentgate.domain.model.evaluator;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Evaluator 目录构造与解析行为测试.
 */
class EvaluatorTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T00:00:00+00:00");
    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-01-02T00:00:00+00:00");

    @Test
    void ofAppliesDefaults() {
        Evaluator evaluator = Evaluator.of("e1", "名称", null, null, false, T0, T0, null, null, null);
        assertEquals(EvaluatorSource.USER, evaluator.source());
        assertEquals(false, evaluator.enabled());
        assertEquals("", evaluator.description());
        assertEquals("", evaluator.userTeamId());
    }

    @Test
    void nullTimestampsFallBackToUtcNow() {
        Evaluator evaluator = Evaluator.of("e1", "名称", "", null, false, null, null, "", "", "");
        assertEquals(ZoneOffset.UTC, evaluator.createdAt().getOffset());
        assertTrue(!evaluator.updatedAt().isBefore(evaluator.createdAt()));
    }

    @Test
    void builtinMustBeEnabled() {
        assertEquals("built-in Evaluator must be enabled",
                assertThrows(IllegalArgumentException.class, () -> Evaluator.of(
                        "e1", "名称", "", EvaluatorSource.BUILTIN, false,
                        T0, T0, "", "", "")).getMessage());
        Evaluator builtin = Evaluator.of("e1", "名称", "", EvaluatorSource.BUILTIN, true,
                T0, T0, "", "", "");
        assertEquals(EvaluatorSource.BUILTIN, builtin.source());
        assertTrue(builtin.enabled());
    }

    @Test
    void userSourceMayBeDisabled() {
        Evaluator user = Evaluator.of("e1", "名称", "", EvaluatorSource.USER, false,
                T0, T0, "", "", "");
        assertEquals(false, user.enabled());
    }

    @Test
    void identityAndTimestampValidation() {
        assertEquals("Evaluator name must not be blank",
                assertThrows(IllegalArgumentException.class, () -> Evaluator.of(
                        "e1", " ", "", null, false, T0, T0, "", "", "")).getMessage());
        assertEquals("Evaluator id must not be blank",
                assertThrows(IllegalArgumentException.class, () -> Evaluator.of(
                        null, "n", "", null, false, T0, T0, "", "", "")).getMessage());
        assertEquals("updated_at must not precede created_at",
                assertThrows(IllegalArgumentException.class, () -> Evaluator.of(
                        "e1", "n", "", null, false, T1, T0, "", "", "")).getMessage());
    }

    @Test
    void fromPayloadGeneratesIdAndDefaults() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "新评测器");
        Evaluator first = Evaluator.fromPayload(payload);
        Evaluator second = Evaluator.fromPayload(payload);
        assertTrue(!first.id().equals(second.id()));
        assertEquals(EvaluatorSource.USER, first.source());
        assertEquals(false, first.enabled());
    }

    @Test
    void fromPayloadRejectsBadSource() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "e1");
        payload.put("name", "n");
        payload.put("source", "mystery");
        assertEquals("Input should be 'builtin' or 'user'",
                assertThrows(IllegalArgumentException.class,
                        () -> Evaluator.fromPayload(payload)).getMessage());
    }
}
