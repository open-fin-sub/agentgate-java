package com.abchina.llmalf.agentgate.domain.model.metric;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 指标聚合方案.
 *
 * <p>对齐 Python domain/metric.py::MetricPlan:固定 id/version 标识。</p>
 */
public final class MetricPlan {

    /** 默认方案 id(协议契约常量) */
    public static final String DEFAULT_ID = "p1-equal-mean";
    /** 默认版本 */
    public static final String DEFAULT_VERSION = "1";

    private final String id;
    private final String version;

    private MetricPlan(String id, String version) {
        this.id = id;
        this.version = version;
    }

    /**
     * 构造指标方案(默认 id 与版本).
     *
     * @return 方案实例
     */
    public static MetricPlan of() {
        return of(DEFAULT_ID, DEFAULT_VERSION);
    }

    /**
     * 构造指标方案.
     *
     * @param id 方案 id
     * @param version 版本
     * @return 方案实例
     */
    public static MetricPlan of(String id, String version) {
        return new MetricPlan(
                DomainValidations.requireNonBlank(id, "MetricPlan id"),
                DomainValidations.requireNonBlank(version, "MetricPlan version"));
    }

    /**
     * 从 payload 解析指标方案.
     *
     * @param payload payload 对象
     * @return 方案实例
     */
    public static MetricPlan fromPayload(Map<String, Object> payload) {
        return of(PayloadValues.stringOrDefault(payload, "id", DEFAULT_ID),
                PayloadValues.stringOrDefault(payload, "version", DEFAULT_VERSION));
    }

    public String id() {
        return id;
    }

    public String version() {
        return version;
    }

    /**
     * 组装 payload 表示.
     *
     * @return JSON 兼容树
     */
    public Object toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("version", version);
        return payload;
    }
}
