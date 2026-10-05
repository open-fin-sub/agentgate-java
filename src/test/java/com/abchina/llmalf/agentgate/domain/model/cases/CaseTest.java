package com.abchina.llmalf.agentgate.domain.model.cases;

import com.abchina.llmalf.agentgate.domain.model.expectation.PolicyExpectation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Case 构造与解析行为测试(golden 未覆盖部分).
 */
class CaseTest {

    private static CaseTurn turn(String id) {
        Map<String, Object> input = new HashMap<>();
        input.put("q", id);
        return CaseTurn.of(id, input);
    }

    private static CaseTurn turnWithExpectation(String turnId, String expectationId) {
        Map<String, Object> input = new HashMap<>();
        input.put("q", 1);
        return CaseTurn.of(turnId, input,
                Collections.singletonList(PolicyExpectation.of(expectationId, null, "p")), "");
    }

    @Test
    void ofAppliesDefaults() {
        Case model = Case.of("c1", "名称", Collections.singletonList(turn("t1")));
        assertEquals(CaseCategory.POSITIVE, model.category());
        assertEquals(CaseDifficulty.MEDIUM, model.difficulty());
        assertTrue(model.tags().isEmpty());
        assertEquals("", model.notes());
        assertTrue(model.initialState().isEmpty());
        assertFalse(model.isMultiTurn());
    }

    @Test
    void nullOptionalFieldsFallBackToDefaults() {
        Case model = Case.of("c1", "名称", Collections.singletonList(turn("t1")),
                null, null, null, null, null);
        assertEquals(CaseCategory.POSITIVE, model.category());
        assertEquals(CaseDifficulty.MEDIUM, model.difficulty());
        assertTrue(model.tags().isEmpty());
        assertEquals("", model.notes());
        assertTrue(model.initialState().isEmpty());
    }

    @Test
    void isMultiTurnReflectsTurnCount() {
        assertFalse(Case.of("c1", "n", Collections.singletonList(turn("t1"))).isMultiTurn());
        assertTrue(Case.of("c1", "n", Arrays.asList(turn("t1"), turn("t2"))).isMultiTurn());
    }

    @Test
    void turnsAndTagsAreImmutable() {
        List<CaseTurn> turnSource = new ArrayList<>(Collections.singletonList(turn("t1")));
        Case model = Case.of("c1", "n", turnSource);
        assertThrows(UnsupportedOperationException.class, () -> model.turns().clear());
        turnSource.clear();
        assertEquals(1, model.turns().size(), "of must copy the turns list");

        Case tagged = Case.of("c1", "n", Collections.singletonList(turn("t1")),
                null, null, null, new ArrayList<>(Collections.singletonList("tag")), "");
        assertThrows(UnsupportedOperationException.class, () -> tagged.tags().add("other"));
    }

