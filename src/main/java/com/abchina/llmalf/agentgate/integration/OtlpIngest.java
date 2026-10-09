package com.abchina.llmalf.agentgate.integration;

import com.abchina.llmalf.agentgate.domain.CanonicalJson;
import com.abchina.llmalf.agentgate.domain.model.trace.SpanStatus;
import com.abchina.llmalf.agentgate.domain.model.trace.Trace;
import com.abchina.llmalf.agentgate.domain.model.trace.TraceSpan;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * OTLP/HTTP JSON 接收归一化.
 *
 * <p>对齐 Python trace/normalizer.py::normalize_otlp_json +
 * ingest_otlp_http_json:属性别名归一、组装完成 Trace
 * (唯一 completion Span / Turn 完成度 / final 输出与状态)。</p>
 */
public final class OtlpIngest {

    private static final Map<String, String[]> ATTRIBUTE_ALIASES = buildAliases();

    private OtlpIngest() {
    }

    /**
     * 归一化并持久化一个 OTLP JSON payload.
     *
     * @param payload OTLP JSON
     * @param sink 持久化回调
     * @return 接收的 Span 数
     */
    public static int ingest(Map<String, Object> payload, TraceSink sink) {
        if (payload == null) {
            throw new IllegalArgumentException("OTLP payload must be an object");
        }
        List<Trace> traces = normalize(payload);
        for (Trace trace : traces) {
            sink.saveTrace(trace);
        }
        int total = 0;
        for (Trace trace : traces) {
            total += trace.spans().size();
        }
        return total;
    }

    /**
     * 归一化 OTLP payload 为 Trace 列表.
     *
     * @param payload OTLP JSON
     * @return Trace 列表(按 trace_id 排序)
     */
    public static List<Trace> normalize(Map<String, Object> payload) {
        Map<String, List<TraceSpan>> grouped = new LinkedHashMap<>();
        // 显式 null 与缺键区分(对齐 Python dict.get 默认值语义):缺键→空,null→422
        Object rawResources = payload.containsKey("resourceSpans")
                ? payload.get("resourceSpans") : new ArrayList<>();
        List<?> resources = asList(rawResources, "resourceSpans must be an array");
        for (Object resourceSpanObject : resources) {
            Map<String, Object> resourceSpan = asObject(resourceSpanObject);
            Map<String, Object> resource = asObjectOrEmpty(resourceSpanObject instanceof Map
                    ? ((Map<?, ?>) resourceSpan).get("resource") : null);
            Map<String, Object> resourceAttributes = attributes(
                    asListOrEmpty(resource.get("attributes")));
            Object scopeFallback = resourceSpan.containsKey(
                    "instrumentationLibrarySpans")
                    ? resourceSpan.get("instrumentationLibrarySpans") : new ArrayList<>();
            Object scopeSpansRaw = resourceSpan.containsKey("scopeSpans")
                    ? resourceSpan.get("scopeSpans") : scopeFallback;
            if (!(scopeSpansRaw instanceof List)) {
                throw new IllegalArgumentException("scopeSpans must be an array");
            }
            List<?> scopeSpans = scopeSpansRaw == null
                    ? Collections.emptyList() : (List<?>) scopeSpansRaw;
            for (Object scopeSpanObject : scopeSpans) {
                Map<String, Object> scopeSpan = asObject(scopeSpanObject);
                Object rawSpans = scopeSpan.containsKey("spans")
                        ? scopeSpan.get("spans") : new ArrayList<>();
                if (!(rawSpans instanceof List)) {
                    throw new IllegalArgumentException("spans must be an array");
                }
                for (Object rawObject : (List<?>) rawSpans) {
                    Map<String, Object> raw = asObject(rawObject);
                    Map<String, Object> spanAttributes = new LinkedHashMap<>();
                    spanAttributes.putAll(resourceAttributes);
                    spanAttributes.putAll(attributes(
                            asListOrEmpty(raw.get("attributes"))));
                    String traceId = stringOrNull(raw.get("traceId"));
                    String spanId = stringOrNull(raw.get("spanId"));
                    if (traceId == null || spanId == null) {
                        throw new IllegalArgumentException(
                                "OTLP Span requires traceId and spanId");
                    }
                    String parentSpanId = stringOrNull(raw.get("parentSpanId"));
                    TraceSpan span = TraceSpan.of(traceId.toLowerCase(Locale.ROOT),
                            spanId.toLowerCase(Locale.ROOT),
                            parentSpanId == null ? null
                                    : parentSpanId.toLowerCase(Locale.ROOT),
                            stringOrDefault(raw.get("name"), "otlp-span"),
                            operationType(spanAttributes),
                            0,
                            timestamp(raw.get("startTimeUnixNano"), "startTimeUnixNano"),
                            timestamp(raw.get("endTimeUnixNano"), "endTimeUnixNano"),
                            status(asObjectOrEmpty(raw.get("status"))),
                            canonicalize(spanAttributes),
                            castEventsList(events(asListOrEmpty(raw.get("events")))));
                    grouped.computeIfAbsent(span.traceId(), key -> new ArrayList<>()).add(span);
                }
            }
        }
        List<Trace> traces = new ArrayList<>(grouped.size());
        for (String traceId : new TreeSet<>(grouped.keySet())) {
            traces.add(assemble(grouped.get(traceId)));
        }
        return traces;
    }

