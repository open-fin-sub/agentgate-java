package com.abchina.llmalf.agentgate.domain.model.trace;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.FrozenJson;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 归一化 OTel 形态的单个操作 Span.
 *
 * <p>对齐 Python domain/trace.py::TraceSpan:trace_id 32hex、
 * span_id/parent_span_id 16hex、sequence 必填非负、时间带类前缀归一。</p>
 */
public final class TraceSpan {

    private static final String TRACE_ID_PATTERN = "[0-9a-f]{32}";
    private static final String SPAN_ID_PATTERN = "[0-9a-f]{16}";

    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final String name;
    private final String operationType;
    private final int sequence;
    private final OffsetDateTime startedAt;
    private final OffsetDateTime endedAt;
    private final SpanStatus status;
    private final Map<String, Object> attributes;
    private final List<Map<String, Object>> events;

    private TraceSpan(String traceId, String spanId, String parentSpanId, String name,
            String operationType, int sequence, OffsetDateTime startedAt,
            OffsetDateTime endedAt, SpanStatus status, Map<String, Object> attributes,
            List<Map<String, Object>> events) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.name = name;
        this.operationType = operationType;
        this.sequence = sequence;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.status = status;
        this.attributes = attributes;
        this.events = events;
    }

    /**
     * 构造 Span.
     *
     * @param traceId Trace id(32 位小写十六进制)
     * @param spanId Span id(16 位小写十六进制)
     * @param parentSpanId 父 Span id(可空,16 位小写十六进制)
     * @param name 操作名
     * @param operationType 操作类型
     * @param sequence 序号(非负,Trace 内唯一)
     * @param startedAt 开始时间(可空,取当前 UTC)
     * @param endedAt 结束时间(可空,取当前 UTC)
     * @param status 状态(默认 UNSET)
     * @param attributes 属性(冻结)
     * @param events 事件列表(每项冻结 JSON 对象)
     * @return Span 实例
     */
    @SuppressWarnings("unchecked")
    public static TraceSpan of(String traceId, String spanId, String parentSpanId, String name,
            String operationType, int sequence, OffsetDateTime startedAt,
            OffsetDateTime endedAt, SpanStatus status, Map<String, ?> attributes,
            List<Map<String, ?>> events) {
        if (traceId == null || !traceId.matches(TRACE_ID_PATTERN)) {
            throw new IllegalArgumentException(
                    "trace_id must be exactly 32 lowercase hexadecimal characters");
        }
        if (spanId == null || !spanId.matches(SPAN_ID_PATTERN)) {
            throw new IllegalArgumentException(
                    "span ids must be exactly 16 lowercase hexadecimal characters");
        }
        if (parentSpanId != null && !parentSpanId.matches(SPAN_ID_PATTERN)) {
            throw new IllegalArgumentException(
                    "span ids must be exactly 16 lowercase hexadecimal characters");
        }
        String validName = DomainValidations.requireNonBlank(name, "TraceSpan name");
        String validOperationType = DomainValidations.requireNonBlank(operationType,
                "TraceSpan operation_type");
        if (sequence < 0) {
            throw new IllegalArgumentException("Input should be greater than or equal to 0");
        }
        OffsetDateTime started = DomainValidations.normalizeUtc(
                startedAt == null ? DomainValidations.utcNow() : startedAt,
                "TraceSpan started_at");
        OffsetDateTime ended = DomainValidations.normalizeUtc(
                endedAt == null ? DomainValidations.utcNow() : endedAt,
                "TraceSpan ended_at");
        SpanStatus effectiveStatus = status == null ? SpanStatus.UNSET : status;
        Map<String, Object> frozenAttributes = attributes == null
                ? Collections.<String, Object>emptyMap()
                : (Map<String, Object>) FrozenJson.freeze(attributes);
        List<Map<String, Object>> frozenEvents;
        if (events == null) {
            frozenEvents = Collections.emptyList();
        } else {
            List<Map<String, Object>> parsed = new ArrayList<>(events.size());
            for (Map<String, ?> event : events) {
                if (event == null) {
                    throw new IllegalArgumentException("Input should be a valid dictionary");
                }
                parsed.add((Map<String, Object>) FrozenJson.freeze(event));
            }
            frozenEvents = Collections.unmodifiableList(parsed);
        }
        if (ended.isBefore(started)) {
            throw new IllegalArgumentException("TraceSpan ended_at must not be before started_at");
        }
        return new TraceSpan(traceId, spanId, parentSpanId, validName, validOperationType,
                sequence, started, ended, effectiveStatus, frozenAttributes, frozenEvents);
    }

    /**
     * 从 payload 解析 Span.
     *
     * @param payload payload 对象
     * @return Span 实例
     */
    public static TraceSpan fromPayload(Map<String, Object> payload) {
        List<Map<String, ?>> events = new ArrayList<>();
        Object rawEvents = payload.get("events");
        if (rawEvents instanceof List) {
            for (Object item : (List<?>) rawEvents) {
                if (!(item instanceof Map)) {
                    throw new IllegalArgumentException("Input should be a valid dictionary");
                }
                @SuppressWarnings("unchecked")
                Map<String, ?> event = (Map<String, ?>) item;
                events.add(event);
            }
        } else if (rawEvents != null) {
            throw new IllegalArgumentException("Input should be a valid list");
        }
        return of(PayloadValues.requiredString(payload, "trace_id"),
                PayloadValues.requiredString(payload, "span_id"),
                PayloadValues.optionalString(payload, "parent_span_id"),
                PayloadValues.requiredString(payload, "name"),
                PayloadValues.requiredString(payload, "operation_type"),
                PayloadValues.requiredInteger(payload, "sequence"),
                PayloadValues.optionalOffsetDateTime(payload, "started_at"),
                PayloadValues.optionalOffsetDateTime(payload, "ended_at"),
                SpanStatus.fromWireValue(PayloadValues.stringOrDefault(payload, "status",
                        SpanStatus.UNSET.wireValue())),
                parseMapField(payload, "attributes"),
                events);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> parseMapField(Map<String, Object> payload, String field) {
        Object raw = payload == null ? null : payload.get(field);
        if (raw == null) {
            return Collections.emptyMap();
        }
        if (!(raw instanceof Map)) {
            throw new IllegalArgumentException("Input should be a valid dictionary");
        }
        return (Map<String, ?>) raw;
    }

    public String traceId() {
        return traceId;
    }

    public String spanId() {
        return spanId;
    }

    public String parentSpanId() {
        return parentSpanId;
    }

    public String name() {
        return name;
    }

    public String operationType() {
        return operationType;
    }

    public int sequence() {
        return sequence;
    }

    public OffsetDateTime startedAt() {
        return startedAt;
    }

    public OffsetDateTime endedAt() {
        return endedAt;
    }

    public SpanStatus status() {
        return status;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public List<Map<String, Object>> events() {
        return events;
    }

    /**
     * 组装 payload 表示(字段顺序对齐 pydantic 声明序).
     *
     * @return JSON 兼容树
     */
    public Object toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("trace_id", traceId);
        payload.put("span_id", spanId);
        payload.put("parent_span_id", parentSpanId);
        payload.put("name", name);
        payload.put("operation_type", operationType);
        payload.put("sequence", sequence);
        payload.put("started_at", DomainValidations.isoFormat(startedAt));
        payload.put("ended_at", DomainValidations.isoFormat(endedAt));
        payload.put("status", status.wireValue());
        payload.put("attributes", attributes);
        List<Object> eventPayloads = new ArrayList<>(events.size());
        for (Map<String, Object> event : events) {
            eventPayloads.add(event);
        }
        payload.put("events", eventPayloads);
        return payload;
    }
}
