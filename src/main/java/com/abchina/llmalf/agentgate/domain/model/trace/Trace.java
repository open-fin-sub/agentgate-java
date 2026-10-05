package com.abchina.llmalf.agentgate.domain.model.trace;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.FrozenJson;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用例执行轨迹(归一化证据).
 *
 * <p>对齐 Python domain/trace.py::Trace 的数据契约:span 归属一致、
 * span_id 与 sequence 唯一。执行链的轮次视图辅助(for_turn/
 * completion_sequence)归 Python 侧,Java 不迁移。</p>
 */
public final class Trace {

    private static final String TRACE_ID_PATTERN = "[0-9a-f]{32}";

    private final String traceId;
    private final String runId;
    private final String caseId;
    private final List<TraceSpan> spans;
    private final Map<String, Object> turnOutcomes;
    private final Map<String, Object> finalOutput;
    private final Map<String, Object> finalState;

    private Trace(String traceId, String runId, String caseId, List<TraceSpan> spans,
            Map<String, Object> turnOutcomes, Map<String, Object> finalOutput,
            Map<String, Object> finalState) {
        this.traceId = traceId;
        this.runId = runId;
        this.caseId = caseId;
        this.spans = spans;
        this.turnOutcomes = turnOutcomes;
        this.finalOutput = finalOutput;
        this.finalState = finalState;
    }

    /**
     * 构造轨迹.
     *
     * @param traceId Trace id(32 位小写十六进制)
     * @param runId Run id
     * @param caseId 用例 id
     * @param spans Span 列表(span_id 与 sequence 唯一,trace_id 一致)
     * @param turnOutcomes 轮次结论(冻结)
     * @param finalOutput 最终输出(冻结)
     * @param finalState 最终状态(冻结)
     * @return 轨迹实例
     */
    @SuppressWarnings("unchecked")
    public static Trace of(String traceId, String runId, String caseId, List<TraceSpan> spans,
            Map<String, ?> turnOutcomes, Map<String, ?> finalOutput, Map<String, ?> finalState) {
        if (traceId == null || !traceId.matches(TRACE_ID_PATTERN)) {
            throw new IllegalArgumentException(
                    "trace_id must be exactly 32 lowercase hexadecimal characters");
        }
        String validRunId = DomainValidations.requireNonBlank(runId, "Trace run_id");
        String validCaseId = DomainValidations.requireNonBlank(caseId, "Trace case_id");
        List<TraceSpan> frozenSpans = spans == null
                ? Collections.<TraceSpan>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(spans));
        for (TraceSpan span : frozenSpans) {
            if (!span.traceId().equals(traceId)) {
                throw new IllegalArgumentException(
                        "every TraceSpan must use the owning Trace trace_id");
            }
        }
        Set<String> spanIds = new HashSet<>();
        for (TraceSpan span : frozenSpans) {
            if (!spanIds.add(span.spanId())) {
                throw new IllegalArgumentException(
                        "TraceSpan span_ids must be unique within a Trace");
            }
        }
        Set<Integer> sequences = new HashSet<>();
        for (TraceSpan span : frozenSpans) {
            if (!sequences.add(span.sequence())) {
                throw new IllegalArgumentException(
                        "TraceSpan sequences must be unique within a Trace");
            }
        }
        Map<String, Object> frozenOutcomes = turnOutcomes == null
                ? Collections.<String, Object>emptyMap()
                : (Map<String, Object>) FrozenJson.freeze(turnOutcomes);
        Map<String, Object> frozenOutput = finalOutput == null
                ? Collections.<String, Object>emptyMap()
                : (Map<String, Object>) FrozenJson.freeze(finalOutput);
        Map<String, Object> frozenState = finalState == null
                ? Collections.<String, Object>emptyMap()
                : (Map<String, Object>) FrozenJson.freeze(finalState);
        return new Trace(traceId, validRunId, validCaseId, frozenSpans, frozenOutcomes,
                frozenOutput, frozenState);
    }

    /**
     * 从 payload 解析轨迹.
     *
     * @param payload payload 对象
     * @return 轨迹实例
     */
    public static Trace fromPayload(Map<String, Object> payload) {
        Object rawSpans = payload.get("spans");
        List<TraceSpan> spans = new ArrayList<>();
        if (rawSpans instanceof List) {
            for (Object item : (List<?>) rawSpans) {
                if (!(item instanceof Map)) {
                    throw new IllegalArgumentException("Input should be a valid dictionary");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> spanPayload = (Map<String, Object>) item;
                spans.add(TraceSpan.fromPayload(spanPayload));
            }
        } else if (rawSpans != null) {
            throw new IllegalArgumentException("Input should be a valid list");
        }
        return of(PayloadValues.requiredString(payload, "trace_id"),
                PayloadValues.requiredString(payload, "run_id"),
                PayloadValues.requiredString(payload, "case_id"),
                spans,
                parseMapField(payload, "turn_outcomes"),
                parseMapField(payload, "final_output"),
                parseMapField(payload, "final_state"));
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

    public String runId() {
        return runId;
    }

    public String caseId() {
        return caseId;
    }

    public List<TraceSpan> spans() {
        return spans;
    }

    public Map<String, Object> turnOutcomes() {
        return turnOutcomes;
    }

    public Map<String, Object> finalOutput() {
        return finalOutput;
    }

    public Map<String, Object> finalState() {
        return finalState;
    }

    /**
     * 组装 payload 表示(字段顺序对齐 pydantic 声明序).
     *
     * @return JSON 兼容树
     */
    public Object toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("trace_id", traceId);
        payload.put("run_id", runId);
        payload.put("case_id", caseId);
        List<Object> spanPayloads = new ArrayList<>(spans.size());
        for (TraceSpan span : spans) {
            spanPayloads.add(span.toPayload());
        }
        payload.put("spans", spanPayloads);
        payload.put("turn_outcomes", turnOutcomes);
        payload.put("final_output", finalOutput);
        payload.put("final_state", finalState);
        return payload;
    }
}
