package com.abchina.llmalf.agentgate.domain.model.result;

import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorKind;
import com.abchina.llmalf.agentgate.domain.model.evaluator.EvaluatorSeverity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * EvaluationResult 聚焦行为测试(golden 未覆盖部分).
 */
class EvaluationResultTest {

    private static final String TRACE = "0123456789abcdef0123456789abcdef";

    private static String repeat64(String seed) {
        StringBuilder buffer = new StringBuilder(64);
        for (int i = 0; i < 64; i++) {
            buffer.append(seed);
        }
        return buffer.toString();
    }

    private static CheckResult check(String id, Outcome outcome, Double score,
            FailureStage stage, Integer sequence) {
        return CheckResult.of(id, "检查-" + id, null, null, outcome, score, "原因",
                null, null, false, null, null, stage, sequence, null);
    }

    private static EvaluationResult result(Outcome outcome, Double score,
            List<CheckResult> checks, FailureStage primaryStage, EvaluatorKind kind) {
        return EvaluationResult.of("res-1", "run-1", "c1", TRACE, "state", "状态", "1",
                repeat64("b"), kind, "state", "metric", EvaluatorSeverity.STANDARD,
                outcome, score, "原因", checks, null, null, primaryStage);
    }

    @Test
    void earliestFailedTieBreaksToFirstCheck() {
        CheckResult first = check("chk-1", Outcome.FAIL, 0.0, FailureStage.ROUTING, 1);
        CheckResult second = check("chk-2", Outcome.FAIL, 0.0, FailureStage.TOOL_SELECTION, 1);
        EvaluationResult failed = result(Outcome.FAIL, 0.4, Arrays.asList(first, second),
                FailureStage.ROUTING, EvaluatorKind.RULE);
        assertEquals(FailureStage.ROUTING, failed.primaryFailureStage());

        assertEquals("primary_failure_stage must match the earliest failed check",
                assertThrows(IllegalArgumentException.class, () -> result(
                        Outcome.FAIL, 0.4, Arrays.asList(
                                check("chk-1", Outcome.FAIL, 0.0, FailureStage.ROUTING, 5),
                                check("chk-2", Outcome.FAIL, 0.0, FailureStage.TOOL_SELECTION, 1)),
                        FailureStage.ROUTING, EvaluatorKind.RULE)).getMessage());
    }

    @Test
    void checksListIsImmutableAndCopied() {
        List<CheckResult> source = new ArrayList<>(
                Arrays.asList(check("chk-1", Outcome.PASS, 1.0, null, null)));
        EvaluationResult passed = result(Outcome.PASS, 1.0, source, null, EvaluatorKind.RULE);
        assertThrows(UnsupportedOperationException.class, () -> passed.checks().clear());
        source.clear();
        assertEquals(1, passed.checks().size(), "of must copy the checks list");
    }

    @Test
    void traceIdMustBe32LowercaseHex() {
        assertEquals("trace_id must be exactly 32 lowercase hexadecimal characters",
                assertThrows(IllegalArgumentException.class, () -> EvaluationResult.of(
                        "res-1", "run-1", "c1", "XYZ", "state", "n", "1",
                        repeat64("b"), EvaluatorKind.RULE, "d", "m",
                        EvaluatorSeverity.STANDARD, Outcome.PASS, 1.0, "r",
                        null, null, null, null)).getMessage());
    }

    @Test
    void requiredKindsAndSeverities() {
        assertEquals("Field required",
                assertThrows(IllegalArgumentException.class, () -> EvaluationResult.of(
                        "res-1", "run-1", "c1", TRACE, "state", "n", "1",
                        repeat64("b"), null, "d", "m",
                        EvaluatorSeverity.STANDARD, Outcome.PASS, 1.0, "r",
                        null, null, null, null)).getMessage());
        assertEquals("Field required",
                assertThrows(IllegalArgumentException.class, () -> EvaluationResult.of(
                        "res-1", "run-1", "c1", TRACE, "state", "n", "1",
                        repeat64("b"), EvaluatorKind.RULE, "d", "m",
                        null, Outcome.PASS, 1.0, "r",
                        null, null, null, null)).getMessage());
    }

    @Test
    void llmJudgeErrorResultMayOmitJudgeRecord() {
        EvaluationResult errored = EvaluationResult.of("res-1", "run-1", "c1", TRACE,
                "state", "状态", "1", repeat64("b"), EvaluatorKind.LLM_JUDGE,
                "state", "metric", EvaluatorSeverity.STANDARD, Outcome.ERROR, null,
                "原因", new ArrayList<CheckResult>(), null,
                EvaluatorErrorDetail.of("timeout", "TimeoutError", "超时", false, null),
                null);
        assertEquals(Outcome.ERROR, errored.outcome());
        assertEquals(null, errored.judgeRecord());
    }
}
