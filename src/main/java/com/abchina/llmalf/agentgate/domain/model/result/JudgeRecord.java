package com.abchina.llmalf.agentgate.domain.model.result;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 审判请求审计记录.
 *
 * <p>对齐 Python domain/result.py::JudgeRecord:
 * resolved_model/request_id 空串归一 null;previous_attempts 递归嵌套。</p>
 */
public final class JudgeRecord {

    private final String providerId;
    private final String requestedModel;
    private final String resolvedModel;
    private final String requestSha256;
    private final String rawResponse;
    private final String requestId;
    private final Long inputTokens;
    private final Long outputTokens;
    private final Double latencyMs;
    private final List<JudgeRecord> previousAttempts;

    private JudgeRecord(String providerId, String requestedModel, String resolvedModel,
            String requestSha256, String rawResponse, String requestId, Long inputTokens,
            Long outputTokens, Double latencyMs, List<JudgeRecord> previousAttempts) {
        this.providerId = providerId;
        this.requestedModel = requestedModel;
        this.resolvedModel = resolvedModel;
        this.requestSha256 = requestSha256;
        this.rawResponse = rawResponse;
        this.requestId = requestId;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.latencyMs = latencyMs;
        this.previousAttempts = previousAttempts;
    }

    /**
     * 构造审判记录.
     *
     * @param providerId 提供方 id
     * @param requestedModel 请求模型
     * @param resolvedModel 实际模型(可空,空串归一 null)
     * @param requestSha256 请求摘要(64 位小写十六进制)
     * @param rawResponse 原始响应
     * @param requestId 请求 id(可空,空串归一 null)
     * @param inputTokens 输入 token 数(可空,非负)
     * @param outputTokens 输出 token 数(可空,非负)
     * @param latencyMs 延迟毫秒(可空,非负)
     * @param previousAttempts 前序尝试记录
     * @return 记录实例
     */
    public static JudgeRecord of(String providerId, String requestedModel, String resolvedModel,
            String requestSha256, String rawResponse, String requestId, Long inputTokens,
            Long outputTokens, Double latencyMs, List<JudgeRecord> previousAttempts) {
        String validProviderId = DomainValidations.requireNonBlank(providerId,
                "JudgeRecord provider_id");
        String validRequestedModel = DomainValidations.requireNonBlank(requestedModel,
                "JudgeRecord requested_model");
        String normalizedResolvedModel = PayloadValues.normalizeOptionalText(resolvedModel,
                "JudgeRecord resolved_model");
        DomainValidations.requireSha256(requestSha256, "request_sha256");
        String validRawResponse = rawResponse == null ? "" : rawResponse;
        String normalizedRequestId = PayloadValues.normalizeOptionalText(requestId,
                "JudgeRecord request_id");
        requireNonNegative(inputTokens, "input_tokens");
        requireNonNegative(outputTokens, "output_tokens");
        requireNonNegative(latencyMs, "latency_ms");
        List<JudgeRecord> frozenAttempts = previousAttempts == null
                ? Collections.<JudgeRecord>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(previousAttempts));
        return new JudgeRecord(validProviderId, validRequestedModel, normalizedResolvedModel,
                requestSha256, validRawResponse, normalizedRequestId, inputTokens, outputTokens,
                latencyMs, frozenAttempts);
    }

    /**
     * 从 payload 解析审判记录.
     *
     * @param payload payload 对象
     * @return 记录实例
     */
    public static JudgeRecord fromPayload(Map<String, Object> payload) {
        return of(PayloadValues.requiredString(payload, "provider_id"),
                PayloadValues.requiredString(payload, "requested_model"),
                PayloadValues.optionalString(payload, "resolved_model"),
                PayloadValues.requiredString(payload, "request_sha256"),
                PayloadValues.stringOrDefault(payload, "raw_response", ""),
                PayloadValues.optionalString(payload, "request_id"),
                optionalLong(payload, "input_tokens"),
                optionalLong(payload, "output_tokens"),
                PayloadValues.optionalDouble(payload, "latency_ms"),
                parsePreviousAttempts(payload));
    }

    private static List<JudgeRecord> parsePreviousAttempts(Map<String, Object> payload) {
        Object rawAttempts = payload == null ? null : payload.get("previous_attempts");
        if (rawAttempts == null) {
            return Collections.emptyList();
        }
        if (!(rawAttempts instanceof List)) {
            throw new IllegalArgumentException("Input should be a valid list");
        }
        List<JudgeRecord> parsed = new ArrayList<>(((List<?>) rawAttempts).size());
        for (Object item : (List<?>) rawAttempts) {
            if (!(item instanceof Map)) {
                throw new IllegalArgumentException("Input should be a valid dictionary");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> attemptPayload = (Map<String, Object>) item;
            parsed.add(JudgeRecord.fromPayload(attemptPayload));
        }
        return parsed;
    }

    private static Long optionalLong(Map<String, Object> payload, String field) {
        Object value = payload == null ? null : payload.get(field);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        throw new IllegalArgumentException("Input should be a valid integer");
    }

    private static void requireNonNegative(Number value, String field) {
        if (value != null && value.doubleValue() < 0) {
            throw new IllegalArgumentException("Input should be greater than or equal to 0");
        }
    }

    public String providerId() {
        return providerId;
    }

    public String requestedModel() {
        return requestedModel;
    }

    public String resolvedModel() {
        return resolvedModel;
    }

    public String requestSha256() {
        return requestSha256;
    }

    public String rawResponse() {
        return rawResponse;
    }

    public String requestId() {
        return requestId;
    }

    public Long inputTokens() {
        return inputTokens;
    }

    public Long outputTokens() {
        return outputTokens;
    }

    public Double latencyMs() {
        return latencyMs;
    }

    public List<JudgeRecord> previousAttempts() {
        return previousAttempts;
    }

    /**
     * 组装 payload 表示(字段顺序对齐 pydantic 声明序).
     *
     * @return JSON 兼容树
     */
    public Object toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider_id", providerId);
        payload.put("requested_model", requestedModel);
        payload.put("resolved_model", resolvedModel);
        payload.put("request_sha256", requestSha256);
        payload.put("raw_response", rawResponse);
        payload.put("request_id", requestId);
        payload.put("input_tokens", inputTokens);
        payload.put("output_tokens", outputTokens);
        payload.put("latency_ms", latencyMs);
        List<Object> attemptPayloads = new ArrayList<>(previousAttempts.size());
        for (JudgeRecord attempt : previousAttempts) {
            attemptPayloads.add(attempt.toPayload());
        }
        payload.put("previous_attempts", attemptPayloads);
        return payload;
    }
}
