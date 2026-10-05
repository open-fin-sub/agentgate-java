package com.abchina.llmalf.agentgate.domain.model.result;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 产生检查结果的具体实现引用.
 *
 * <p>对齐 Python domain/result.py::MethodRef。</p>
 */
public final class MethodRef {

    private final String implementationId;
    private final String implementationVersion;

    private MethodRef(String implementationId, String implementationVersion) {
        this.implementationId = implementationId;
        this.implementationVersion = implementationVersion;
    }

    /**
     * 构造实现引用.
     *
     * @param implementationId 实现 id
     * @param implementationVersion 实现版本
     * @return 引用实例
     */
    public static MethodRef of(String implementationId, String implementationVersion) {
        return new MethodRef(
                DomainValidations.requireNonBlank(implementationId, "MethodRef implementation_id"),
                DomainValidations.requireNonBlank(implementationVersion,
                        "MethodRef implementation_version"));
    }

    /**
     * 从 payload 解析实现引用.
     *
     * @param payload payload 对象
     * @return 引用实例
     */
    public static MethodRef fromPayload(Map<String, Object> payload) {
        return of(PayloadValues.requiredString(payload, "implementation_id"),
                PayloadValues.requiredString(payload, "implementation_version"));
    }

    public String implementationId() {
        return implementationId;
    }

    public String implementationVersion() {
        return implementationVersion;
    }

    /**
     * 组装 payload 表示.
     *
     * @return JSON 兼容树
     */
    public Object toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("implementation_id", implementationId);
        payload.put("implementation_version", implementationVersion);
        return payload;
    }
}
