package com.abchina.llmalf.agentgate.domain.model.gate;

import com.abchina.llmalf.agentgate.domain.model.result.Outcome;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ReleaseGateDecision 聚焦行为测试.
 */
class ReleaseGateDecisionTest {

    @Test
    void classifyFollowsFailClosedPriority() {
        assertEquals(ReleaseGateDecision.REASON_MISSING_RESULTS,
                ReleaseGateDecision.classify(true, true, true, true, 1.0, 0.5).reasonCode());
        assertEquals(Outcome.FAIL,
                ReleaseGateDecision.classify(true, true, true, true, 1.0, 0.5).outcome());

        assertEquals(ReleaseGateDecision.REASON_EVALUATOR_ERROR,
                ReleaseGateDecision.classify(false, true, true, true, 1.0, 0.5).reasonCode());
        assertEquals(ReleaseGateDecision.REASON_BLOCKING_FAILURE,
                ReleaseGateDecision.classify(false, false, true, true, 1.0, 0.5).reasonCode());
        assertEquals(ReleaseGateDecision.REASON_REVIEW_REQUIRED,
                ReleaseGateDecision.classify(false, false, false, true, 1.0, 0.5).reasonCode());
        assertEquals(ReleaseGateDecision.REASON_NO_APPLICABLE_RESULTS,
                ReleaseGateDecision.classify(false, false, false, false, null, 0.5).reasonCode());
        assertEquals(ReleaseGateDecision.REASON_THRESHOLD_MET,
                ReleaseGateDecision.classify(false, false, false, false, 0.5, 0.5).reasonCode());
        assertEquals(Outcome.PASS,
                ReleaseGateDecision.classify(false, false, false, false, 0.5, 0.5).outcome());
        assertEquals(ReleaseGateDecision.REASON_SCORE_BELOW_THRESHOLD,
                ReleaseGateDecision.classify(false, false, false, false, 0.49, 0.5).reasonCode());
    }

    @Test
    void outcomeMustBePassOrFail() {
        assertEquals("Input should be 'pass' or 'fail'",
                assertThrows(IllegalArgumentException.class, () -> ReleaseGateDecision.of(
                        Outcome.REVIEW, null, null, 0.5,
                        ReleaseGateDecision.REASON_REVIEW_REQUIRED)).getMessage());
    }

    @Test
    void missingResultsPairsAreValidatedAndUnique() {
        assertEquals("missing_results cannot contain blank identifiers",
                assertThrows(IllegalArgumentException.class, () -> ReleaseGateDecision.of(
                        Outcome.FAIL, java.util.Collections.singletonList(new String[] {"c1", " "}),
                        null, 0.5, ReleaseGateDecision.REASON_MISSING_RESULTS)).getMessage());
        assertEquals("missing_results must be unique",
                assertThrows(IllegalArgumentException.class, () -> ReleaseGateDecision.of(
                        Outcome.FAIL, java.util.Arrays.<String[]>asList(new String[] {"c1", "e"}, new String[] {"c1", "e"}),
                        null, 0.5, ReleaseGateDecision.REASON_MISSING_RESULTS)).getMessage());

        ReleaseGateDecision decision = ReleaseGateDecision.of(Outcome.FAIL,
                java.util.Collections.singletonList(new String[] {"c1", "e1"}), null, 0.5,
                ReleaseGateDecision.REASON_MISSING_RESULTS);
        Map<?, ?> payload = (Map<?, ?>) decision.toPayload();
        List<?> pairs = (List<?>) payload.get("missing_results");
        List<?> first = (List<?>) pairs.get(0);
        assertEquals("c1", first.get(0));
        assertEquals("e1", first.get(1));
    }

    @Test
    void fromPayloadParsesPairArrays() {
        List<Object> pair = new ArrayList<>();
        pair.add("case-1");
        pair.add("eval-1");
        Map<String, Object> payload = new HashMap<>();
        payload.put("outcome", "fail");
        payload.put("missing_results", new ArrayList<>(Arrays.asList(pair)));
        payload.put("minimum_score", 0.9);
        payload.put("reason_code", "missing_results");
        ReleaseGateDecision decision = ReleaseGateDecision.fromPayload(payload);
        assertEquals(1, decision.missingResults().size());
        assertEquals("case-1", decision.missingResults().get(0)[0]);
        assertEquals(0.9, decision.minimumScore(), 0.0);
    }

    @Test
    void scoreBoundsAndReasonSemantics() {
        assertEquals("Input should be greater than or equal to 0",
                assertThrows(IllegalArgumentException.class, () -> ReleaseGateDecision.of(
                        Outcome.FAIL, null, -0.1, 0.5,
                        ReleaseGateDecision.REASON_SCORE_BELOW_THRESHOLD)).getMessage());
        assertEquals("Input should be less than or equal to 1",
                assertThrows(IllegalArgumentException.class, () -> ReleaseGateDecision.of(
                        Outcome.PASS, null, 1.0, 1.5,
                        ReleaseGateDecision.REASON_THRESHOLD_MET)).getMessage());
        assertEquals("Input should be 'threshold_met', 'score_below_threshold', "
                        + "'missing_results', 'evaluator_error', 'blocking_failure', "
                        + "'review_required' or 'no_applicable_results'",
                assertThrows(IllegalArgumentException.class, () -> ReleaseGateDecision.of(
                        Outcome.FAIL, null, null, 0.5, "mystery")).getMessage());
    }
}
