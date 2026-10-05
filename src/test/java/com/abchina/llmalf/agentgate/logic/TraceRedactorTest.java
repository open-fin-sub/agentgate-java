package com.abchina.llmalf.agentgate.logic;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 轨迹脱敏纯单测(对齐 Python test_trace_redaction.py 核心规则族).
 */
class TraceRedactorTest {

    @Test
    void sensitiveKeysAreRedactedRecursively() {
        Map<String, Object> value = new HashMap<>();
        value.put("api_key", "secret-value");
        value.put("nested", java.util.Arrays.asList(
                java.util.Collections.singletonMap("password", "p")));
        Object redacted = TraceRedactor.redactValue(value);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) redacted;
        assertEquals(TraceRedactor.REDACTION_MARKER, map.get("api_key"));
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) ((java.util.List<?>)
                map.get("nested")).get(0);
        assertEquals(TraceRedactor.REDACTION_MARKER, nested.get("password"));
    }

    @Test
    void inlineCredentialsAndBearerTokensAreMasked() {
        Object redacted = TraceRedactor.redactValue("api_key=abc123 other");
        assertTrue(redacted instanceof String);
        assertEquals(false, String.valueOf(redacted).contains("abc123"));

        Object bearer = TraceRedactor.redactValue("Authorization: Bearer a.b.c-d_e");
        assertEquals(false, String.valueOf(bearer).contains("a.b.c-d_e"));
    }

    @Test
    void urlCredentialsAreMasked() {
        Object redacted = TraceRedactor.redactValue("see https://user:pass@host/path");
        assertEquals(false, String.valueOf(redacted).contains("user:pass"));
    }

    @Test
    void privateKeyBlocksAreMasked() {
        String key = "-----BEGIN RSA PRIVATE KEY-----\nabc\n-----END RSA PRIVATE KEY-----";
        Object redacted = TraceRedactor.redactValue(key);
        assertEquals(TraceRedactor.REDACTION_MARKER, redacted);
    }

    @Test
    void emailsAndCardNumbersAreMasked() {
        Object email = TraceRedactor.redactValue("contact: someone@example.com");
        assertEquals(false, String.valueOf(email).contains("someone@example.com"));

        Object card = TraceRedactor.redactValue("card 4111111111111111 ok");
        assertEquals(false, String.valueOf(card).contains("4111111111111111"));
    }

    @Test
    void uuidsArePreserved() {
        String uuid = "01234567-89ab-cdef-0123-456789abcdef";
        assertEquals(uuid, TraceRedactor.redactValue(uuid));
    }

    @Test
    void ordinaryTextIsUnchangedAndIdempotent() {
        String text = "普通业务消息 turn completed";
        assertEquals(text, TraceRedactor.redactValue(text));
        Object once = TraceRedactor.redactValue("api_key=x1");
        assertEquals(once, TraceRedactor.redactValue(once));
    }

    @Test
    void nonTextValuesPassThroughStructure() {
        assertEquals(42, TraceRedactor.redactValue(42));
        assertEquals(Boolean.TRUE, TraceRedactor.redactValue(true));
        assertEquals(java.util.Collections.emptyList(),
                TraceRedactor.redactValue(java.util.Collections.emptyList()));
    }

    @Test
    void correlationKeysKeepNonUuidValues() {
        // request_id 不在敏感键集:非 UUID 文本原样保留(Python 同语义)
        Object requestId = TraceRedactor.redactValue(
                java.util.Collections.singletonMap("request_id", "opaque"));
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) requestId;
        assertEquals("opaque", map.get("request_id"));
    }

    @Test
    void redactTraceKeepsShape() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("api_key", "k");
        com.abchina.llmalf.agentgate.domain.model.trace.TraceSpan span =
                com.abchina.llmalf.agentgate.domain.model.trace.TraceSpan.of(
                        repeat('a', 32), repeat('b', 16), null, "turn", "turn", 1,
                        java.time.OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                        java.time.OffsetDateTime.parse("2026-01-01T00:00:01Z"),
                        com.abchina.llmalf.agentgate.domain.model.trace.SpanStatus.OK,
                        attrs, java.util.Collections.emptyList());
        com.abchina.llmalf.agentgate.domain.model.trace.Trace trace =
                com.abchina.llmalf.agentgate.domain.model.trace.Trace.of(
                        repeat('a', 32), "run-1", "case-1",
                        java.util.Collections.singletonList(span),
                        java.util.Collections.emptyMap(),
                        java.util.Collections.emptyMap(),
                        java.util.Collections.emptyMap());
        com.abchina.llmalf.agentgate.domain.model.trace.Trace redacted =
                TraceRedactor.redact(trace);
        assertEquals(1, redacted.spans().size());
        assertEquals(TraceRedactor.REDACTION_MARKER,
                redacted.spans().get(0).attributes().get("api_key"));
        assertEquals("run-1", redacted.runId());
    }

    private static String repeat(char c, int n) {
        StringBuilder b = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            b.append(c);
        }
        return b.toString();
    }
}
