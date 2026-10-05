package com.abchina.llmalf.agentgate.domain.model.result;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 评测器执行失败脱敏详情.
 *
 * <p>对齐 Python domain/result.py::EvaluatorErrorDetail:
 * category 为 Literal 三值(wire 契约),reference 空串归一 null。</p>
 */
public final class EvaluatorErrorDetail {

    /** category wire 值:崩溃 */
    public static final String CATEGORY_CRASH = "crash";
    /** category wire 值:超时 */
    public static final String CATEGORY_TIMEOUT = "timeout";
    /** category wire 值:非法输出 */
    public static final String CATEGORY_INVALID_OUTPUT = "invalid_output";

    private final String category;
    private final String exceptionType;
    private final String message;
    private final boolean retryable;
    private final String reference;

    private EvaluatorErrorDetail(String category, String exceptionType, String message,
            boolean retryable, String reference) {
        this.category = category;
        this.exceptionType = exceptionType;
        this.message = message;
        this.retryable = retryable;
        this.reference = reference;
    }

    /**
     * 构造错误详情.
     *
     * @param category crash/timeout/invalid_output
     * @param exceptionType 异常类型名
     * @param message 消息
     * @param retryable 是否可重试
     * @param reference 引用(可空,空串归一 null)
     * @return 详情实例
     */
    public static EvaluatorErrorDetail of(String category, String exceptionType, String message,
            boolean retryable, String reference) {
        if (!CATEGORY_CRASH.equals(category) && !CATEGORY_TIMEOUT.equals(category)
                && !CATEGORY_INVALID_OUTPUT.equals(category)) {
            throw new IllegalArgumentException(
                    "Input should be 'crash', 'timeout' or 'invalid_output'");
        }
        String validExceptionType = DomainValidations.requireNonBlank(exceptionType,
                "EvaluatorErrorDetail exception_type");
        String validMessage = DomainValidations.requireNonBlank(message,
                "EvaluatorErrorDetail message");
        String normalizedReference = PayloadValues.normalizeOptionalText(reference,
                "EvaluatorErrorDetail reference");
        return new EvaluatorErrorDetail(category, validExceptionType, validMessage, retryable,
                normalizedReference);
    }

    /**
     * 从 payload 解析错误详情.
     *
     * @param payload payload 对象
     * @return 详情实例
     */
    public static EvaluatorErrorDetail fromPayload(Map<String, Object> payload) {
        return of(PayloadValues.requiredString(payload, "category"),
                PayloadValues.requiredString(payload, "exception_type"),
                PayloadValues.requiredString(payload, "message"),
                PayloadValues.boolOrDefault(payload, "retryable", false),
                PayloadValues.optionalString(payload, "reference"));
    }

    public String category() {
        return category;
    }

    public String exceptionType() {
        return exceptionType;
    }

    public String message() {
        return message;
    }

    public boolean retryable() {
        return retryable;
    }

    public String reference() {
        return reference;
    }

    /**
     * 组装 payload 表示.
     *
     * @return JSON 兼容树
     */
    public Object toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("category", category);
        payload.put("exception_type", exceptionType);
        payload.put("message", message);
        payload.put("retryable", retryable);
        payload.put("reference", reference);
        return payload;
    }
}
