package com.abchina.llmalf.agentgate.domain.model.trace;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trace/TraceSpan 聚焦行为测试.
 */
class TraceModelsTest {

    private static final String TRACE = "0123456789abcdef0123456789abcdef";
    private static final String SPAN_A = "0123456789abcdef";
    private static final String SPAN_B = "fedcba9876543210";
    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T00:00:00+00:00");
    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-01-02T00:00:00+00:00");

    private static TraceSpan span(String spanId, int sequence) {
        return TraceSpan.of(TRACE, spanId, null, "操作", "tool", sequence, T0, T1,
                null, null, null);
    }

    @Test
    void spanDefaultsAndFreezing() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("k", new ArrayList<>());
        Map<String, Object> event = new HashMap<>();
        event.put("name", "evt");
        List<Map<String, ?>> events = new ArrayList<>();
        events.add(event);

        TraceSpan span = TraceSpan.of(TRACE, SPAN_A, null, "操作", "turn", 0, T0, T1,
                null, attributes, events);
        assertEquals(SpanStatus.UNSET, span.status());
        assertNull(span.parentSpanId());
        assertThrows(UnsupportedOperationException.class,
                () -> span.attributes().put("late", 1));
        assertThrows(UnsupportedOperationException.class,
                () -> ((List<Object>) span.attributes().get("k")).add("x"));
        assertThrows(UnsupportedOperationException.class,
                () -> span.events().add(new HashMap<String, Object>()));
        assertThrows(UnsupportedOperationException.class,
                () -> span.events().get(0).put("late", 1));
        attributes.put("late", 1);
        assertTrue(!span.attributes().containsKey("late"), "of must copy the source");
    }

    @Test
    void traceFreezesTopLevelObjects() {
        Map<String, Object> output = new HashMap<>();
        output.put("answer", "答复");
        Trace model = Trace.of(TRACE, "run-1", "c1", null, null, output, null);
        assertThrows(UnsupportedOperationException.class,
                () -> model.finalOutput().put("late", 1));
        assertTrue(model.turnOutcomes().isEmpty());
        assertTrue(model.finalState().isEmpty());
    }

    @Test
    void traceSpansListIsImmutableAndCopied() {
        List<TraceSpan> source = new ArrayList<>(Arrays.asList(span(SPAN_A, 0)));
        Trace model = Trace.of(TRACE, "run-1", "c1", source, null, null, null);
        assertThrows(UnsupportedOperationException.class, () -> model.spans().clear());
        source.clear();
        assertEquals(1, model.spans().size(), "of must copy the spans list");
    }

    @Test
    void spanStatusRoundTrip() {
        assertEquals(SpanStatus.OK, SpanStatus.fromWireValue("ok"));
        assertEquals(SpanStatus.ERROR, SpanStatus.fromWireValue("error"));
        assertEquals("Input should be 'unset', 'ok' or 'error'",
                assertThrows(IllegalArgumentException.class,
                        () -> SpanStatus.fromWireValue("failed")).getMessage());
    }

    @Test
    void fromPayloadDefaults() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("trace_id", TRACE);
        payload.put("span_id", SPAN_A);
        payload.put("name", "操作");
        payload.put("operation_type", "tool");
        payload.put("sequence", 0);
        TraceSpan span = TraceSpan.fromPayload(payload);
        assertEquals(SpanStatus.UNSET, span.status());
        assertTrue(span.attributes().isEmpty());
        assertTrue(span.events().isEmpty());
        assertEquals(null, span.parentSpanId());
        assertTrue(span.startedAt() != null);
    }
}
