package com.abchina.llmalf.agentgate.domain.model.report;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * EvaluationReport 聚焦行为测试(golden 未覆盖部分).
 */
class EvaluationReportTest {

    @Test
    void nullRequiredObjectsAreRejected() {
        assertEquals("Field required",
                assertThrows(IllegalArgumentException.class, () -> EvaluationReport.of(
                        null, null, null, null)).getMessage());
    }

    @Test
    void fromPayloadRejectsBadContainerTypes() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("run", "not-a-map");
        payload.put("results", null);
        payload.put("metrics", null);
        payload.put("release_gate", null);
        assertEquals("Input should be a valid dictionary",
                assertThrows(IllegalArgumentException.class,
                        () -> EvaluationReport.fromPayload(payload)).getMessage());
    }
}
