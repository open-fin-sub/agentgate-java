package com.abchina.llmalf.agentgate.domain.model.result;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CheckResult/JudgeRecord/EvaluatorErrorDetail/MethodRef 聚焦行为测试.
 */
class ResultModelsTest {

    private static final String SPAN = "0123456789abcdef";

    private static String repeat64(String seed) {
        StringBuilder buffer = new StringBuilder(64);
        for (int i = 0; i < 64; i++) {
            buffer.append(seed);
        }
        return buffer.toString();
    }

    @Test
    void methodRefValidation() {
        assertEquals("MethodRef implementation_id must not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> MethodRef.of(" ", "1")).getMessage());
        assertEquals("MethodRef implementation_version must not be blank",
                assertThrows(IllegalArgumentException.class,
                        () -> MethodRef.of("impl", "")).getMessage());
    }

    @Test
    void judgeRecordNormalizesEmptyStringsToNull() {
        JudgeRecord record = JudgeRecord.of("openai", "gpt-4o", "", repeat64("a"),
                "raw", "", null, null, null, null);
        assertNull(record.resolvedModel(), "empty resolved_model normalizes to null");
        assertNull(record.requestId(), "empty request_id normalizes to null");
        assertEquals("raw", record.rawResponse(), "raw_response passes through");

        Map<String, Object> payload = (Map<String, Object>) record.toPayload();
        assertNull(payload.get("resolved_model"));
        assertNull(payload.get("request_id"));
    }

    @Test
    void judgeRecordBlankOptionalTextIsRejected() {
        assertEquals("JudgeRecord resolved_model must not be blank",
                assertThrows(IllegalArgumentException.class, () -> JudgeRecord.of(
                        "openai", "gpt-4o", " ", repeat64("a"), "raw", null,
                        null, null, null, null)).getMessage());
        assertEquals("JudgeRecord request_id must not be blank",
                assertThrows(IllegalArgumentException.class, () -> JudgeRecord.of(
                        "openai", "gpt-4o", null, repeat64("a"), "raw", " ",
                        null, null, null, null)).getMessage());
    }

    @Test
    void judgeRecordPreviousAttemptsAreRecursiveAndImmutable() {
        JudgeRecord first = JudgeRecord.of("openai", "m", null, repeat64("a"), "r1",
                null, null, null, null, null);
        List<JudgeRecord> attempts = new ArrayList<>();
        attempts.add(first);
        JudgeRecord retry = JudgeRecord.of("openai", "m", null, repeat64("b"), "r2",
                null, 10L, 5L, 800.0, attempts);
        assertEquals(1, retry.previousAttempts().size());
        assertEquals("r1", retry.previousAttempts().get(0).rawResponse());
        assertThrows(UnsupportedOperationException.class,
                () -> retry.previousAttempts().clear());
        attempts.clear();
        assertEquals(1, retry.previousAttempts().size(), "of must copy the list");
    }

    @Test
    void errorDetailCategoryAndReference() {
        assertEquals("Input should be 'crash', 'timeout' or 'invalid_output'",
                assertThrows(IllegalArgumentException.class, () -> EvaluatorErrorDetail.of(
                        "fatal", "TypeError", "msg", false, null)).getMessage());
        EvaluatorErrorDetail detail = EvaluatorErrorDetail.of("crash", "TypeError",
                "崩溃", true, "");
        assertNull(detail.reference(), "empty reference normalizes to null");
        assertEquals("EvaluatorErrorDetail reference must not be blank",
                assertThrows(IllegalArgumentException.class, () -> EvaluatorErrorDetail.of(
                        "crash", "TypeError", "msg", false, " ")).getMessage());
    }

    @Test
    void checkFreezesExpectedAndActual() {
        Map<String, Object> nested = new HashMap<>();
        nested.put("a", new ArrayList<>());
        CheckResult check = CheckResult.of("chk", "名", null, null, Outcome.PASS, 1.0,
                "原因", nested, Arrays.asList(1, 2), false, null, null, null, null, null);
        assertThrows(UnsupportedOperationException.class,
                () -> ((Map<String, Object>) check.expected()).put("late", 1));
        assertThrows(UnsupportedOperationException.class,
                () -> ((List<Object>) check.actual()).add(3));
        nested.put("late", 1);
        assertEquals(false, ((Map<String, Object>) check.expected()).containsKey("late"),
                "of must copy the source");
    }

    @Test
    void checkNormalizesEmptyOptionalText() {
        CheckResult check = CheckResult.of("chk", "名", "", "", Outcome.PASS, 1.0,
                "原因", null, null, false, null, null, null, null, null);
        assertNull(check.turnId(), "empty turn_id normalizes to null");
        assertNull(check.expectationId(), "empty expectation_id normalizes to null");
        assertEquals("CheckResult expectation_id must not be blank",
                assertThrows(IllegalArgumentException.class, () -> CheckResult.of(
                        "chk", "名", null, " ", Outcome.PASS, 1.0, "原因",
                        null, null, false, null, null, null, null, null)).getMessage());
    }

    @Test
    void checkScoreBounds() {
        assertEquals("Input should be greater than or equal to 0",
                assertThrows(IllegalArgumentException.class, () -> CheckResult.of(
                        "chk", "n", null, null, Outcome.PASS, -0.1, "r",
                        null, null, false, null, null, null, null, null)).getMessage());
        assertEquals("Input should be less than or equal to 1",
                assertThrows(IllegalArgumentException.class, () -> CheckResult.of(
                        "chk", "n", null, null, Outcome.PASS, 1.1, "r",
                        null, null, false, null, null, null, null, null)).getMessage());
    }

    @Test
    void checkFailureFieldsBelongToFailOnly() {
        CheckResult failed = CheckResult.of("chk", "n", null, null, Outcome.FAIL, 0.0, "r",
                null, null, false, null, Arrays.asList(SPAN),
                FailureStage.TOOL_SELECTION, 3, SPAN);
        assertEquals(FailureStage.TOOL_SELECTION, failed.failureStage());
        assertEquals(Integer.valueOf(3), failed.failureSequence());
        assertEquals(SPAN, failed.failureSpanId());

        assertEquals("failure_span_id must be included in span_ids",
                assertThrows(IllegalArgumentException.class, () -> CheckResult.of(
                        "chk", "n", null, null, Outcome.FAIL, 0.0, "r",
                        null, null, false, null, Arrays.asList(SPAN),
                        FailureStage.TOOL_SELECTION, 1, "fedcba9876543210")).getMessage());
    }

    @Test
    void checkSpanIdRules() {
        assertEquals("span_ids must contain lowercase OTel Span IDs",
                assertThrows(IllegalArgumentException.class, () -> CheckResult.of(
                        "chk", "n", null, null, Outcome.PASS, 1.0, "r",
                        null, null, false, null, Arrays.asList("ABCDEF0123456789"),
                        null, null, null)).getMessage());
        assertEquals("span_ids must be unique",
                assertThrows(IllegalArgumentException.class, () -> CheckResult.of(
                        "chk", "n", null, null, Outcome.PASS, 1.0, "r",
                        null, null, false, null, Arrays.asList(SPAN, SPAN),
                        null, null, null)).getMessage());
    }

    @Test
    void outcomeEnumsRoundTrip() {
        assertEquals(Outcome.NOT_APPLICABLE, Outcome.fromWireValue("not_applicable"));
        assertEquals("Input should be 'pass', 'fail', 'review', 'not_applicable' or 'error'",
                assertThrows(IllegalArgumentException.class,
                        () -> Outcome.fromWireValue("skipped")).getMessage());
        assertEquals(FailureStage.RESULT_INTERPRETATION,
                FailureStage.fromWireValue("result_interpretation"));
        assertEquals("Input should be 'task_understanding', 'planning', 'context_retrieval', "
                        + "'routing', 'tool_selection', 'tool_arguments', 'tool_execution', "
                        + "'result_interpretation', 'final_state' or 'final_output'",
                assertThrows(IllegalArgumentException.class,
                        () -> FailureStage.fromWireValue("unknown")).getMessage());
    }
}
