package com.abchina.llmalf.agentgate.domain.model.expectation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 缺失条件.
 *
 * <p>对齐 Python domain/expectation.py::MustBeMissing:无字段的判别类,
 * 无状态单例。</p>
 */
public final class MustBeMissing implements Condition {

    public static final String KIND = "must_be_missing";

    private static final MustBeMissing INSTANCE = new MustBeMissing();

    private MustBeMissing() {
    }

    /**
     * 获取单例.
     *
     * @return 条件实例
     */
    public static MustBeMissing of() {
        return INSTANCE;
    }

    static MustBeMissing parse(Map<String, Object> payload) {
        return INSTANCE;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public Object toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", KIND);
        return Collections.unmodifiableMap(payload);
    }
}
