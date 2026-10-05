package com.abchina.llmalf.agentgate.domain.model.cases;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CaseTurn 构造与解析行为测试(golden 未覆盖部分).
 */
class CaseTurnTest {

    private static Map<String, Object> input(String key, Object value) {
        Map<String, Object> input = new HashMap<>();
        input.put(key, value);
        return input;
    }

    @Test
    void ofAppliesDefaultsForOptionalFields() {
        CaseTurn turn = CaseTurn.of("t1", input("q", 1));
        assertTrue(turn.expectations().isEmpty(), "expectations default to empty");
        assertEquals("", turn.notes(), "notes default to empty string");
        assertEquals("t1", turn.id());
        assertEquals(1, turn.input().get("q"));
    }

    @Test
    void nullOptionalFieldsFallBackToDefaults() {
        CaseTurn turn = CaseTurn.of("t1", input("q", 1), null, null);
        assertTrue(turn.expectations().isEmpty());
        assertEquals("", turn.notes());
    }

    @Test
    void inputIsDeepFrozen() {
        Map<String, Object> nested = new HashMap<>();
        nested.put("inner", new ArrayList<>());
        CaseTurn turn = CaseTurn.of("t1", nested);
        assertThrows(UnsupportedOperationException.class, () -> turn.input().put("late", 1));
        @SuppressWarnings("unchecked")
        List<Object> inner = (List<Object>) turn.input().get("inner");
        assertThrows(UnsupportedOperationException.class, () -> inner.add("x"));
        nested.put("late", 1);
        assertTrue(!turn.input().containsKey("late"), "of must copy the source input");
    }

    @Test
    void expectationsListIsImmutable() {
        List<com.abchina.llmalf.agentgate.domain.model.expectation.Expectation> source =
                new ArrayList<>();
        source.add(com.abchina.llmalf.agentgate.domain.model.expectation.PolicyExpectation.of(
                "e1", null, "p1"));
        CaseTurn turn = CaseTurn.of("t1", input("q", 1), source, "");
        assertThrows(UnsupportedOperationException.class, () -> turn.expectations().clear());
        source.clear();
        assertEquals(1, turn.expectations().size(), "of must copy the expectations list");
    }

    @Test
    void nonFiniteInputNumbersAreRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> CaseTurn.of("t1", input("x", Double.POSITIVE_INFINITY)));
        assertEquals("JSON numbers must be finite", error.getMessage());
    }

    @Test
    void blankOrNullIdIsRejected() {
        assertEquals("CaseTurn id must not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> CaseTurn.of(" ", input("q", 1))).getMessage());
        assertEquals("CaseTurn id must not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> CaseTurn.of(null, input("q", 1))).getMessage());
    }

    @Test
    void missingOrNullInputIsRejected() {
        assertEquals("Field required",
                assertThrows(IllegalArgumentException.class,
                        () -> CaseTurn.of("t1", null)).getMessage());
    }

    @Test
    void fromPayloadGeneratesIdAndAppliesDefaults() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("input", input("q", 1));
        CaseTurn first = CaseTurn.fromPayload(payload);
        CaseTurn second = CaseTurn.fromPayload(payload);
        assertTrue(first.id().trim().length() > 0, "generated id must not be blank");
        assertTrue(!first.id().equals(second.id()), "generated ids must differ");
        assertEquals("", first.notes());
        assertTrue(first.expectations().isEmpty());
    }

    @Test
    void fromPayloadRejectsNonListExpectations() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "t1");
        payload.put("input", input("q", 1));
        payload.put("expectations", "not-a-list");
        assertEquals("Input should be a valid list",
                assertThrows(IllegalArgumentException.class, () -> CaseTurn.fromPayload(payload))
                        .getMessage());
    }

    @Test
    void fromPayloadRejectsNonStringId() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", 42);
        payload.put("input", input("q", 1));
        assertEquals("Input should be a valid string",
                assertThrows(IllegalArgumentException.class, () -> CaseTurn.fromPayload(payload))
                        .getMessage());
    }
}
