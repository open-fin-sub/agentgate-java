package com.abchina.llmalf.agentgate.domain.model.gate;

import com.abchina.llmalf.agentgate.domain.DomainValidations;
import com.abchina.llmalf.agentgate.domain.PayloadValues;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 发布门禁阈值配置.
 *
 * <p>对齐 Python domain/gate.py::ReleaseGateSpec:
 * minimum_score ∈ [0,1],默认 0.95。</p>
 */
public final class ReleaseGateSpec {

    /** 默认门禁 id(协议契约常量) */
    public static final String DEFAULT_ID = "release-gate";
    /** 默认最低分 */
    public static final double DEFAULT_MINIMUM_SCORE = 0.95;

    private final String id;
    private final String version;
    private final double minimumScore;

    private ReleaseGateSpec(String id, String version, double minimumScore) {
        this.id = id;
        this.version = version;
        this.minimumScore = minimumScore;
    }

    /**
     * 构造门禁配置(默认 id/版本/阈值).
     *
     * @return 门禁实例
     */
    public static ReleaseGateSpec of() {
        return of(DEFAULT_ID, "1", DEFAULT_MINIMUM_SCORE);
    }

    /**
     * 构造门禁配置.
     *
     * @param id 门禁 id
     * @param version 版本
     * @param minimumScore 最低分 [0,1]
     * @return 门禁实例
     */
    public static ReleaseGateSpec of(String id, String version, double minimumScore) {
        if (minimumScore < 0) {
            throw new IllegalArgumentException("Input should be greater than or equal to 0");
        }
        if (minimumScore > 1) {
            throw new IllegalArgumentException("Input should be less than or equal to 1");
        }
        return new ReleaseGateSpec(
                DomainValidations.requireNonBlank(id, "ReleaseGateSpec id"),
                DomainValidations.requireNonBlank(version, "ReleaseGateSpec version"),
                minimumScore);
    }

    /**
     * 从 payload 解析门禁配置.
     *
     * @param payload payload 对象
     * @return 门禁实例
     */
    public static ReleaseGateSpec fromPayload(Map<String, Object> payload) {
        return of(PayloadValues.stringOrDefault(payload, "id", DEFAULT_ID),
                PayloadValues.stringOrDefault(payload, "version", "1"),
                PayloadValues.doubleOrDefault(payload, "minimum_score", DEFAULT_MINIMUM_SCORE));
    }

    public String id() {
        return id;
    }

    public String version() {
        return version;
    }

    public double minimumScore() {
        return minimumScore;
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
        payload.put("minimum_score", minimumScore);
        return payload;
    }
}