    @Test
    void initialStateIsDeepFrozen() {
        Map<String, Object> state = new HashMap<>();
        state.put("account", new HashMap<String, Object>());
        Case model = Case.of("c1", "n", Collections.singletonList(turn("t1")),
                state, null, null, null, "");
        assertThrows(UnsupportedOperationException.class, () -> model.initialState().put("late", 1));
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) model.initialState().get("account");
        assertThrows(UnsupportedOperationException.class, () -> nested.put("balance", 1));
    }

    @Test
    void identityValidation() {
        assertEquals("Case id must not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> Case.of(" ", "n", Collections.singletonList(turn("t1")))).getMessage());
        assertEquals("Case name must not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> Case.of("c1", " ", Collections.singletonList(turn("t1")))).getMessage());
    }

    @Test
    void turnsMustBeNonEmpty() {
        assertEquals("Tuple should have at least 1 item after validation, not 0",
                assertThrows(IllegalArgumentException.class,
                        () -> Case.of("c1", "n", new ArrayList<CaseTurn>())).getMessage());
        assertEquals("Tuple should have at least 1 item after validation, not 0",
                assertThrows(IllegalArgumentException.class,
                        () -> Case.of("c1", "n", null)).getMessage());
    }

    @Test
    void duplicateTurnIdsAreRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> Case.of("c1", "n", Arrays.asList(turn("t1"), turn("t1"))));
        assertEquals("CaseTurn ids must be unique within a Case", error.getMessage());
    }

    @Test
    void duplicateExpectationIdsAcrossTurnsAreRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> Case.of("c1", "n", Arrays.asList(
                        turnWithExpectation("t1", "e1"),
                        turnWithExpectation("t2", "e1"))));
        assertEquals("Expectation ids must be unique within a Case", error.getMessage());
    }

    @Test
    void sameExpectationIdInDifferentCasesIsAllowed() {
        Case first = Case.of("c1", "n", Collections.singletonList(turnWithExpectation("t1", "e1")));
        Case second = Case.of("c2", "n", Collections.singletonList(turnWithExpectation("t1", "e1")));
        assertEquals("e1", first.turns().get(0).expectations().get(0).id());
        assertEquals("e1", second.turns().get(0).expectations().get(0).id());
    }

    @Test
    void tagsValidation() {
        List<CaseTurn> turns = Collections.singletonList(turn("t1"));
        assertEquals("tags must not contain blank values",
                assertThrows(IllegalArgumentException.class, () -> Case.of("c1", "n", turns,
                        null, null, null, Arrays.asList("ok", " "), "")).getMessage());
        assertEquals("tags must not contain duplicates",
                assertThrows(IllegalArgumentException.class, () -> Case.of("c1", "n", turns,
                        null, null, null, Arrays.asList("dup", "dup"), "")).getMessage());
    }

    @Test
    void fromPayloadAppliesDefaultsAndGeneratesId() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "用例");
        Map<String, Object> turnPayload = new HashMap<>();
        turnPayload.put("input", Collections.singletonMap("q", 1));
        payload.put("turns", Collections.singletonList(turnPayload));

        Case model = Case.fromPayload(payload);
        assertTrue(model.id().trim().length() > 0, "generated id must not be blank");
        assertEquals("用例", model.name());
        assertEquals(CaseCategory.POSITIVE, model.category());
        assertEquals(CaseDifficulty.MEDIUM, model.difficulty());
        assertTrue(model.tags().isEmpty());
        assertTrue(model.initialState().isEmpty());
        assertEquals("", model.notes());
    }

    @Test
    void fromPayloadRejectsNonMapTurn() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "c1");
        payload.put("name", "n");
        payload.put("turns", Collections.singletonList("not-a-map"));
        assertEquals("Input should be a valid dictionary",
                assertThrows(IllegalArgumentException.class, () -> Case.fromPayload(payload))
                        .getMessage());
    }

    @Test
    void fromPayloadRejectsNonStringTag() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "c1");
        payload.put("name", "n");
        payload.put("turns", Collections.singletonList(
                Collections.singletonMap("id", "t1")));
        Map<String, Object> firstTurn = new HashMap<>();
        firstTurn.put("id", "t1");
        firstTurn.put("input", Collections.singletonMap("q", 1));
        payload.put("turns", Collections.singletonList(firstTurn));
        payload.put("tags", Collections.singletonList(42));
        assertEquals("Input should be a valid string",
                assertThrows(IllegalArgumentException.class, () -> Case.fromPayload(payload))
                        .getMessage());
    }

    @Test
    void toPayloadNestsTurnsAndExpectations() {
        PolicyExpectation policy = PolicyExpectation.of("e1", "策略", "p1");
        Map<String, Object> input = new HashMap<>();
        input.put("q", 1);
        CaseTurn turn = CaseTurn.of("t1", input, Collections.singletonList(policy), "轮备注");
        Case model = Case.of("c1", "名称", Collections.singletonList(turn),
                null, CaseCategory.NEGATIVE, CaseDifficulty.HARD,
                Collections.singletonList("标签"), "备注");

        Map<?, ?> payload = (Map<?, ?>) model.toPayload();
        assertEquals("c1", payload.get("id"));
        assertEquals("名称", payload.get("name"));
        assertEquals("negative", payload.get("category"));
        assertEquals("hard", payload.get("difficulty"));
        assertEquals("备注", payload.get("notes"));
        java.util.List<?> turnPayloads = (java.util.List<?>) payload.get("turns");
        assertEquals(1, turnPayloads.size());
        Map<?, ?> turnPayload = (Map<?, ?>) turnPayloads.get(0);
        assertEquals("t1", turnPayload.get("id"));
        assertEquals("轮备注", turnPayload.get("notes"));
        java.util.List<?> expectationPayloads = (java.util.List<?>) turnPayload.get("expectations");
        assertEquals(1, expectationPayloads.size());
        Map<?, ?> expectationPayload = (Map<?, ?>) expectationPayloads.get(0);
        assertEquals("policy", expectationPayload.get("kind"));
        assertEquals("e1", expectationPayload.get("id"));
        assertEquals("策略", expectationPayload.get("name"));
        assertEquals("p1", expectationPayload.get("policy_id"));
    }
}
