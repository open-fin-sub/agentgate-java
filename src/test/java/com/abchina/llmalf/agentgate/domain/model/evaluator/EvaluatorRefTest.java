package com.abchina.llmalf.agentgate.domain.model.evaluator;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * EvaluatorRef 构造与解析行为测试.
 */
class EvaluatorRefTest {

    @Test
    void ofAcceptsNullWeight() {
        EvaluatorRef ref = EvaluatorRef.of("child-a", "3", null);
        assertEquals("child-a", ref.evaluatorId());
        assertEquals("3", ref.evaluatorVersion());
        assertNull(ref.weight());
    }

    @Test
    void weightMustBeStrictlyPositive() {
        assertEquals("Input should be greater than 0",
                assertThrows(IllegalArgumentException.class,
                        () -> EvaluatorRef.of("a", "1", 0.0)).getMessage());
        assertEquals("Input should be greater than 0",
                assertThrows(IllegalArgumentException.class,
                        () -> EvaluatorRef.of("a", "1", -0.5)).getMessage());
        assertEquals("Input should be greater than 0",
                assertThrows(IllegalArgumentException.class,
                        () -> EvaluatorRef.of("a", "1", Double.NaN)).getMessage());
    }

    @Test
    void identityValidation() {
        assertEquals("EvaluatorRef evaluator_id must not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> EvaluatorRef.of(" ", "1", null)).getMessage());
        assertEquals("EvaluatorRef evaluator_version must not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> EvaluatorRef.of("a", " ", null)).getMessage());
    }

    @Test
    void toPayloadIncludesNullWeight() {
        Map<String, Object> payload = (Map<String, Object>) EvaluatorRef.of("a", "1", null).toPayload();
        assertNull(payload.get("weight"));
        Map<String, Object> weighted = (Map<String, Object>) EvaluatorRef.of("a", "1", 1.5).toPayload();
        assertEquals(1.5, weighted.get("weight"));
    }

    @Test
    void fromPayloadRejectsBadWeightType() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("evaluator_id", "a");
        payload.put("evaluator_version", "1");
        payload.put("weight", "heavy");
        assertEquals("Input should be a valid number",
                assertThrows(IllegalArgumentException.class,
                        () -> EvaluatorRef.fromPayload(payload)).getMessage());
    }
}
