package com.abchina.llmalf.agentgate.domain.model.metric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * MetricPlan 行为测试.
 */
class MetricPlanTest {

    @Test
    void defaultsAreProtocolConstants() {
        MetricPlan plan = MetricPlan.of();
        assertEquals("p1-equal-mean", plan.id());
        assertEquals("1", plan.version());
    }

    @Test
    void identityValidation() {
        assertEquals("MetricPlan id must not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> MetricPlan.of(" ", "1")).getMessage());
        assertEquals("MetricPlan version must not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> MetricPlan.of("p", " ")).getMessage());
    }

    @Test
    void fromPayloadAppliesDefaults() {
        MetricPlan plan = MetricPlan.fromPayload(new java.util.HashMap<>());
        assertEquals("p1-equal-mean", plan.id());
        MetricPlan custom = MetricPlan.fromPayload(
                new java.util.HashMap<>(java.util.Collections.singletonMap("id", "custom")));
        assertEquals("custom", custom.id());
        assertEquals("1", custom.version());
    }
}
