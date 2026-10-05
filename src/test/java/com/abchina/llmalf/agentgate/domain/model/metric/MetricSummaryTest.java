package com.abchina.llmalf.agentgate.domain.model.metric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * MetricSummary 聚焦行为测试.
 */
class MetricSummaryTest {

    @Test
    void consistentSummaryPasses() {
        MetricSummary summary = MetricSummary.of("overall", MetricSummary.LEVEL_OVERALL,
                0.9, 9, 1, 0, 0, 0, 10, 10);
        assertEquals("overall", summary.key());
        assertEquals(Double.valueOf(0.9), summary.score());
        assertEquals(10, summary.total());

        MetricSummary empty = MetricSummary.of("kind-rule", MetricSummary.LEVEL_KIND,
                null, 0, 0, 0, 0, 0, 0, 0);
        assertNull(empty.score());
    }

    @Test
    void countConsistencyRules() {
        assertEquals("total must equal all outcome counts",
                assertThrows(IllegalArgumentException.class, () -> MetricSummary.of(
                        "overall", "overall", 0.9, 9, 1, 0, 0, 0, 10, 11)).getMessage());
        assertEquals("applicable must equal passed + failed + reviewed",
                assertThrows(IllegalArgumentException.class, () -> MetricSummary.of(
                        "overall", "overall", 0.9, 9, 1, 0, 0, 0, 9, 10)).getMessage());
        assertEquals("score must exist exactly when applicable results exist",
                assertThrows(IllegalArgumentException.class, () -> MetricSummary.of(
                        "overall", "overall", null, 9, 1, 0, 0, 0, 10, 10)).getMessage());
        assertEquals("score must exist exactly when applicable results exist",
                assertThrows(IllegalArgumentException.class, () -> MetricSummary.of(
                        "k", "kind", 0.5, 0, 0, 0, 0, 0, 0, 0)).getMessage());
        assertEquals("overall level and key must be used together",
                assertThrows(IllegalArgumentException.class, () -> MetricSummary.of(
                        "state", "overall", 0.9, 9, 1, 0, 0, 0, 10, 10)).getMessage());
        assertEquals("overall level and key must be used together",
                assertThrows(IllegalArgumentException.class, () -> MetricSummary.of(
                        "overall", "dimension", 0.9, 9, 1, 0, 0, 0, 10, 10)).getMessage());
    }

    @Test
    void fieldValidation() {
        assertEquals("MetricSummary key must not be blank",
                assertThrows(IllegalArgumentException.class, () -> MetricSummary.of(
                        " ", "overall", 0.9, 1, 0, 0, 0, 0, 1, 1)).getMessage());
        assertEquals("Input should be 'overall', 'kind', 'dimension' or 'metric'",
                assertThrows(IllegalArgumentException.class, () -> MetricSummary.of(
                        "k", "weird", 0.9, 1, 0, 0, 0, 0, 1, 1)).getMessage());
        assertEquals("Input should be greater than or equal to 0",
                assertThrows(IllegalArgumentException.class, () -> MetricSummary.of(
                        "k", "kind", null, 0, 0, 0, 0, 0, 0, -1)).getMessage());
        assertEquals("Input should be greater than or equal to 0",
                assertThrows(IllegalArgumentException.class, () -> MetricSummary.of(
                        "k", "kind", -0.1, 0, 0, 0, 0, 0, 0, 0)).getMessage());
        assertEquals("Input should be less than or equal to 1",
                assertThrows(IllegalArgumentException.class, () -> MetricSummary.of(
                        "k", "kind", 1.1, 0, 0, 0, 0, 0, 0, 0)).getMessage());
    }

    @Test
    void fromPayloadAppliesZeroDefaults() {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("key", "k");
        payload.put("level", "kind");
        MetricSummary summary = MetricSummary.fromPayload(payload);
        assertEquals(0, summary.total());
        assertNull(summary.score());
    }
}