    /**
     * 组装单条 Trace.
     *
     * @param spans 归一化 Span 列表
     * @return Trace
     */
    public static Trace assemble(List<TraceSpan> spans) {
        if (spans.isEmpty()) {
            throw new IllegalArgumentException("cannot assemble an empty Trace");
        }
        Set<String> traceIds = new HashSet<>();
        for (TraceSpan span : spans) {
            traceIds.add(span.traceId());
        }
        if (traceIds.size() != 1) {
            throw new IllegalArgumentException("Trace Spans must share one trace_id");
        }
        String runId = requiredOwner(spans, "agentgate.run.id");
        String caseId = requiredOwner(spans, "agentgate.case.id");
        List<TraceSpan> ordered = new ArrayList<>(spans);
        ordered.sort(Comparator
                .comparing(TraceSpan::startedAt)
                .thenComparing(TraceSpan::endedAt)
                .thenComparing(TraceSpan::spanId));
        List<TraceSpan> completion = new ArrayList<>();
        for (TraceSpan span : ordered) {
            if (Boolean.TRUE.equals(span.attributes().get("agentgate.trace.complete"))) {
                completion.add(span);
            }
        }
        if (completion.size() != 1) {
            throw new IllegalArgumentException(
                    "Trace requires exactly one completion Span");
        }
        Map<String, Object> turnOutcomes = new LinkedHashMap<>();
        for (TraceSpan span : ordered) {
            if (!Boolean.TRUE.equals(span.attributes().get("agentgate.turn.complete"))) {
                continue;
            }
            Object turnIdRaw = span.attributes().get("agentgate.turn.id");
            if (!(turnIdRaw instanceof String)
                    || ((String) turnIdRaw).trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "Turn completion Span requires agentgate.turn.id");
            }
            String turnId = (String) turnIdRaw;
            if (turnOutcomes.containsKey(turnId)) {
                throw new IllegalArgumentException(
                        "duplicate Turn completion Span: " + turnId);
            }
            Map<String, Object> outcome = new LinkedHashMap<>();
            outcome.put("input", jsonObject(span, "agentgate.turn.input"));
            outcome.put("output", jsonObject(span, "agentgate.turn.output"));
            outcome.put("state", jsonObject(span, "agentgate.turn.state"));
            turnOutcomes.put(turnId, Collections.unmodifiableMap(outcome));
        }
        if (turnOutcomes.isEmpty()) {
            throw new IllegalArgumentException("Trace requires at least one completed Turn");
        }
        List<TraceSpan> sequenced = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            sequenced.add(withSequence(ordered.get(i), i));
        }
        TraceSpan terminal = completion.get(0);
        return Trace.of(traceIds.iterator().next(), runId, caseId, sequenced,
                Collections.unmodifiableMap(turnOutcomes),
                jsonObject(terminal, "agentgate.final.output"),
                jsonObject(terminal, "agentgate.final.state"));
    }

    /** 持久化回调. */
    public interface TraceSink {

        /**
         * 持久化 Trace.
         *
         * @param trace Trace
         */
        void saveTrace(Trace trace);
    }

    private static TraceSpan withSequence(TraceSpan span, int sequence) {
        return TraceSpan.of(span.traceId(), span.spanId(), span.parentSpanId(),
                span.name(), span.operationType(), sequence, span.startedAt(),
                span.endedAt(), span.status(), span.attributes(),
                castEventsList(span.events()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Map<String, ?>> castEventsList(List<Map<String, Object>> events) {
        return (List<Map<String, ?>>) (List) events;
    }

    private static String operationType(Map<String, Object> attributes) {
        // 对齐 Python or 链:False/0/""/null 均回退,字符串 "0" 为真值
        Object canonical = attributes.get("agentgate.operation.type");
        if (isTruthy(canonical)) {
            return String.valueOf(canonical);
        }
        Object genAi = attributes.get("gen_ai.operation.name");
        if (isTruthy(genAi)) {
            return String.valueOf(genAi);
        }
        return "event";
    }

    private static boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0;
        }
        return !String.valueOf(value).isEmpty();
    }

    private static Map<String, Object> canonicalize(Map<String, Object> values) {
        Map<String, Object> normalized = new LinkedHashMap<>(values);
        for (Map.Entry<String, String[]> entry : ATTRIBUTE_ALIASES.entrySet()) {
            String canonical = entry.getKey();
            String[] aliases = entry.getValue();
            List<String> present = new ArrayList<>();
            present.add(canonical);
            for (String alias : aliases) {
                present.add(alias);
            }
            Map<String, Object> found = new LinkedHashMap<>();
            for (String key : present) {
                if (values.containsKey(key)) {
                    found.put(key, values.get(key));
                }
            }
            if (found.isEmpty()) {
                continue;
            }
            Set<String> serialized = new HashSet<>();
            for (Object value : found.values()) {
                serialized.add(CanonicalJson
                        .serialize(value));
            }
            if (serialized.size() > 1) {
                throw new IllegalArgumentException(
                        "conflicting Trace attribute aliases for " + canonical);
            }
            normalized.put(canonical, found.values().iterator().next());
            for (String alias : aliases) {
                normalized.remove(alias);
            }
        }
        return normalized;
    }

    private static Map<String, Object> attributes(List<?> items) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Object itemObject : items) {
            Map<String, Object> item = asObject(itemObject);
            Object keyRaw = item.get("key");
            if (!(keyRaw instanceof String) || ((String) keyRaw).trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "OTLP attribute key must be a nonblank string");
            }
            String key = (String) keyRaw;
            if (result.containsKey(key)) {
                throw new IllegalArgumentException("duplicate OTLP attribute: " + key);
            }
            result.put(key, otlpValue(asObjectOrEmpty(item.get("value"))));
        }
        return result;
    }

    private static Object otlpValue(Map<String, Object> value) {
        if (value.containsKey("stringValue")) {
            return value.get("stringValue");
        }
        if (value.containsKey("boolValue")) {
            return value.get("boolValue");
        }
        if (value.containsKey("intValue")) {
            return new BigDecimal(String.valueOf(value.get("intValue"))).longValueExact();
        }
        if (value.containsKey("doubleValue")) {
            return Double.parseDouble(String.valueOf(value.get("doubleValue")));
        }
        if (value.containsKey("arrayValue")) {
            Map<String, Object> array = asObjectOrEmpty(value.get("arrayValue"));
            List<Object> values = new ArrayList<>();
            for (Object item : asListOrEmpty(array.get("values"))) {
                values.add(otlpValue(asObjectOrEmpty(item)));
            }
            return Collections.unmodifiableList(values);
        }
        if (value.containsKey("kvlistValue")) {
            Map<String, Object> kvlist = asObjectOrEmpty(value.get("kvlistValue"));
            return Collections.unmodifiableMap(attributes(
                    asListOrEmpty(kvlist.get("values"))));
        }
        return null;
    }

    private static List<Map<String, Object>> events(List<?> items) {
        List<Map<String, Object>> events = new ArrayList<>(items.size());
        for (Object itemObject : items) {
            Map<String, Object> item = asObject(itemObject);
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("name", stringOrDefault(item.get("name"), "event"));
            event.put("time_unix_nano", item.get("timeUnixNano"));
            event.put("attributes", Collections.unmodifiableMap(
                    attributes(asListOrEmpty(item.get("attributes")))));
            events.add(Collections.unmodifiableMap(event));
        }
        return events;
    }

    private static OffsetDateTime timestamp(Object nanoseconds, String fieldName) {
        if (nanoseconds == null || "".equals(nanoseconds)) {
            throw new IllegalArgumentException("OTLP Span requires " + fieldName);
        }
        BigDecimal value = new BigDecimal(String.valueOf(nanoseconds));
        if (value.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }
        long seconds = value.divide(new BigDecimal(1_000_000_000)).longValue();
        long nanos = value.remainder(new BigDecimal(1_000_000_000)).longValue();
        return OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(seconds, nanos),
                ZoneOffset.UTC);
    }

    private static SpanStatus status(Map<String, Object> raw) {
        String code = stringOrDefault(raw.get("code"), "unset")
                .toLowerCase(Locale.ROOT);
        if ("2".equals(code) || "status_code_error".equals(code) || "error".equals(code)) {
            return SpanStatus.ERROR;
        }
        if ("1".equals(code) || "status_code_ok".equals(code) || "ok".equals(code)) {
            return SpanStatus.OK;
        }
        return SpanStatus.UNSET;
    }

    private static String requiredOwner(List<TraceSpan> spans, String key) {
        Set<String> values = new HashSet<>();
        for (TraceSpan span : spans) {
            if (!span.attributes().containsKey(key)) {
                continue;
            }
            Object value = span.attributes().get(key);
            if (!(value instanceof String)
                    || ((String) value).trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "Trace Span " + key + " must be a nonblank string");
            }
            values.add((String) value);
        }
        if (values.size() != 1) {
            if (values.isEmpty()) {
                throw new IllegalArgumentException("Trace Spans require " + key);
            }
            throw new IllegalArgumentException("Trace Spans contain conflicting " + key);
        }
        return values.iterator().next();
    }

    private static Map<String, Object> jsonObject(TraceSpan span, String key) {
        Object raw = span.attributes().get(key);
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(
                    "completion Span requires JSON string attribute " + key);
        }
        Object value;
        try {
            value = new ObjectMapper()
                    .readValue((String) raw, Object.class);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "completion Span contains invalid JSON in " + key);
        }
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException(
                    "completion Span attribute " + key + " must contain a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) value;
        return result;
    }

    private static Map<String, String[]> buildAliases() {
        Map<String, String[]> aliases = new LinkedHashMap<>();
        aliases.put("agentgate.run.id", new String[] {"agentgate.run_id"});
        aliases.put("agentgate.case.id", new String[] {"agentgate.case_id"});
        aliases.put("agentgate.turn.id", new String[] {"turn_id"});
        aliases.put("agentgate.operation.type", new String[] {"agentgate.operation_type"});
        return Collections.unmodifiableMap(aliases);
    }

    private static String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isEmpty() ? null : text;
    }

    private static String stringOrDefault(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Input should be a valid dictionary");
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObjectOrEmpty(Object value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Input should be a valid dictionary");
        }
        return (Map<String, Object>) value;
    }

    private static List<?> asList(Object value, String message) {
        if (!(value instanceof List)) {
            throw new IllegalArgumentException(message);
        }
        return (List<?>) value;
    }

    private static List<?> asListOrEmpty(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        if (!(value instanceof List)) {
            throw new IllegalArgumentException("Input should be a valid list");
        }
        return (List<?>) value;
    }
}
