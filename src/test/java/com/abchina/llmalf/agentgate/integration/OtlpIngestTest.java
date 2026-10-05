package com.abchina.llmalf.agentgate.integration;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OTLP 归一化纯单测(对齐 Python test_otlp_http.py 核心用例).
 */
class OtlpIngestTest {

    private static Map<String, Object> attribute(String key, Object value) {
        Map<String, Object> attribute = new LinkedHashMap<>();
        attribute.put("key", key);
        Map<String, Object> field = new LinkedHashMap<>();
        if (value instanceof Boolean) {
            field.put("boolValue", value);
        } else {
            field.put("stringValue", value);
        }
        attribute.put("value", field);
        return attribute;
    }

    private static List<Map<String, Object>> turnAttributes() {
        return Arrays.asList(
                attribute("agentgate.operation.type", "turn"),
                attribute("agentgate.turn.id", "turn"),
                attribute("agentgate.turn.complete", true),
                attribute("agentgate.turn.input", "{\"message\": \"hello\"}"),
                attribute("agentgate.turn.output", "{\"message\": \"done\"}"),
                attribute("agentgate.turn.state", "{}"),
                attribute("agentgate.trace.complete", true),
                attribute("agentgate.final.output", "{\"message\": \"done\"}"),
                attribute("agentgate.final.state", "{}"));
    }

    private static Map<String, Object> span(String traceId, String spanId, String name,
            String startTime, String endTime, List<Map<String, Object>> attributes) {
        Map<String, Object> span = new LinkedHashMap<>();
        span.put("traceId", traceId);
        span.put("spanId", spanId);
        span.put("name", name);
        span.put("startTimeUnixNano", startTime);
        span.put("endTimeUnixNano", endTime);
        span.put("attributes", attributes);
        return span;
    }

    private static Map<String, Object> payload(List<Map<String, Object>> spans) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("spans", spans);
        Map<String, Object> resourceSpan = new LinkedHashMap<>();
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("attributes", Arrays.asList(
                attribute("agentgate.run.id", "external-run"),
                attribute("agentgate.case.id", "external-case")));
        resourceSpan.put("resource", resource);
        resourceSpan.put("scopeSpans", java.util.Collections.singletonList(scope));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resourceSpans", java.util.Collections.singletonList(resourceSpan));
        return payload;
    }

    @Test
    void emptyResourceSpansAcceptZeroSpans() {
        List<com.abchina.llmalf.agentgate.domain.model.trace.Trace> saved =
                new ArrayList<>();
        assertEquals(0, OtlpIngest.ingest(new HashMap<>(), saved::add));
        assertTrue(saved.isEmpty());
    }

    @Test
    void nullPayloadIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> OtlpIngest.ingest(null, trace -> {
                }));
    }

    @Test
    void nonArrayResourceSpansIsRejected() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("resourceSpans", 5);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> OtlpIngest.ingest(payload, trace -> {
                }));
        assertEquals("resourceSpans must be an array", error.getMessage());
    }

    @Test
    void nonArrayScopeSpansIsRejected() {
        Map<String, Object> resourceSpan = new HashMap<>();
        resourceSpan.put("resource", new HashMap<>());
        resourceSpan.put("scopeSpans", "bad");
        Map<String, Object> payload = new HashMap<>();
        payload.put("resourceSpans", java.util.Collections.singletonList(resourceSpan));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> OtlpIngest.ingest(payload, trace -> {
                }));
        assertEquals("scopeSpans must be an array", error.getMessage());
    }

    @Test
    void acceptsAndPersistsOneSpan() {
        List<com.abchina.llmalf.agentgate.domain.model.trace.Trace> saved =
                new ArrayList<>();
        Map<String, Object> span = span(repeat('a', 32), repeat('b', 16), "agent.turn",
                "1000000000", "2000000000", turnAttributes());
        int accepted = OtlpIngest.ingest(payload(java.util.Collections.singletonList(span)),
                saved::add);
        assertEquals(1, accepted);
        assertEquals(1, saved.size());
        assertEquals(repeat('a', 32), saved.get(0).traceId());
        assertEquals("external-run", saved.get(0).runId());
        assertEquals("external-case", saved.get(0).caseId());
    }

    @Test
    void spansAreOrderedByTimeInsteadOfPayloadPosition() {
        // 仅一个 completion Span,另一 span 为普通 Turn(无 trace.complete/final 属性)
        List<Map<String, Object>> lateAttrs = java.util.Collections.singletonList(
                attribute("agentgate.operation.type", "event"));
        Map<String, Object> late = span(repeat('a', 32), repeat('b', 16), "late",
                "3000000000", "4000000000", lateAttrs);
        Map<String, Object> early = span(repeat('a', 32), repeat('c', 16), "early",
                "1000000000", "2000000000", turnAttributes());
        List<com.abchina.llmalf.agentgate.domain.model.trace.Trace> saved =
                new ArrayList<>();
        OtlpIngest.ingest(payload(Arrays.asList(late, early)), saved::add);
        assertEquals(1, saved.size());
        assertEquals("early", saved.get(0).spans().get(0).name());
    }

    @Test
    void falsyOperationTypeFallsBackToGenAiThenEvent() {
        List<Map<String, Object>> attributes = new ArrayList<>(turnAttributes());
        attributes.remove(0);
        attributes.add(attribute("agentgate.operation.type", false));
        attributes.add(attribute("gen_ai.operation.name", "custom-op"));
        Map<String, Object> span = span(repeat('a', 32), repeat('b', 16), "agent.turn",
                "1000000000", "2000000000", attributes);
        List<com.abchina.llmalf.agentgate.domain.model.trace.Trace> saved =
                new ArrayList<>();
        OtlpIngest.ingest(payload(java.util.Collections.singletonList(span)), saved::add);
        assertEquals("custom-op", saved.get(0).spans().get(0).operationType());
    }

    @Test
    void missingOperationTypeDefaultsToEvent() {
        List<Map<String, Object>> attributes = new ArrayList<>(turnAttributes());
        attributes.remove(0);
        Map<String, Object> span = span(repeat('a', 32), repeat('b', 16), "agent.turn",
                "1000000000", "2000000000", attributes);
        List<com.abchina.llmalf.agentgate.domain.model.trace.Trace> saved =
                new ArrayList<>();
        OtlpIngest.ingest(payload(java.util.Collections.singletonList(span)), saved::add);
        assertEquals("event", saved.get(0).spans().get(0).operationType());
    }

    @Test
    void aliasAttributesAreCanonicalized() {
        List<Map<String, Object>> attributes = new ArrayList<>(turnAttributes());
        attributes.remove(1);
        attributes.add(attribute("turn_id", "turn-alias"));
        Map<String, Object> span = span(repeat('a', 32), repeat('b', 16), "agent.turn",
                "1000000000", "2000000000", attributes);
        List<com.abchina.llmalf.agentgate.domain.model.trace.Trace> saved =
                new ArrayList<>();
        OtlpIngest.ingest(payload(java.util.Collections.singletonList(span)), saved::add);
        assertTrue(saved.get(0).spans().get(0).attributes()
                .containsKey("agentgate.turn.id"));
    }

    private static String repeat(char c, int n) {
        StringBuilder b = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            b.append(c);
        }
        return b.toString();
    }
}
