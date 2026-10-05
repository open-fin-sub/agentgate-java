package com.abchina.llmalf.agentgate.domain.model.run;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Run 状态机跨语言 golden 对拍测试.
 *
 * <p>金样本由 scripts/generate_run_transitions_golden.py 调用 Python
 * agentgate.domain.run(真实 EvaluationRun/transition_run)生成:7 状态基线、
 * 49 对迁移矩阵、15 个变体场景、18 个 lifecycle 构造校验用例。</p>
 */
class RunTransitionsGoldenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final OffsetDateTime T_PLUS_3H = OffsetDateTime.parse("2026-01-01T03:00:00+00:00");

    private static JsonNode document;

    @BeforeAll
    static void loadGolden() throws Exception {
        try (InputStream input =
                RunTransitionsGoldenTest.class.getResourceAsStream("/contract/run-transitions.json")) {
            assertNotNull(input, "golden fixture /contract/run-transitions.json is missing");
            document = MAPPER.readTree(input);
        }
    }

    @Test
    void baselineStatesConstructAndMatch() {
        for (JsonNode baseline : document.get("baseline_states")) {
            RunLifecycle lifecycle = fromState(baseline);
            assertEquals(baseline.get("status").asText(), lifecycle.status().wireValue());
            assertEquals(parseTime(baseline.get("created_at")), lifecycle.createdAt());
            assertEquals(parseTime(baseline.get("scheduled_for")), lifecycle.scheduledFor());
            assertEquals(parseTime(baseline.get("started_at")), lifecycle.startedAt());
            assertEquals(parseTime(baseline.get("completed_at")), lifecycle.completedAt());
            assertEquals(textOrNull(baseline.get("error")), lifecycle.error());
        }
        assertEquals(7, ensureBaselines().size());
    }

    @Test
    void transitionMatrixMatchesPython() {
        Map<String, RunLifecycle> baselines = ensureBaselines();
        for (JsonNode entry : document.get("transition_matrix")) {
            String fromName = entry.get("from").asText();
            String toName = entry.get("to").asText();
            RunLifecycle source = baselines.get(fromName);
            RunStatus target = RunStatus.fromWireValue(toName);
            String errorInput = "failed".equals(toName) ? "boom" : null;
            if (entry.get("legal").asBoolean()) {
                RunLifecycle result = assertDoesNotThrow(
                        () -> RunTransitions.apply(source, target, T_PLUS_3H, errorInput),
                        "expected legal transition: " + fromName + " -> " + toName);
                assertStateMatches(entry.get("result"), result, fromName + " -> " + toName);
            } else {
                IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                        () -> RunTransitions.apply(source, target, T_PLUS_3H, errorInput));
                assertEquals(entry.get("error").asText(), error.getMessage(),
                        "wrong error for " + fromName + " -> " + toName);
            }
        }
    }

    @Test
    void variantScenariosMatchPython() {
        Map<String, RunLifecycle> baselines = ensureBaselines();
        for (JsonNode variant : document.get("variants")) {
            String name = variant.get("name").asText();
            RunLifecycle source = baselines.get(variant.get("from").asText());
            RunStatus target = RunStatus.fromWireValue(variant.get("to").asText());
            OffsetDateTime occurredAt = parseTime(variant.get("occurred_at"));
            String errorInput = textOrNull(variant.get("error_input"));
            if (variant.get("legal").asBoolean()) {
                RunLifecycle result = assertDoesNotThrow(
                        () -> RunTransitions.apply(source, target, occurredAt, errorInput),
                        "expected legal variant: " + name);
                assertStateMatches(variant.get("result"), result, name);
            } else {
                IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                        () -> RunTransitions.apply(source, target, occurredAt, errorInput));
                assertEquals(variant.get("error").asText(), error.getMessage(),
                        "wrong error for variant " + name);
            }
        }
    }

    @Test
    void lifecycleValidationsMatchPython() {
        for (JsonNode validation : document.get("lifecycle_validations")) {
            String name = validation.get("name").asText();
            JsonNode state = validation.get("state");
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> RunLifecycle.of(
                            RunStatus.fromWireValue(state.get("status").asText()),
                            parseTime(state.get("created_at")),
                            parseTime(state.get("scheduled_for")),
                            parseTime(state.get("started_at")),
                            parseTime(state.get("completed_at")),
                            textOrNull(state.get("error"))),
                    "expected validation failure: " + name);
            assertEquals(validation.get("error").asText(), error.getMessage(),
                    "wrong message for " + name);
        }
    }

    private final Map<String, RunLifecycle> baselines = new HashMap<>();

    private Map<String, RunLifecycle> ensureBaselines() {
        if (baselines.isEmpty()) {
            for (JsonNode baseline : document.get("baseline_states")) {
                baselines.put(baseline.get("status").asText(), fromState(baseline));
            }
        }
        return baselines;
    }

    private static RunLifecycle fromState(JsonNode state) {
        return RunLifecycle.of(
                RunStatus.fromWireValue(state.get("status").asText()),
                parseTime(state.get("created_at")),
                parseTime(state.get("scheduled_for")),
                parseTime(state.get("started_at")),
                parseTime(state.get("completed_at")),
                textOrNull(state.get("error")));
    }

    private static void assertStateMatches(JsonNode expected, RunLifecycle actual, String label) {
        assertEquals(expected.get("status").asText(), actual.status().wireValue(), "status: " + label);
        assertEquals(parseTime(expected.get("created_at")), actual.createdAt(), "created_at: " + label);
        assertEquals(parseTime(expected.get("scheduled_for")), actual.scheduledFor(), "scheduled_for: " + label);
        assertEquals(parseTime(expected.get("started_at")), actual.startedAt(), "started_at: " + label);
        assertEquals(parseTime(expected.get("completed_at")), actual.completedAt(), "completed_at: " + label);
        assertEquals(textOrNull(expected.get("error")), actual.error(), "error: " + label);
    }

    private static OffsetDateTime parseTime(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return OffsetDateTime.parse(node.asText());
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}
