package com.abchina.llmalf.agentgate.domain.model.evaluator;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 子评测器版本引用.
 *
 * <p>对齐 Python domain/evaluator.py::EvaluatorRef:
 * weight 可空且严格大于 0。</p>
 */
public final class EvaluatorRef {

    private final String evaluatorId;
    private final String evaluatorVersion;
    private final Double weight;

    private EvaluatorRef(String evaluatorId, String evaluatorVersion, Double weight) {
        this.evaluatorId = evaluatorId;
        this.evaluatorVersion = evaluatorVersion;
        this.weight = weight;
    }

    /**
     * 构造子引用.
     *
     * @param evaluatorId 评测器 id
     * @param evaluatorVersion 版本
     * @param weight 权重(可空,大于 0)
     * @return 引用实例
     */
    public static EvaluatorRef of(String evaluatorId, String evaluatorVersion, Double weight) {
        if (weight != null && !(weight > 0)) {
            throw new IllegalArgumentException("Input should be greater than 0");
        }
        return new EvaluatorRef(
                DomainValidations.requireNonBlank(evaluatorId, "EvaluatorRef evaluator_id"),
                DomainValidations.requireNonBlank(evaluatorVersion, "EvaluatorRef evaluator_version"),
                weight);
    }

    /**
     * 从 payload 解析子引用.
     *
     * @param payload payload 对象
     * @return 引用实例
     */
    public static EvaluatorRef fromPayload(Map<String, Object> payload) {
        return of(PayloadValues.requiredString(payload, "evaluator_id"),
                PayloadValues.requiredString(payload, "evaluator_version"),
                PayloadValues.optionalDouble(payload, "weight"));
    }

    public String evaluatorId() {
        return evaluatorId;
    }

    public String evaluatorVersion() {
        return evaluatorVersion;
    }

    public Double weight() {
        return weight;
    }

    /**
     * 组装 payload 表示.
     *
     * @return JSON 兼容树
     */
    public Object toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evaluator_id", evaluatorId);
        payload.put("evaluator_version", evaluatorVersion);
        payload.put("weight", weight);
        return payload;
    }
}
